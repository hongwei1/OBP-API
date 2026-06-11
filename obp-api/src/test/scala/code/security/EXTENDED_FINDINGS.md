# Extended Security & Concurrency Findings — OBP-API Batch 2

This document covers the 32 new findings discovered in the Pass-2 and Pass-3 audits
(distinct from the original 7 in `SECURITY_VULNERABILITIES.md`).  Each entry records the
finding ID, severity, affected file/line, root cause, evidence test, and a recommended fix.

---

## Summary

| Severity | Count | IDs |
|---|---|---|
| CRITICAL | 3 | BG-2, CR-1, PERF-PP |
| HIGH | 9 | BG-1, BG-3, SSRF-2, SSRF-3, IDOR-1, IDOR-2, INJECT-1, JWT-EXP, PRIVESC-1 |
| MEDIUM | 14 | BG-4..6, SQLi-2, IDOR-3..4, CONC-DD..KK, RACE-1, CR-2, CORS-1 |
| LOW / Doc only | 10 | LEAK-1..3, CR-3..4, XSS-2, PERF-LL..RR, CSRF-1, INSEC-1 |

Evidence tests are in `code/security/` (security tag) and `code/concurrency/` (concurrency tag).
All tests assert the **secure** outcome; they fail (red-bar) until the path is hardened.
`testFailureIgnore=true` keeps the CI build green while the vulnerabilities remain open.

---

## CRITICAL

### BG-2 — Consent "fake account": read-view granted on ANY IBAN without ownership check

**File**: `code/api/util/ConsentUtil.scala:804-830`, `Http4sBGv13AIS.scala:63,110`
**Root cause**: `createBerlinGroupConsentJWT` maps each requested IBAN to a `SYSTEM_READ_*` view
and calls `grantAccessToSystemView` unconditionally. There is no check that the PSU (authenticated
user) owns the requested account. An attacker can create a consent for any IBAN, self-authorise
it, and read balances/transactions of accounts they don't own.
**Fix**: Before granting a view, verify the PSU owns the account via
`checkBankAccountExists(bankId, accountId)` and that the PSU matches the account holder.
**Evidence test**: `BerlinGroupConsentScopeTest` — BG-2 scenario.

---

### CR-1 — Hardcoded default HMAC secret; no startup guard

**File**: `code/api/util/ApiPropsWithAlias.scala:33-36`, `GatewayLogin.scala:133`
**Root cause**: `jwtTokenSecret` defaults to the public literal
`"Cannot get your at least 256 bit secret"`. If the operator does not set it, any attacker who
has read the source can forge a valid GatewayLogin or DAuth JWT for any `login_user_name`,
bypassing all authentication.
**Fix**: (a) At boot, detect `jwtTokenSecret == default` and refuse to start, or (b) generate a
random secret at first boot and persist it. Log a CRITICAL warning and block all JWT auth paths
if the default is in use.
**Evidence test**: `GatewayLoginDefaultSecretTest` — CR-1 scenario.

---

### PERF-PP — Blocking `Await.result` in gRPC AuthInterceptor

**File**: `code/obp/grpc/chat/AuthInterceptor.scala:72`
**Root cause**: `Await.result(APIUtil.getUserAndSessionContextFuture(cc), 30.seconds)` blocks the
Netty gRPC dispatcher thread for up to 30 seconds per authenticated call. Under auth-service
degradation (slow DB, connection-pool exhaustion) all concurrent gRPC requests serialize behind
this wall, stalling the entire gRPC server.
**Fix**: Replace with an async callback: complete the `ServerCall.Listener` from a
`Future.onComplete` continuation on a non-dispatcher thread.
**Evidence test**: `GrpcAuthBlockingTest` — PERF-PP scenario.

---

## HIGH

### BG-1 — Cross-user BG payment authorise+execute (SCA bypass IDOR)

