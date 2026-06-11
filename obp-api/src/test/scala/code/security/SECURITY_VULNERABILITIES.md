# OBP-API Security Vulnerability Evidence Suite

**Test run result**: 5 FAILED (vulnerabilities confirmed) · BUILD SUCCESS

---

## Overview

This suite provides red-bar evidence tests for five high-severity security vulnerabilities
found in OBP-API. Each scenario asserts the **theoretically secure/correct outcome**, so a
live vulnerability surfaces as a **FAILED test** — the red bar with its `expected vs actual`
clue is the evidence. When a path is fixed, the corresponding scenario flips from red to
green automatically.

The `testFailureIgnore` setting in `obp-api/pom.xml` keeps the build green while evidence
tests are failing (identical behaviour to the concurrency hazard suite).

---

## How to Run

```sh
# Run only security evidence tests
mvn -pl obp-commons,obp-api scalatest:test \
  -DtagsToInclude=code.security.SecurityVuln \
  -DfailIfNoTests=false

# Exclude from CI main flow
mvn -pl obp-commons,obp-api scalatest:test \
  -DtagsToExclude=code.security.SecurityVuln
```

---

## Test Files

| File | Scenario | Lines |
|---|---|---|
| `SecurityVulnSetup.scala` | base trait + `SecurityVuln` tag | 55 |
| `MetricsSqlInjectionTest.scala` | SEC-1 SQL injection | ~120 |
| `OAuthIdTokenAudienceTest.scala` | SEC-2 circular aud validation | ~110 |
| `SigningBasketStateGuardTest.scala` | SEC-3 missing status guard on DELETE | ~80 |
| `WebhookSsrfTest.scala` | SEC-4 webhook SSRF | ~100 |
| `AuthContextChallengeTest.scala` | SEC-5 IDOR on challenge answer | ~110 |

---

## Vulnerability Findings

### SEC-1 — SQL Injection in Metrics Aggregate Query

| | |
|---|---|
| **Severity** | HIGH |
| **File** | `obp-api/src/main/scala/code/metrics/MappedMetrics.scala:196-202, 427-457` |
| **Shape** | Input validation missing — string interpolation into raw SQL |

**Description**: `MappedMetrics.sqlFriendly` wraps user-supplied values in single quotes
(`s"'$value'"`) with no escaping. OBP query parameters (`OBPConsumerId`, `OBPUserId`,
`OBPUrl`, `OBPAppName`, etc.) flow directly into a raw SQL query executed by
`DBUtil.runQuery`. A payload such as `nope' OR '1'='1` widens the WHERE clause to
`consumerid = 'nope' OR '1'='1'`, effectively matching all rows and bypassing the filter.

**Red-bar assertion**: With two rows seeded for `consumer_id="real-consumer"`,
`getAllAggregateMetricsFuture(List(OBPFromDate, OBPToDate, OBPConsumerId("nope' OR '1'='1"))))`
must return `totalCount == 0`. Currently returns `totalCount == 2` (all rows leak).

**Fix pattern**: Use parameterised queries / prepared statements, or a proper escape
function that replaces `'` with `''` before interpolation.

---

### SEC-2 — Circular `aud` Validation in OAuth ID Token

| | |
|---|---|
| **Severity** | HIGH |
| **File** | `obp-api/src/main/scala/code/api/util/JwtUtil.scala:269-271` |
| **Shape** | Circular validation — token validates itself |

**Description**: `JwtUtil.validateIdToken` derives both the expected issuer and the
expected `clientID` from the token being validated:
```scala
val aud      = getAudience(idToken).headOption.getOrElse("")
val clientID = new ClientID(aud)
val validator = new IDTokenValidator(iss, clientID, keySelector, null)
```
Because the validator is configured with the token's own `aud`, any token whose signature
verifies passes the audience check unconditionally. An attacker presenting a token with
`aud="attacker-app"` is accepted even when `openid_connect_1.client_id="obp-configured-client"`.

**Red-bar assertion**: A valid signed token with `aud="attacker-app"` passed to
`validateIdToken` when the configured `client_id="obp-configured-client"` must return
`Failure`. Currently returns `Full(claims)`.

**Fix pattern**: Read the expected client_id from props:
```scala
val expectedClientId = APIUtil.getPropsValue("openid_connect_1.client_id", "")
val clientID = new ClientID(expectedClientId)
```
Then validate the token's `aud` against that configured value.

---

### SEC-3 — Signing Basket Deletable After Partial Authorisation

| | |
|---|---|
| **Severity** | HIGH |
| **File** | `obp-api/src/main/scala/code/api/berlin/group/v1_3/Http4sBGv13SigningBaskets.scala:115-126` |
| **Shape** | Missing state-machine guard on DELETE |

