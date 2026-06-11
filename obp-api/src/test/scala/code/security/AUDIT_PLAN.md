# OBP-API Deep Vulnerability Audit — Red-Bar Test Plan (Batch 2)

## Context

The concurrency-hazard suite (16 confirmed DB races, `code.concurrency`, tag
`ConcurrencyRace`) is merged on `feature/concurrency-hazard-tests` / PR #2838.
The user asked to go *beyond* concurrency and audit the whole codebase for
*higher-severity* vulnerabilities, then prepare tests.

A multi-agent audit (11 vulnerability classes × adversarial verification) ran and
**confirmed 21 of 43 candidates** (22 refuted/mitigated — verifier reasoning held
up: most were already guarded by BCrypt, HMAC signatures, view checks, or were
misreads). I then hand-verified the headline findings against the live code.

**Decisions taken with the user:**
- **Scope** = security headliners **+ new concurrency** (not the full medium-tier set).
- **Convention** = **red-bar tests only** — assert the *secure/correct* behaviour so a
  live vulnerability surfaces as a **FAILED test** (its `expected vs actual` clue is
  the evidence). **No business-code fixes** in this batch.

This mirrors the existing concurrency suite exactly: the build stays green because
`obp-api/pom.xml:602` sets `<testFailureIgnore>${maven.test.failure.ignore}</testFailureIgnore>`,
so red-bar evidence tests are visible in surefire reports without gating the build.

## Hand-verified findings → 7 new red-bar scenarios

### Security suite — new package `code.security`, new tag `SecurityVuln` (5 scenarios)

| ID | Sev | Bug (verified file:line) | Red-bar assertion |
|---|---|---|---|
| **SEC-1 SQL injection** | HIGH | `MappedMetrics.scala:196-202` `sqlFriendly` does `s"'$value'"` with **no escaping**; user query params (`OBPConsumerId` etc., `OBPParam.scala:27`) flow into the raw SQL at `:427-457` / `:604-613` and run via `DBUtil.runQuery` (`:463`). Confirmed end-to-end. | A `' OR '1'='1` payload in `consumer_id` must be treated as a **literal** (0 matching rows), not interpreted (all rows). |
| **SEC-2 OAuth `aud` circular validation** | HIGH | `JwtUtil.validateIdToken:269-271` derives BOTH `iss` and `clientID` **from the token itself** (`getIssuer(idToken)`, `getAudience(idToken)`), then validates the token against them — circular, so any `aud` passes once the signature verifies. | A token whose `aud` ≠ the configured `openid_connect_*.client_id` must be **rejected** (`Failure`), not accepted. |
| **SEC-3 Signing-basket deletable post-auth** | HIGH | `Http4sBGv13SigningBaskets.scala:115-126` DELETE calls `deleteSigningBasket()` with **no status guard**; doc itself says "not deletable after a first (partial) authorisation". | After a basket reaches an authorised state (e.g. `ACTC`/`received`), DELETE must return **4xx**, not 204. |
| **SEC-4 Webhook SSRF** | HIGH | `WebhookHttpClient.scala:129-144` builds the request straight from the stored URL; create handler (`Http4s310` `account-web-hooks`) only checks `canCreateWebhook` — **no host allowlist**. | Creating a webhook with `url=http://127.0.0.1...` / `169.254.169.254` (cloud-metadata) must be **rejected**. |
| **SEC-5 Weak auth-context challenge + missing authz** | HIGH | `MappedUserAuthContextUpdateProvider.scala:63` compares an **8-digit plain** challenge with `==` (no hash, no rate-limit); `Http4s500.scala:779-807` lets **any** authenticated user answer **any** `userAuthContextUpdateId`. | A non-owner answering another user's update — or a brute-forced guess — must be **rejected** (403 / not-found), not `ACCEPTED`. |

### Concurrency extension — existing package `code.concurrency`, existing tag `ConcurrencyRace`, new file `ConcurrentUpsertRaceTest.scala` (2 scenarios)

| ID | Shape | Bug (verified) | Red-bar assertion |
|---|---|---|---|
| **BB TransactionIdMapping duplicate** | check-then-insert | `MappedTransactionIdMappingProvider.scala:12-41` find-then-create; indexes are `UniqueIndex(TransactionId)` / `(TransactionId, ref)` — **no unique index on `TransactionPlainTextReference` alone**, so concurrent calls for the same ref both insert (distinct UUIDs) → duplicate mappings. | N concurrent `getOrCreateTransactionId(sameRef)` → exactly **1** row / stable `transactionId`. |
| **CC ProductCollectionItem delete-then-insert** | unique-constraint-unhandled | `MappedProductCollectionItem.scala:37-59` deletes all items then re-creates with `.saveMe` (no `tryo`); `UniqueIndex(mCollectionCode, mMemberProductCode)` (`:76`) → concurrent resets collide on insert → uncaught throw. | N concurrent `getOrCreateProductCollectionItem(code, members)` → no throw, correct item count. |

## Audited and deliberately excluded (explicit, not silent)