**File**: `Http4sBGv13PIS.scala:396,592`; `NewStyle.scala:1340`
**Root cause**: `startPaymentAuthorisationAll` creates a challenge bound to the CALLER's userId.
`createTransactionAfterChallengeV210` then executes the payment using the STORED debtor account
without re-verifying that the authenticated user owns it. Attacker knows a `paymentId`, creates
their own challenge against it, answers it, and moves funds from the victim's account.
**Fix**: On every authorisation step, assert `payment.userId == authenticatedUser.userId` (or
debtor IBAN ownership). Bind the challenge to the payment's owner, not the challenger.
**Evidence test**: `BerlinGroupPaymentAuthIdorTest` — BG-1 scenario.

---

### BG-3 — BG payment cancellation IDOR

**File**: `Http4sBGv13PIS.scala:130,144,163-176`
**Root cause**: `cancelPayment` fetches the payment by ID and immediately cancels it. No check
that the authenticated caller is the original payment initiator or owns the debtor account.
**Fix**: Verify `payment.userId == authenticatedUser.userId` before proceeding with cancellation.
**Evidence test**: `BerlinGroupPaymentAuthIdorTest` — BG-3 scenario.

---

### SSRF-2 — Dynamic-endpoint swagger `servers[].url` SSRF

**File**: `DynamicEndpointHelper.scala:227,253-257`; `RestConnector_vMar2019.scala:7126-7128`
**Root cause**: The `url` value from `servers[0]` in a user-supplied OpenAPI spec is used
directly as the proxy target. An operator-level user can register a spec whose server URL points
to `127.0.0.1`, `169.254.169.254` (AWS IMDS), or internal services.
**Fix**: Validate `servers[].url` against an allowlist or block RFC-1918 and link-local ranges
at create time; reject the spec if the URL resolves to a private address.
**Evidence test**: `DynamicEndpointSsrfTest` — SSRF-2 scenario.

---

### SSRF-3 — MethodRouting `url` SSRF

**File**: `RestConnector_vMar2019.scala:7075-7108`; `Http4s310.scala:4046-4140`
**Root cause**: `MethodRouting.url` is stored as-is and used as the HTTP proxy target for
connector calls. Same internal-network access path as SSRF-2.
**Fix**: Same allowlist/block approach as SSRF-2 applied at `createMethodRouting` time.
**Evidence test**: `DynamicEndpointSsrfTest` — SSRF-3 scenario.

---

### IDOR-1 — `getTransactionRequest` ignores path account; reads any txReq by ID

**File**: `Http4s400.scala:6287-6295`
**Root cause**: `getTransactionRequestImpl(TransactionRequestId(id))` returns the txReq without
checking it belongs to the path `BANK_ID`/`ACCOUNT_ID`. Any user with a view on any account can
read any other user's txReq by guessing its UUID.
**Fix**: After fetching, assert `txReq.from.bankId == pathBankId && txReq.from.accountId == pathAccountId`.
**Evidence test**: `TransactionRequestIdorTest` — IDOR-1 scenario.

---

### IDOR-2 — Card-attribute create/update have NO authorization

**File**: `Http4s310.scala:3685-3735,3739-3788`
**Root cause**: Both `createCardAttribute` and `updateCardAttribute` use
`executeFutureWithBodyCreated` with no `withUser` or role check. Any caller, including
unauthenticated ones, can write card metadata.
**Fix**: Require `canCreateCardsForBank` (or a dedicated card-attribute role) and verify the
caller's bank matches the path `BANK_ID`.
**Evidence test**: `CardAttributeAuthzTest` — IDOR-2 scenarios.

---

### INJECT-1 — JSON injection via unescaped `newHost` in DynamicEndpointHelper

**File**: `DynamicEndpointHelper.scala:86-112`
**Root cause**: `newHost` is string-interpolated directly into a JSON template:
`s"""{"servers":[{"url":"$newHost"}]}"""`. A payload like
`http://x.com","extra":"injected` breaks out of the `url` string.
**Fix**: Use `JObject(List(JField("url", JString(newHost))))` to construct the JSON object, or
call `compact(render(JString(newHost)))` and splice the rendered value.
**Evidence test**: `DynamicEndpointJsonInjectionTest` — INJECT-1 scenarios.

---

### JWT-EXP — DAuth + GatewayLogin JWT `exp` claim never validated

