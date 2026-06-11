# OBP-API Extended Vulnerability Audit — Findings & Evidence-Test Plan

## Context

A prior session produced 7 red-bar evidence tests (SEC-1..5 security + BB/CC concurrency)
on branch `feature/security-vulnerability-tests`, all passing the full parallel regression
(2725 tests). The user asked to **continue analyzing the whole project for further hidden
risks**. A second-pass multi-agent audit (7 parallel auditors, each one vulnerability class,
each instructed to exclude the already-found 7 and verify source→sink) confirmed **25 new
findings** — none overlapping the first batch. Two are CRITICAL and enable actual fund theft
or unauthorized account reads through the Berlin Group (PSD2) flow.

The goal of this plan: convert the confirmed findings into **red-bar evidence tests** that
follow the established convention — each test asserts the *secure* outcome, so a live
vulnerability shows as a FAILED test; `testFailureIgnore=true` in `obp-api/pom.xml` keeps the
build green. New tests land in `code/security/` (shard-4 catch-all) and `code/concurrency/`.

---

## New Findings (all distinct from SEC-1..5 / BB / CC)

### CRITICAL — fund theft / unauthorized account access

| ID | Title | File:line | Why critical |
|---|---|---|---|
| BG-1 | Cross-user BG payment authorise+execute (SCA bypass IDOR) | `code/api/berlin/group/v1_3/Http4sBGv13PIS.scala:396,592`; `code/api/util/NewStyle.scala:1340` | Attacker who knows a `paymentId` creates a challenge bound to *their own* userId, answers it, and `createTransactionAfterChallengeV210` moves funds out of the victim's stored debtor account — no re-check of caller access on the authorise/execute path. |
| BG-2 | Consent "fake account" — read-view granted on ANY IBAN | `code/api/util/ConsentUtil.scala:804-830,373-399`; `Http4sBGv13AIS.scala:63,110` | `createBerlinGroupConsentJWT` maps each requested IBAN to a `SYSTEM_READ_*` view and `grantAccessToSystemView` unconditionally — no check the PSU owns the account. Attacker self-authorises and reads any account's balances/transactions. |
| CR-1 | Hardcoded default HMAC secret for GatewayLogin/DAuth JWTs, no startup guard | `code/api/util/ApiPropsWithAlias.scala:33-36`; `CertificateUtil.scala:28,177-184`; `GatewayLogin.scala:133` | If `jwt_token_secret` is unset, the HS256 key is a constant in public source. Attacker mints a token with any `login_user_name` → authenticated as anyone. Gated only on the operator leaving the secret at default (silently permitted). |

### HIGH