**Description**: The `deleteSigningBasket` handler deletes the basket unconditionally:
```scala
SigningBasketX.signingBasketProvider.vend.deleteSigningBasket(basketid)
```
The ResourceDoc description explicitly states *"not deletable after a first (partial)
authorisation has been applied"*, but the handler performs no status check. After
`startSigningBasketAuthorisation` creates an authorisation sub-resource, a DELETE request
still returns 204.

**Red-bar assertion**: After creating a basket and starting an authorisation, `DELETE
/signing-baskets/BASKETID` must return `4xx`. Currently returns `204`.

**Fix pattern**: Check whether any authorisation sub-resources (challenges) exist for the
basket before deleting; if so, return 400/405 with a descriptive message.

---

### SEC-4 — Webhook SSRF via Unvalidated URL

| | |
|---|---|
| **Severity** | HIGH |
| **File** | `obp-api/src/main/scala/code/webhook/WebhookHttpClient.scala:129-144`, `code/api/v3_1_0/Http4s310.scala:2483-2506` |
| **Shape** | Server-Side Request Forgery — no host allowlist |

**Description**: `createAccountWebhook` stores the caller-supplied `url` field directly
to the DB without validating the target host. When the webhook trigger fires,
`WebhookHttpClient.composeRequest` constructs an OkHttp request directly from the stored
URL. An authenticated user with `canCreateWebhook` can register a webhook targeting
loopback (`127.0.0.1`), link-local (`169.254.169.254` — cloud instance-metadata), or any
other internal address, causing the OBP server to make server-side requests on the
attacker's behalf.

**Red-bar assertion**: `POST /banks/BANK_ID/account-web-hooks` with
`url="http://127.0.0.1:22"` must return `4xx`. Currently returns `201`.

**Fix pattern**: Validate `url` at creation time against an allowlist of permitted
external hosts (or block RFC-1918 / link-local / loopback / reserved ranges via a URL
validator before persisting the webhook).

---

### SEC-5 — IDOR on Auth-Context-Update Challenge Answer

| | |
|---|---|
| **Severity** | HIGH |
| **Files** | `obp-api/src/main/scala/code/api/v5_0_0/Http4s500.scala:779-807`, `code/context/MappedUserAuthContextUpdateProvider.scala:52-78` |
| **Shape** | Insecure Direct Object Reference — missing ownership check |

**Description**: `answerUserAuthContextUpdateChallenge` receives `AUTH_CONTEXT_UPDATE_ID`
from the URL and calls `checkAnswer(authContextUpdateId, answer, ...)` without verifying
that the authenticated user is the **owner** of that update record. Any authenticated user
can submit an answer for any other user's challenge by knowing (or guessing) the update ID
— which is a UUID returned in plain text in the create-update response.

Additionally, `MappedUserAuthContextUpdate.mChallenge` generates an integer in the range
`[0, 99_999_999]` (`csprng.nextInt(99999999)`) with no per-attempt rate-limit beyond
the TTL check, making the 8-decimal-digit challenge practical to brute-force under
concurrent requests (see also hazard K in the concurrency suite).

**Red-bar assertion**: user2 answering user1's challenge must return `403` or `404`.
Currently returns `200 / ACCEPTED`.

**Fix pattern**: In `answerUserAuthContextUpdateChallenge`, after resolving the update
record, compare `update.userId == cc.user.get.userId` and return 403 if they do not match.

---

## Deliberately Excluded (Explicit)

| Finding | Reason excluded |
|---|---|
| `makeHistoricalPayment` IDOR | Policy, not bypass: the endpoint is a `/management/` import path gated by `canCreateHistoricalTransaction` global admin role — designed for cross-account historical import. |
| Card-attribute unconditional create | Not a get-or-create race: `createOrUpdateCardAttribute(id=None)` always creates a new row; two concurrent calls producing two rows is the intended semantic. |
| CVV in card-create response | Deferred: medium severity; no separate card-data-at-rest encryption strategy defined yet. |
| 500 exception-message leak | Deferred: medium; generic `UnknownError` + stack-trace in response body, but no credential leak confirmed. |
| BerlinGroup per-request scope / consent fake-account / dynamic-endpoint SSRF | Deferred: medium; confirmed real but outside agreed scope for this batch. |

---

## Fix Patterns

| Vulnerability class | Recommended fix |
|---|---|
| SQL injection | Parameterised queries; never interpolate user input into raw SQL |
| Circular token validation | Derive expected `clientID` from server config, not the incoming token |
| Missing state-machine guard | Check `basket.status` before DELETE; return 400/405 if already authorised |
| SSRF | URL allowlist / deny RFC-1918 + loopback at creation time |
| IDOR | Ownership check: `update.userId == authenticated user id` before processing |