**File**: `code/api/dauth.scala:112-132`; `GatewayLogin.scala:124-144`
**Root cause**: Both `validateJwtToken` implementations verify the HMAC signature but never
extract or check `expirationTime`. A captured token (or one issued without exp) is accepted
indefinitely, defeating the intended session timeout.
**Fix**: After successful signature verification, call
`SignedJWT.getJWTClaimsSet().getExpirationTime()` and reject if null or in the past.
**Evidence test**: `EntitlementPrivescTest` — JWT-EXP scenario; `GatewayLoginDefaultSecretTest` — JWT-EXP scenario.

---

### PRIVESC-1 — Entitlement privilege escalation via body `bank_id`

**File**: `Http4s700.scala:307-327`
**Root cause**: `addEntitlement` reads `bank_id` from the JSON body and uses it for BOTH the
`hasAtLeastOneEntitlement` check AND the `addEntitlement` grant. A user with
`canCreateEntitlementAtOneBank` for bank A can set `body.bank_id=B` to grant entitlements at B.
**Fix**: Use the path's `BANK_ID` for the authorization check; accept `body.bank_id` only when
the role is `canCreateEntitlementAtAnyBank` (system-level).
**Evidence test**: `EntitlementPrivescTest` — PRIVESC-1 scenario.

---

## MEDIUM

### BG-4 — Consent status / SCA-status / authorisations readable for ANY consent ID

**File**: `Http4sBGv13AIS.scala:300,335,354`
**Root cause**: Status endpoints return consent data for any ID with no PSU ownership check.
**Fix**: Verify `consent.userId == authenticatedUser.userId` before returning.
**Evidence test**: `BerlinGroupPaymentAuthIdorTest` — BG-4 scenario.

---

### BG-5 — Signing-basket creation: no ownership check on paymentIds/consentIds

**File**: `Http4sBGv13SigningBaskets.scala:46`
**Root cause**: Basket creation accepts any `paymentIds`/`consentIds` without verifying the
caller owns the referenced resources.
**Fix**: For each referenced payment/consent, assert caller == owner.
**Evidence test**: `BerlinGroupConsentScopeTest` — BG-5 scenario.

---

### BG-6 — Per-request consent scope not enforced (cumulative persisted view grants)

**File**: `ConsentUtil.scala:373-399`; `Http4sBGv13AIS.scala:166,205,390,422`
**Root cause**: Consent view grants are persisted and accumulate across consent authorisations.
A consent for account A is reused on subsequent requests covering account B.
**Fix**: Evaluate the consent's `access` object on each request and reject access to resources
not in the current request's consent scope.
**Evidence test**: `BerlinGroupConsentScopeTest` — BG-6 scenario.

---

### SQLi-2 — Metrics list-param SQL injection via `extendLikeQuery`

**File**: `MappedMetrics.scala:335-354,407-416,438-460`
**Root cause**: `extendLikeQuery` builds raw SQL: `s"'${params.head}'"`. Filter params
`exclude_app_names`, `exclude_url_patterns`, and `exclude_implemented_by_partial_functions`
flow from HTTP query params directly into the SQL string.
**Fix**: Use Lift Mapper's `Like` / `NotLike` query DSL, or parameterized prepared statements.
**Evidence test**: `MetricsListParamSqlInjectionTest` — SQLi-2a scenario.

---

### IDOR-3 — Consent management "at one bank" doesn't verify consent belongs to BANK_ID

**File**: `Http4s510.scala:4093-4260`
**Root cause**: `getConsentsAtBank` checks the role but performs the bank filter in-memory
after fetching all consents; a user with `canGetConsentsAtOneBank` for bank A can request
bank B's consents and receive them if the in-memory filter is bypassed.
**Fix**: Apply the bank_id filter in the DB query, not in memory.
**Evidence test**: `ConsentManagementCrossTenantTest` — IDOR-3 scenario.

---

### IDOR-4 — `getConsentRequest`: any consumer reads any consent request by ID

**File**: `Http4s500.scala:904-939`
**Root cause**: No ownership check on consent request fetch; the ResourceDoc explicitly notes
"any registered consumer can read any consent request by ID."
**Fix**: Store and verify the `consumerId` that created the consent request.
**Evidence test**: `ConsentManagementCrossTenantTest` — IDOR-4 scenario.

---

### CR-2 — DAuth RSA-verifies-HMAC key confusion