- **Card-attribute "race" (#14)** — **rejected**: `createOrUpdateCardAttribute(id=None)` is an *unconditional create* (not get-or-create); two rows from two calls is the intended semantic, not a hazard.
- **Account-holder race (#18)** = existing scenario **D**; **Counterparty-metadata (#15)** ≈ existing **F** — duplicates of the merged suite.
- **`makeHistoricalPayment` IDOR (#2, Http4s310:4851)** — **policy, not bypass**: the only gate is the powerful `canCreateHistoricalTransaction` role on a `/management/...` import endpoint that is *designed* to move money between arbitrary accounts. Documented, not tested as a vuln.
- **Medium tier deferred by scope** (CVV in card-create response, 500 exception-message leak, BerlinGroup consent per-request scope, consent fake-account grant / JWT re-validation, dynamic-endpoint SSRF via Pekko, signing-basket status-transition, tx-status-not-awaited). Real per audit; out of the agreed scope. Listed here so the gap is explicit.

## Files to create

```
obp-api/src/test/scala/code/security/SecurityVulnSetup.scala      # base trait + SecurityVuln tag + helpers
obp-api/src/test/scala/code/security/MetricsSqlInjectionTest.scala         # SEC-1
obp-api/src/test/scala/code/security/OAuthIdTokenAudienceTest.scala        # SEC-2
obp-api/src/test/scala/code/security/SigningBasketStateGuardTest.scala     # SEC-3
obp-api/src/test/scala/code/security/WebhookSsrfTest.scala                 # SEC-4
obp-api/src/test/scala/code/security/AuthContextChallengeTest.scala        # SEC-5
obp-api/src/test/scala/code/concurrency/ConcurrentUpsertRaceTest.scala     # BB + CC
obp-api/src/test/scala/code/security/SECURITY_VULNERABILITIES.md           # findings doc (mirror CONCURRENCY_HAZARDS.md)
```

### `SecurityVulnSetup` (mirror `ConcurrentRaceSetup`)
- `extends ServerSetupWithTestData with DefaultUsers`.
- `object SecurityVuln extends Tag("code.security.SecurityVuln")`.
- Reuse existing helpers: `setPropsValues`, OAuth `user1`/`resourceUser1..3`, `makePostRequest(Async)`, `createBank`, `createAccountRelevantResource`, and (from concurrency) `runConcurrentWithBarrier` for any provider-level fan-out.
- Direct provider/DB entry points (no HTTP needed): `MappedMetrics.getAllAggregateMetricsFuture(params, isNewVersion=true)` for SEC-1; `JwtUtil.validateIdToken(token, jwkUrl)` for SEC-2.

### Per-scenario test approach (key points)
- **SEC-1**: seed 2 `MappedMetric` rows for `consumer_id="real"`; call `getAllAggregateMetricsFuture(List(OBPFromDate, OBPToDate, OBPConsumerId("nope' OR '1'='1")), true)`; assert returned count == 0 (literal), not 2. Pure provider call — deterministic.
- **SEC-2** (moderate setup): generate an RSA keypair (nimbus), serve/point a JWK set at `remoteJWKSetUrl` (file:// JWKS or a tiny local source), set `openid_connect_1.client_id="obp-configured-client"`, build a signed token with `aud="attacker-app"`, call `validateIdToken`; assert `Failure`. Currently `Full(claims)` → red. If JWK serving proves fiddly, fall back to asserting the circular derivation at the seam.
- **SEC-3**: BG v1.3 — create a SEPA payment + signing basket, start an authorisation, then DELETE the basket; assert 4xx. Reuse existing `Http4sBGv13*` test setup patterns.
- **SEC-4**: grant `canCreateWebhook`; POST `account-web-hooks` with a localhost/metadata URL; assert creation rejected (4xx). Provider-level alt: assert a host-allowlist check exists before dispatch.
- **SEC-5**: user1 creates an auth-context-update (SMS) → id; user2 answers that id (and/or brute a 0-9999… guess); assert 403/forbidden, no `UserAuthContext` created for user1.
- **BB/CC**: `runConcurrentWithBarrier(n)` over the provider call; assert row counts / no-throw exactly like scenarios D/O.

## Build & CI behaviour
- Red-bar tests fail by design; `obp-api/pom.xml:602` `testFailureIgnore` keeps the build green (same as the merged concurrency suite — verified: 16 failing tests → `BUILD SUCCESS`).
- `code.security` lands in the **shard-4 catch-all** automatically (per CLAUDE.md shard map), same as `code.concurrency`.
- Tags `SecurityVuln` / `ConcurrencyRace` let anyone include (`-DtagsToInclude=...`) or exclude the evidence suites without touching the build.

## Verification
1. Compile: `mvn test-compile -pl obp-commons,obp-api`.
2. Run the new suites only:
   `mvn -pl obp-commons,obp-api scalatest:test -DwildcardSuites="code.security.MetricsSqlInjectionTest,code.security.OAuthIdTokenAudienceTest,code.security.SigningBasketStateGuardTest,code.security.WebhookSsrfTest,code.security.AuthContextChallengeTest,code.concurrency.ConcurrentUpsertRaceTest" -DfailIfNoTests=false -Dscalatestargs="-oWDS"`.
3. Confirm each new scenario is **RED** with a clear `expected vs actual` clue (= vulnerability confirmed), and the Maven run still reports `BUILD SUCCESS` (testFailureIgnore).
4. Full regression via `./run_tests_parallel.sh` → expect `ALL SHARDS PASSED`.
5. Write `SECURITY_VULNERABILITIES.md`; commit on the same branch with the audit doc; push to `Hongwei`.
