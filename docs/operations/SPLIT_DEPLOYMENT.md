# Splitting one OBP-API build across several deployments

One jar, four deployments. `instance.role` decides what a JVM does at boot, and which jars are
on its classpath decides whether it can compile user-supplied Scala at runtime. Nothing here
needs a code change or a second artifact — the manifests below were run against a real k3s
cluster and the observations quoted are from that run.

| Deployment | `instance.role` | Image | Does |
|---|---|---|---|
| `obp-migrator` | `migrator` | slim | schema + migrations + seed data, then `System.exit(0)` without binding a port. A `Job`, not a `Deployment`. |
| `obp-scheduler` | `scheduler` | slim | owns the background schedulers and their `jobscheduler` lock rows. **Exactly one replica.** |
| `obp-web` | `web` | slim | serves traffic. Scale freely. |
| `obp-dynamic` | `web` | **full** | the only instance that can compile Scala at runtime. One replica, or zero when nobody is submitting dynamic code. |

`obp-dynamic` uses `instance.role=web` deliberately: it wants exactly the same gating (no
migrations, no schedulers, bind a port). The only thing that makes it different is its image.

## The two images

The normal build is the full one. The slim one is the same jar with six dependencies removed:

```dockerfile
RUN rm -f /app/lib/scala-compiler-*.jar \
          /app/lib/polyglot-*.jar \
          /app/lib/js-language-*.jar \
          /app/lib/truffle-*.jar \
          /app/lib/regex-*.jar \
          /app/lib/icu4j-*.jar
```

Use two build stages and copy the pruned directory forward. Deleting in a `RUN` layer after a
`COPY` leaves the jars in the lower layer and saves nothing.

Measured: `/app/lib` 295M → 224M, image 932MB → 820MB.

A slim instance says so at boot, and is explicit about what it gave up:

```
INFO DynamicCodeCompilerImpl$ -- Dynamic code compiler NOT installed: the toolbox is absent
from this deployment (NoClassDefFoundError: scala/tools/reflect/FrontEnd). Dynamic Resource
Docs, Dynamic Message Docs, Connector Methods, ABAC rules and the 'internal' connector are
unavailable; everything else is unaffected.
```

A full instance says `Dynamic code compiler installed (allow_user_generated_scala_code=…)`.

The difference is visible over HTTP without authenticating: `GET /obp/v6.0.0/system/connectors`
lists `internal` on a full instance and omits it on a slim one (889 vs 971 bytes in the run
below). That makes a usable routing health check.

## Why bother

On JDK 24+ the SecurityManager is gone, so `DynamicUtil`'s sandbox cannot be installed and
`doPrivileged` degrades to a pass-through. Holding the entitlement to submit dynamic code is
therefore close to holding remote code execution on the host. Confining that to one pod means it
can carry its own NetworkPolicy, its own audit, and — most usefully — can be scaled to zero when
nobody needs it.

## Routing

These are the paths that need the compiler. Send them to `obp-dynamic`; send everything else to
`obp-web`.

```
POST   /obp/*/management/connector-methods
PUT    /obp/*/management/connector-methods/CONNECTOR_METHOD_ID
GET    /obp/*/management/banks/BANK_ID/dynamic-resource-docs
POST   /obp/*/management/dynamic-resource-docs/validate
POST   /obp/*/management/dynamic-resource-docs/compile
GET    /obp/*/management/dynamic-code-approval-config
POST   /obp/*/management/abac-rules
PUT    /obp/*/management/abac-rules/ABAC_RULE_ID
DELETE /obp/*/management/abac-rules/ABAC_RULE_ID
POST   /obp/*/management/abac-rules/validate
POST   /obp/*/management/abac-rules/ABAC_RULE_ID/execute
POST   /obp/*/management/abac-policies/POLICY/execute
```

An ingress-nginx rule set that does it (longest prefix wins, so the catch-all `/` is safe):

```yaml
annotations:
  nginx.ingress.kubernetes.io/use-regex: "true"
...
  - path: /obp/[^/]+/management/connector-methods
    pathType: ImplementationSpecific
    backend: { service: { name: obp-dynamic, port: { number: 8080 } } }
  - path: /obp/[^/]+/management/dynamic-resource-docs
    pathType: ImplementationSpecific
    backend: { service: { name: obp-dynamic, port: { number: 8080 } } }
  - path: /obp/[^/]+/management/banks/[^/]+/dynamic-resource-docs
    pathType: ImplementationSpecific
    backend: { service: { name: obp-dynamic, port: { number: 8080 } } }
  - path: /obp/[^/]+/management/dynamic-code-approval-config
    pathType: ImplementationSpecific
    backend: { service: { name: obp-dynamic, port: { number: 8080 } } }
  - path: /obp/[^/]+/management/abac-rules
    pathType: ImplementationSpecific
    backend: { service: { name: obp-dynamic, port: { number: 8080 } } }
  - path: /obp/[^/]+/management/abac-policies
    pathType: ImplementationSpecific
    backend: { service: { name: obp-dynamic, port: { number: 8080 } } }
  - path: /
    pathType: Prefix
    backend: { service: { name: obp-web, port: { number: 8080 } } }
```

