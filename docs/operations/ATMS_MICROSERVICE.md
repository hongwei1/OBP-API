# `obp-atms`: the first domain microservice

`MethodRouting` already lets a deployment send any Connector method to a remote process —
that mechanism exists for connecting to core banking systems. `obp-atms` uses the exact same
mechanism to become the data owner for one domain instead: atm and branch records. Nothing in
`obp-api` was changed to make this possible; the module only had to exist and speak the wire
protocol a Connector product already speaks.

## What it is

A new Maven module, `obp-atms/`, depending on `obp-commons` only (same constraint as
`obp-kernel` — see that module's own comment for why the compiler enforcing it beats a reviewer
noticing). It owns two Postgres tables (`atms`, `branches`, column-for-column the same shape as
`code.atms.MappedAtm` / `code.branches.MappedBranches` in `obp-api`) and speaks the
`RabbitMQConnector_vOct2024` wire protocol from the consumer side: one shared queue
(`obp_rpc_queue` by default), the AMQP `messageId` carries the process name
(`obp_get_atm`, `obp_create_or_update_atm`, ...), the reply goes to the AMQP `replyTo` queue with
the AMQP `correlationId` echoed back. See `obp-api/.../rabbitmq/RabbitMQUtils.scala` for the
publisher side this mirrors.

It reuses `com.openbankproject.commons.util.JsonSerializers.nullTolerateFormats` — the exact
`Formats` the core uses to write outbound and read inbound adapter messages, including
`AbstractTypeDeserializer`, which is what lets `atm: AtmT` / `branch: BranchT` (trait-typed
`OutBound*` fields) decode into their concrete Commons class. There is no independent codec on
the adapter side to drift out of sync with the core's.

## Which 13 methods, and why not more

19 `Connector` methods touch atm/branch data. Only 15 have `InBound`/`OutBound` DTOs at all
(`createOrUpdateAtmAttribute`, `getAtmAttributeById`, `getAtmAttributesByAtm`, `getAllAtms` do
not — see plan stage 9.3 step 2). Of those 15, this deployment routes 13:

```
getAtm, getAtms, getBranch, getBranches,
createOrUpdateAtm, createOrUpdateBranch, deleteAtm,
updateAtmAccessibilityFeatures, updateAtmLocationCategories, updateAtmNotes,
updateAtmServices, updateAtmSupportedCurrencies, updateAtmSupportedLanguages
```

`deleteAtmAttribute` and `deleteAtmAttributesByAtmId` have DTOs and `obp-atms` implements
handlers for them, but they are **not** in the routing table below on purpose: atm attributes
live in the core's `MappedAtmAttribute` table, which nothing about creating or reading them can
route away (no DTOs for those three methods). Routing only the *delete* of an attribute to a
process that owns no attribute rows would split one logical table across two data stores for no
reason. If a future deployment adds DTOs for the three attribute methods, revisit this.

`updateAtmServices`'s wire field is named `supportedCurrencies` in
`obp-commons/dto/JsonsTransfer.scala` even though the value is the services list — a pre-existing
mismatch in the DTO (the `Connector` trait's own abstract signature already calls the parameter
that) that predates this module. `AtmRepo.updateServices` documents and mirrors it rather than
"fixing" it, matching `LocalMappedConnector.updateAtmServices`'s actual behaviour.

## Enabling it

Three moving parts, none of which touch `obp-api` source:

1. **`connector=star`**, not `mapped`. `Connector.buildOne` resolves `connector=mapped` straight
   to `LocalMappedConnector`, bypassing `MethodRouting` entirely — `star` is what makes
   `StarConnector` consult the routing table per call and fall back to `mapped` when a method has
   no row. Every method without a routing row behaves exactly as before.
2. **`rabbitmq_connector.*` props** (`OBP_RABBITMQ_CONNECTOR_HOST/PORT/USERNAME/PASSWORD/VIRTUAL_HOST`)
   pointing at a RabbitMQ reachable from `obp-web`/`obp-dynamic`/`obp-scheduler`. Mandatory —
   `RabbitMQUtils` throws at first use if any is missing.