**File**: `code/api/dauth.scala:118-131`; `JwtUtil.scala:303-312`
**Root cause**: When `jwt.use.ssl=false`, `validateJwtToken` first calls
`validateJwtWithRsaKey(token)`. If RSA verification returns false (HMAC token), the code
falls through and parses claims from the unverified token before calling `verifyHmacSignedJwt`.
**Fix**: Verify the token type first; if algorithm is HMAC, call only the HMAC verifier; if RSA,
call only the RSA verifier. Do not fall through between verifiers.
**Evidence test**: `GatewayLoginDefaultSecretTest` — CR-2 scenario.

---

### CONC-DD — AccountIdMapping find-then-create race

**File**: `MappedAccountIdMappingProvider.scala:12-41`
**Root cause**: `getOrCreateAccountId` does a `find`; if absent, `create.saveMe`. Two concurrent
callers both find absence and both insert, violating the unique index (or creating two mappings).
**Fix**: Use a DB-level upsert or wrap in a transaction with `SELECT FOR UPDATE`.
**Evidence test**: `ConcurrentIdMappingRaceTest` — CONC-DD scenario.

---

### CONC-EE — CustomerIdMapping find-then-create race (same pattern as CONC-DD)

**File**: `MappedCustomerIdMappingProvider.scala:11-40`
**Fix / Evidence test**: Same as CONC-DD; `ConcurrentIdMappingRaceTest` — CONC-EE scenario.

---

### CONC-FF — Transaction-request status state machine: read-then-write, no optimistic lock

**File**: `MappedTransactionRequestProvider.scala:202-209`
**Root cause**: Status update reads the current status, checks it, then writes the new status
without any optimistic lock or CAS. Concurrent status transitions can overlap.
**Fix**: Use `UPDATE ... WHERE status = expected_old_status` (rows-affected check) as an
optimistic lock.

---

### CONC-GG — AccountAccess grant-vs-grant: unique-index violation unhandled

**File**: `MapperViews.scala:133-161`
**Root cause**: `grantAccessToSystemView` does a find-then-create; the second concurrent insert
hits the unique index and the exception is not caught, causing a 500.
**Fix**: Wrap in `tryo` / catch `SQLIntegrityConstraintViolationException` and return the existing
row idempotently.
**Evidence test**: `ConcurrentStateAndUpsertRaceTest` — CONC-GG scenario.

---

### CONC-HH — MappedCounterparty create: unique-constraint swallowed by `tryo`

**File**: `MapperCounterparties.scala:190-237`
**Root cause**: The second concurrent insert is swallowed into `Empty` by `tryo`; the caller
receives no counterparty.
**Fix**: After constraint failure, fetch and return the existing row.
**Evidence test**: `ConcurrentStateAndUpsertRaceTest` — CONC-HH scenario.

---

### CONC-II — MappedProductCollection delete-then-reinsert race

**File**: `MappedProductCollection.scala:16-39`
**Root cause**: find-then-create under a single `tryo`; second insert violates unique index.
**Fix**: Wrap in `tryo` that re-fetches on constraint failure.
**Evidence test**: `ConcurrentStateAndUpsertRaceTest` — CONC-II scenario.

---

### CONC-JJ — UserAuthContext/ConsentAuthContext check-then-insert defeated by unique index

**File**: `MappedUserAuthContextProvider.scala:40-67`
**Root cause**: Check-then-insert for `(userId, key)`. Unique index on `(userId, key, createdAt)`
means createdAt timing differences can allow duplicates; alternatively concurrent inserts race.
**Fix**: Use `INSERT OR REPLACE` / `ON CONFLICT DO UPDATE` at DB level.
**Evidence test**: `ConcurrentStateAndUpsertRaceTest` — CONC-JJ scenario.

---

### RACE-1 — Challenge attempt counter race: brute-force limit bypassable via parallel requests

**File**: `MappedChallengeProvider.scala:76-91`
**Root cause**: Counter is incremented BEFORE checking the limit. Two concurrent requests both
read `counter < limit` before either write flushes, each getting an extra attempt.
**Fix**: Use `UPDATE ... SET attempt_counter = attempt_counter + 1 WHERE attempt_counter < limit`
and check `rows_affected == 1` to gate the attempt.
**Evidence test**: `ConcurrentChallengeAttemptTest` — RACE-1 scenario.