| ID | Title | File:line |
|---|---|---|
| BG-3 | BG payment cancellation IDOR — cancel/reverse any `paymentId` | `Http4sBGv13PIS.scala:130,144,163-176` |
| SQLi-2 | Metrics list-param SQL injection (distinct sink from SEC-1) — `exclude_app_names` / `exclude_url_patterns` / `exclude_implemented_by_partial_functions` (+`include_*`) spliced raw via `extendLikeQuery` | `code/metrics/MappedMetrics.scala:335-354,407-416,438-460,463,584-622` |
| IDOR-1 | `getTransactionRequest` reads any txReq by ID (ignores path account; dropped `CAN_SEE_TRANSACTION_REQUESTS`) | `code/api/v4_0_0/Http4s400.scala:6287-6295` |
| IDOR-2 | Card-attribute create/update have NO authorization (any user writes any bank's card metadata) | `code/api/v3_1_0/Http4s310.scala:3685-3735,3739-3788` |
| SSRF-2 | Dynamic-endpoint swagger `servers[].url` SSRF (the deferred item, confirmed) | `code/api/dynamic/endpoint/helper/DynamicEndpointHelper.scala:227,253-257`; `RestConnector_vMar2019.scala:7126-7128`; `AkkaHttpClient.scala:102-103` |
| SSRF-3 | MethodRouting `url` param SSRF (same sink, 2nd path) | `RestConnector_vMar2019.scala:7075-7108`; `Http4s310.scala:4046-4140` |
| CONC-DD | `AccountIdMapping` duplicate mappings — get-or-create, no unique index on natural key (BB twin, different table) | `code/model/dataAccess/internalMapping/MappedAccountIdMappingProvider.scala:12-41` |
| CONC-EE | `CustomerIdMapping` duplicate mappings (BB twin, different table) | `code/customer/internalMapping/MappedCustomerIdMappingProvider.scala:11-40` |
| CONC-FF | Transaction-request status state machine — read-then-write, no optimistic lock | `code/transactionrequests/MappedTransactionRequestProvider.scala:202-209`; `Http4sBGv13PIS.scala:144-189` |
| LEAK-1 | CVV returned in card-create response (deferred, confirmed) | `code/api/v5_0_0/Http4s500.scala:1846,1870` |
| LEAK-2 | Raw JWT / id-token logged unmasked at DEBUG (default level) | `code/api/dauth.scala:100,104,115,123,125`; `GatewayLogin.scala:112,116,127,135,137` |
| CR-2 | DAuth RSA-verifies-HMAC key confusion (signature guarantee absent) | `code/api/dauth.scala:118-131`; `JwtUtil.scala:303-312` |

### MEDIUM

| ID | Title | File:line |
|---|---|---|
| IDOR-3 | Consent-management "at one bank" doesn't verify consent belongs to BANK_ID (cross-tenant) | `code/api/v5_1_0/Http4s510.scala:4093-4135,4137-4191,4193-4260` |
| IDOR-4 | `getConsentRequest` — any consumer reads any consent request by ID | `code/api/v5_0_0/Http4s500.scala:904-939` |
| BG-4 | Consent status / sca-status / authorisations readable for ANY consent ID | `Http4sBGv13AIS.scala:300,335,354` |
| BG-5 | Signing-basket creation: no ownership check on `paymentIds`/`consentIds` | `Http4sBGv13SigningBaskets.scala:46` |
| BG-6 | Per-request consent scope not enforced (cumulative persisted view grants) | `code/api/util/ConsentUtil.scala:373-399`; `Http4sBGv13AIS.scala:166,205,390,422` |
| CONC-GG | `AccountAccess` grant-vs-grant — unique-index violation unhandled (500) | `code/views/MapperViews.scala:133-161` |
| CONC-HH | `MappedCounterparty` create — unique-constraint swallowed by `tryo` | `code/metadata/counterparties/MapperCounterparties.scala:190-237`; `Http4s400.scala:2820-2855` |
| CONC-II | `MappedProductCollection` delete-then-reinsert under one `tryo` (CC twin) | `code/productcollection/MappedProductCollection.scala:16-39` |
| CONC-JJ | `UserAuthContext`/`ConsentAuthContext` check-then-insert defeated by `createdAt` in the unique index | `code/context/MappedUserAuthContextProvider.scala:40-67`; `MappedConsentAuthContextProvider.scala` |
| CONC-KK | `JobScheduler` archive lock is a racy check-then-insert (unique index on wrong column) | `code/scheduler/MetricsArchiveScheduler.scala:96-133` |
| LEAK-3 | Raw exception message in 500 response body (deferred, confirmed) | `code/api/util/http4s/ErrorResponseConverter.scala:183` |
| CR-3 | JWT `typ`/`alg` read from unverified token to choose validation path (Keycloak) | `code/api/OAuth2.scala:674-687`; `JwtUtil.scala:200-211` |

### LOW (hardening — document, no test)
| ID | Title | File:line |
|---|---|---|
| LEAK-4 | OAuth consumer key partially exposed in INFO logs | `code/model/OAuth.scala:402,412` |
| LEAK-5 | Metrics persistence captures full response bodies + URLs (conditional on `write_metrics`) | `code/api/util/WriteMetricUtil.scala:42-81` |
| CR-4 | Timing-unsafe equality on cert/consumer-key comparisons | `CertificateUtil.scala:258`; `ConsentUtil.scala:200,210,220` |

---

## Proposed Work — new red-bar evidence tests (mirroring SEC-1..5 / BB / CC)

Reuse the existing harness: `SecurityVulnSetup` trait + `SecurityVuln` tag for security,
`ConcurrentRaceSetup` + `ConcurrencyRace` tag for concurrency. Each test seeds data via
providers directly, exercises the vulnerable path, and asserts the *secure* outcome (RED while
unpatched). Berlin Group tests use `bgBase = baseRequest / urlPrefix / apiShortVersion` as in
the existing `SigningBasketStateGuardTest`. Files grouped by domain:

- **`code/security/BerlinGroupPaymentAuthIdorTest.scala`** — BG-1 (cross-user authorise+execute → assert 403/404), BG-3 (cancel IDOR → 403/404), BG-4 (consent-status IDOR → 403).
- **`code/security/BerlinGroupConsentScopeTest.scala`** — BG-2 (fake-account consent → consent activation must NOT grant a view on a non-owned IBAN), BG-5 (basket creation rejects non-owned ids), BG-6 (per-request scope).
- **`code/security/MetricsListParamSqlInjectionTest.scala`** — SQLi-2 (OR-injection in `exclude_app_names` → 0 rows / no leak).
- **`code/security/DynamicEndpointSsrfTest.scala`** — SSRF-2 + SSRF-3 (swagger `servers.url` and MethodRouting `url` to `127.0.0.1`/`169.254.169.254` → rejected at create time).
- **`code/security/TransactionRequestIdorTest.scala`** — IDOR-1 (cross-account txReq read → 403/404).
- **`code/security/CardAttributeAuthzTest.scala`** — IDOR-2 (no-role user writing card attribute → 403).
- **`code/security/ConsentManagementCrossTenantTest.scala`** — IDOR-3 + IDOR-4.
- **`code/security/GatewayLoginDefaultSecretTest.scala`** — CR-1 (forged HS256 token with default secret must be rejected / default secret must be refused at startup), CR-2 (DAuth key confusion).
- **`code/security/SensitiveDataExposureTest.scala`** — LEAK-1 (no CVV in card-create response), LEAK-3 (no raw `e.getMessage` in 500 body), LEAK-2 (JWT not logged in clear — assert via a captured log appender).
- **`code/concurrency/ConcurrentIdMappingRaceTest.scala`** — CONC-DD, CONC-EE.
- **`code/concurrency/ConcurrentStateAndUpsertRaceTest.scala`** — CONC-FF, CONC-GG, CONC-HH, CONC-II, CONC-JJ, CONC-KK.
- **`code/security/EXTENDED_FINDINGS.md`** — full write-up (this document, expanded) + fix patterns, alongside the existing `SECURITY_VULNERABILITIES.md`.

LOW items (LEAK-4/5, CR-3/4) documented in `EXTENDED_FINDINGS.md` only, no evidence test.

## Decided scope & branch

- **Scope: ALL 22 findings** at CRITICAL + HIGH + MEDIUM get a red-bar evidence test
  (every file in *Proposed Work* above). The 3 CRITICAL (BG-1, BG-2, CR-1), all 12 HIGH,
  and all 12 MEDIUM. The 3 LOW items (LEAK-4/5, CR-4) and the genuinely hard-to-reproduce
  CR-3 (`typ`/`alg` policy steering) are documented in `EXTENDED_FINDINGS.md` only.
- **Branch: new independent branch** `feature/security-vulnerability-tests-batch2`, cut from
  the current `feature/security-vulnerability-tests` HEAD, kept separate from batch 1.

## Verification

1. New branch: `git checkout -b feature/security-vulnerability-tests-batch2` (from current HEAD).
2. Compile: `mvn test-compile -pl obp-commons,obp-api -q` (the reactor form — required so
   `transmittable-thread-local` resolves; the script precompile is already fixed to use it).
3. Run only the new security evidence: `mvn -pl obp-commons,obp-api scalatest:test
   -DtagsToInclude=code.security.SecurityVuln -DfailIfNoTests=false` — each new scenario should
   FAIL (red = vulnerability confirmed), build stays SUCCESS via `testFailureIgnore`.
4. Full regression: `./run_tests_parallel.sh` — all 4 shards must stay BUILD SUCCESS (the
   evidence tests are red-bar but ignored; no other suite regresses).
5. Commit per-domain with Conventional-Commits messages; push to `Hongwei`
   `feature/security-vulnerability-tests-batch2`.

## Execution order (per-domain, each = compile + commit)

1. **BerlinGroup security** (BG-1..6) — highest impact, largest reuse of `bgBase` helper.
2. **SQLi-2** metrics list-param injection.
3. **SSRF-2/3** dynamic-endpoint + method-routing.
4. **IDOR-1..4** txReq / card-attribute / consent-management / consent-request.
5. **Crypto** CR-1 (default secret) + CR-2 (DAuth key confusion).
6. **Data exposure** LEAK-1 (CVV) + LEAK-2 (JWT log) + LEAK-3 (500 body).
7. **Concurrency batch 3** CONC-DD..KK (two files: id-mapping; state+upsert).
8. **Docs** `EXTENDED_FINDINGS.md`, then full regression + push.