3. **13 rows in the `methodrouting` table**, `connectorname='rabbitmq_vOct2024'`,
   `bankidpattern='.*'`, `isbankidexactmatch=false` — matches any bank. Inserted directly (see
   below); the `/management/method_routings` REST endpoint is the normal path in a real
   deployment, direct SQL is equivalent and was faster to script for this verification run.

```sql
INSERT INTO methodrouting (methodname, bankidpattern, connectorname, isbankidexactmatch, methodroutingid, parameters)
VALUES ('getAtm', '.*', 'rabbitmq_vOct2024', false, gen_random_uuid()::text, '[]');
-- ... one row per method name above
```

`NewStyle.function.getMethodRoutings` caches the DB read (`methodRoutingTTL` seconds); on a
freshly booted instance the cache is empty and the first call populates it, so there is no stale
negative-lookup to invalidate as long as the rows exist before the first request for that method.

## Deployment shape

```
obp-atms      Deploy ×1   full deps    consumes obp_rpc_queue, owns its own Postgres database
rabbitmq      Deploy ×1   —            the queue both sides speak
```

`obp-atms` needs `ATMS_DB_JDBC_URL`/`_USERNAME`/`_PASSWORD` (its own database — `obp_atms` in
this run, created once with `CREATE DATABASE obp_atms OWNER obp`, not a schema shared with the
core's `obp_roles`/`obp` database) and the same `RABBITMQ_*` values as the core side. It runs
`Database.migrate()` (idempotent `CREATE TABLE IF NOT EXISTS`) on every boot — no separate
migration step. Health: `/health/live` (process up) and `/health/ready` (DB and RabbitMQ
connections both usable) on port 8081.

Dockerfile is `eclipse-temurin:25-jre-alpine` + `target/lib/*.jar` + the module's own jar on the
classpath, entrypoint `com.tesobe.obpatms.Main` — no shading, matching how `obp-api` itself is
packaged (`docs/operations/SPLIT_DEPLOYMENT.md`).

## Verified against a real cluster (2026-09-12, k3d)

1. `POST /obp/v4.0.0/banks/BANK_ID/atms` → **201**, body round-trips through obp-atms.
2. The row lands in `obp_atms.atms`; `obp_roles.mappedatm` (the core's table) stays at **0
   rows** — proof the data is genuinely owned by the new process, not cached there.
3. `GET /obp/v5.1.0/banks/BANK_ID/atms/ATM_ID` and the list form both return **200** with the
   same data.
4. `obp-atms` scaled to 0 replicas: the atm endpoint returns **504** after the RabbitMQ response
   timeout (55s in this run); `/obp/v5.1.0/root` and `/obp/v5.1.0/banks` stay **200** throughout —
   only the atm/branch surface is affected.
5. Scaled back to 1: the atm endpoint returns **200** again immediately, no core-side restart
   needed.

One pre-existing, unrelated gap this run surfaced along the way: a fresh `obp_roles` database
built from an image compiled today did not have `v_account_access_with_views` until the migrator
was re-run with `migration_scripts.enabled=true` and `migration_scripts.execute_all=true` (both
default `false` — see `code.api.util.migration.Migration`). Any deployment that rebuilds its
image without re-running the migrator under those props will hit the same gap on an
already-existing database; it is not specific to atms.

## What this does not give you

Same caveats as every other domain-split candidate in the plan: `obp-atms` only answers atm and
branch questions — `getBank`/account/view validation on the same request still runs in the core.
Consistency is by routing, not by transaction: a write to `atms` and a write to the core's own
tables in the same logical operation are not atomic across the two databases. Atm/branch data is
reference data, which tolerates that; it is exactly why this domain was picked first and why
`accounts`/`transactions` are explicitly not candidates for the same treatment (see plan 9.4).