---

### CORS-1 — CORS handler emits both `Allow-Origin: *` and `Allow-Credentials: true`

**File**: `Http4sApp.scala:32-35`
**Root cause**: RFC 6454 §3.2.3 prohibits this combination; browsers that enforce the spec will
reject credential-sending cross-origin requests, but non-compliant or older clients may allow them.
**Fix**: Use explicit allowlist of origins for credentialed requests; never combine `*` with
`Allow-Credentials: true`.

---

## LOW / Documentation only

| ID | Title | File:line | Note |
|---|---|---|---|
| LEAK-1 | CVV returned in card-create response | `Http4s500.scala:1846,1870` | PCI DSS violation; remove from response shape. Evidence test: `SensitiveDataExposureTest`. |
| LEAK-2 | Raw JWT / id-token logged unmasked at DEBUG | `dauth.scala:100,104`; `GatewayLogin.scala:112,116` | Mask token to first/last 8 chars before logging. |
| LEAK-3 | Raw exception message in 500 response body | `ErrorResponseConverter.scala:183` | Return only `UnknownError` constant; log full message server-side. Evidence test: `SensitiveDataExposureTest`. |
| LEAK-4 | OAuth consumer key partially exposed in INFO logs | `OAuth.scala:402,412` | Truncate or mask. |
| LEAK-5 | Metrics captures full response bodies | `WriteMetricUtil.scala:42-81` | Conditional on `write_metrics`; acceptable if operator-controlled. |
| CR-3 | JWT `typ`/`alg` read from unverified token | `OAuth2.scala:674-687` | Algorithm confusion; validate before reading header claims. |
| CR-4 | Timing-unsafe equality on cert/consumer-key | `CertificateUtil.scala:258` | Use `MessageDigest.isEqual` or `java.security.MessageDigest.isEqual`. |
| XSS-2 | HTML XSS in `AppsPage` — app URL injected into href | `AppsPage.scala:109-111` | HTML-encode `url` before injecting into template. |
| PERF-LL | N+1 DB queries in `getBankAccountsForUser` | `LocalMappedConnector.scala:650-660` | Batch with one JOIN query. |
| PERF-MM | Per-item DB save in `ConsentScheduler` foreach | `ConsentScheduler.scala:69-82` | Batch update. |
| PERF-NN | Unbounded dynamic role cache | `ApiRole.scala:1432-1440` | Add a size cap or TTL (e.g. Caffeine). |
| PERF-RR | Unbounded dynamic tag cache | `ApiTag.scala:177-184` | Same. |
| CSRF-1 | OpenID Connect `state` hardcoded to empty string | `Http4sOpenIdConnect.scala:162-179` | Generate and validate a random nonce. |
| INSEC-1 | `allow_direct_login` defaults to `true` | `APIUtil.scala` | Document risk; require explicit opt-in in secure deployments. |

---

## Fix Patterns Reference

### Atomic upsert (Lift Mapper)
```scala
// Instead of: find(...).getOrElse(create...)
def atomicGetOrCreate[T <: LongKeyedMapper[T]](
    lookup: => Box[T], create: => T): T =
  lookup match {
    case Full(existing) => existing
    case _              =>
      scala.util.Try(create.saveMe()).toOption
        .orElse(lookup.toOption)
        .getOrElse(throw new RuntimeException("Upsert failed"))
  }
```

### Atomic counter increment with limit
```scala
// UPDATE challenge SET attempt_counter = attempt_counter + 1
// WHERE challenge_id = ? AND attempt_counter < ?
// Check: rows-affected == 1 means "granted", 0 means "rejected"
```

### Safe JSON construction (avoid string interpolation)
```scala
import net.liftweb.json._
val serversJson = JObject(List(JField("servers",
  JArray(List(JObject(List(JField("url", JString(newHost)))))))))
```

### JWT expiry check
```scala
val exp = signedJwt.getJWTClaimsSet().getExpirationTime()
if (exp == null || exp.before(new java.util.Date()))
  return Failure("Token has expired")
```