## The one thing routing cannot split: ABAC on the data path

`APIUtil.checkAbacAccountAccess` calls `AbacAccountAccess.grantsAccountAccess`
([`APIUtil.scala:3563`](../../obp-api/src/main/scala/code/api/util/APIUtil.scala#L3563)). That is
not an endpoint — it sits on the account-access authorisation chain of every request. On a slim
instance it returns `Full(false)`, which is fail-closed and exactly what
`allow_abac_account_access=false` already produces: ABAC grants nothing, and nothing that was
denied becomes allowed.

**So a deployment that relies on ABAC rules to grant account access must not run slim web pods.**
Give `obp-web` the full image in that case and keep `obp-dynamic` only as a blast-radius boundary
for the management endpoints. Moving this call across a process boundary is not a good trade: it
is a synchronous `Await` with a 10 second budget sitting on the authorisation hot path.

Two read-only diagnostics are in the same position and will report nothing on a slim instance:
`GET /banks/BANK_ID/accounts/ACCOUNT_ID/views/VIEW_ID/users-with-access` (v6.0.0) and
`GET …/account-access-trace` (v7.0.0).

## Ordering

`web`, `scheduler` and `dynamic` instances all read the schema and never create it. They must not
start before the migrator Job has finished. Use an initContainer that waits on the Job, or an
Argo/Helm hook — do not rely on the readiness probe to sort it out.

If they do start early they fail with `relation "viewdefinition" does not exist`, and the failure
mode is worth knowing: `main` throws, but the Hikari housekeeper is a non-daemon thread, so the
**JVM does not exit and the pod stays `Running` forever**. The readiness probe correctly keeps it
out of the Service, but without a liveness probe it never restarts and never reaches
CrashLoopBackOff. Give every OBP deployment a liveness probe, and do not read pod `STATUS` as a
health signal.

## `api_instance_id`

Set it, without a `final` suffix, on every deployment. See the prop's own notes in
`sample.props.template`: unset gives each JVM a unique id for locks and metrics but leaves the
Redis cache namespace at the literal `obp`, which every other unset deployment also uses.

## Verified

Run on k3s (k3d) with a dedicated Postgres, 12 September 2026.

Routing, from ingress-nginx's own upstream log:

```
GET /obp/v6.0.0/system/connectors                       → [obp-roles-obp-web-8080]     10.42.0.90:8080
GET /obp/v7.0.0/management/dynamic-code-approval-config → [obp-roles-obp-dynamic-8080] 10.42.0.92:8080
```

Blast radius, with `obp-dynamic` scaled to zero:

| Path | `obp-dynamic` ×1 | `obp-dynamic` ×0 |
|---|---|---|
| `/obp/v5.1.0/root` | 200 | 200 |
| `/obp/v6.0.0/system/connectors` | 200 | 200 |
| `/obp/v7.0.0/management/dynamic-code-approval-config` | 401 | 503 |
| `/obp/v6.0.0/management/abac-rules` | 401 | 503 |
| `/obp/v4.0.0/management/connector-methods` | 401 | 503 |
| `/obp/v6.0.0/management/dynamic-resource-docs/validate` | 401 | 503 |

(401 rather than 200 because those endpoints need an authenticated caller; the point of the check
is that the request reached an instance that has a compiler rather than being refused for not
having one.)

Role separation, from the boot logs:

```
obp-migrator   instance.role=migrator: boot complete, exiting without binding an HTTP port.
obp-web        instance.role=web: skipping schema, migrations and seed data — a 'migrator' instance owns those.
obp-web        instance.role=web: not starting the background schedulers — a 'scheduler' instance owns those.
```

Scheduler mutual exclusion, two replicas ticking every 20s, three consecutive ticks:

```
pod A  Starting Job ID: 9b5630aa…            (43ms, then deletes its lock row)
pod B  skipped due to ongoing job. Job ID: 9b5630aa…, api_instance_id: roles-experiment_2ead7837-…
```

Note what that proves and what it does not. It shows the read-then-skip path holding under two
real replicas; it does not exercise the `tryAcquire` rejection that `UniqueIndex(Name)` provides,
because that needs the loser to read before the winner's INSERT — a sub-millisecond window that
only the orchestrated unit tests reach.
