# Security Audit — Session Summary

**Date**: 2026-06-11  
**Branch**: `feature/security-vulnerability-tests`  
**Tag**: `SecurityVuln` (new) + `ConcurrencyRace` (extended)

---

## What Was Done

### Phase 1 — Audit (multi-agent, completed earlier)

A multi-agent audit ran 11 vulnerability-class probes with adversarial verification
across the entire OBP-API codebase. 21 of 43 candidates were confirmed real; 22 were
refuted or shown to be already mitigated.

### Phase 2 — Red-bar tests (this session)

Five high-severity security vulnerabilities and two new concurrency races were converted
into failing ScalaTest evidence tests. Convention: each test asserts the *correct/secure*
outcome so a live vulnerability shows as a FAILED test (red bar). `testFailureIgnore` in
`obp-api/pom.xml` keeps the build green.

---

## Files Created

| File | Purpose |
|---|---|
| `SecurityVulnSetup.scala` | Base trait + `SecurityVuln` tag (mirrors `ConcurrentRaceSetup`) |
| `MetricsSqlInjectionTest.scala` | SEC-1: SQL injection evidence test |
| `OAuthIdTokenAudienceTest.scala` | SEC-2: Circular aud validation evidence test |
| `SigningBasketStateGuardTest.scala` | SEC-3: Missing DELETE status guard evidence test |
| `WebhookSsrfTest.scala` | SEC-4: Webhook SSRF evidence test (2 scenarios) |
| `AuthContextChallengeTest.scala` | SEC-5: IDOR on challenge answer evidence test |
| `SECURITY_VULNERABILITIES.md` | Full vulnerability write-up with fix patterns |
| `AUDIT_PLAN.md` | Original audit plan (copy of `~/.claude/plans/…`) |
| `AUDIT_SUMMARY.md` | This file |
| `../concurrency/ConcurrentUpsertRaceTest.scala` | BB + CC concurrency races |

---

## Expected Test Results (Red = Vulnerability Confirmed)

| ID | Test | Expected | Actual (unpatched) | Evidence |
|---|---|---|---|---|
| SEC-1 | `MetricsSqlInjectionTest` | `totalCount == 0` | `totalCount == 2` | OR-injection matches all rows |
| SEC-2 | `OAuthIdTokenAudienceTest` | `Failure` | `Full(claims)` | Token validates itself |
| SEC-3 | `SigningBasketStateGuardTest` | `4xx` | `204` | No status guard on DELETE |
| SEC-4a | `WebhookSsrfTest` (127.0.0.1) | `4xx` | `201` | No host allowlist |
| SEC-4b | `WebhookSsrfTest` (169.254.x) | `4xx` | `201` | No host allowlist |
| SEC-5 | `AuthContextChallengeTest` | `403/404` | `200` | No ownership check (IDOR) |
| BB | `ConcurrentUpsertRaceTest` | `1 row` | `8 rows` | No unique index on PlainTextRef |
| CC | `ConcurrentUpsertRaceTest` | `3 rows` | `0/exception` | tryo swallows second insert |

---

## Confirmed Vulnerabilities — Source Locations

| ID | Severity | Source File | Lines | Root Cause |
|---|---|---|---|---|
| SEC-1 | HIGH | `code/metrics/MappedMetrics.scala` | 196-202, 427 | `s"'$value'"` — no SQL escaping |
| SEC-2 | HIGH | `code/api/util/JwtUtil.scala` | 269-271 | `clientID = new ClientID(getAudience(token))` — circular |
| SEC-3 | HIGH | `code/api/berlin/group/v1_3/Http4sBGv13SigningBaskets.scala` | 115-126 | No status guard before DELETE |
| SEC-4 | HIGH | `code/webhook/WebhookHttpClient.scala` | 129-144 | No host allowlist check |
| SEC-5 | HIGH | `code/api/v5_0_0/Http4s500.scala` | 779-807 | `checkAnswer` without owner verification |
| BB | MEDIUM | `code/transaction/internalMapping/MappedTransactionIdMappingProvider.scala` | 12-41 | No UniqueIndex on `TransactionPlainTextReference` |
| CC | MEDIUM | `code/productcollectionitem/MappedProductCollectionItem.scala` | 37-59 | Delete-then-reinsert in `tryo` — race on UniqueIndex |

---

## Explicitly Excluded

| Finding | Reason |
|---|---|
| `makeHistoricalPayment` IDOR | Policy endpoint (`canCreateHistoricalTransaction` global role), not a bypass |
| Card-attribute unconditional create | Intended two-row semantic, not a race |
| CVV in card-create response | Medium severity, deferred |
| 500 exception-message leak | Medium severity, deferred |
| BerlinGroup per-request scope / consent fake-account / dynamic-endpoint SSRF | Real but out of agreed scope |

---

## Fix Patterns (Quick Reference)

| Vulnerability class | Fix |
|---|---|
| SQL injection | Parameterised queries — never interpolate user input into raw SQL |
| Circular token validation | Read expected `clientID` from props (`openid_connect_1.client_id`) |
| Missing state-machine guard | Check basket `status` before DELETE; return 405 if authorised |
| SSRF | Deny RFC-1918 / loopback / link-local at webhook creation time |
| IDOR | Compare `update.userId == cc.user.get.userId` before `checkAnswer` |
| check-then-insert (no index) | Add `UniqueIndex` on natural key; wrap insert in `tryo`; re-fetch on `Failure` |
| tryo-swallowed unique violation | Return existing rows on `Failure` instead of empty |

---

## Build & CI

- `code.security` lands in **shard-4 catch-all** automatically (no workflow change needed).
- `testFailureIgnore=true` in `obp-api/pom.xml:602` → failing evidence tests = `BUILD SUCCESS`.
- Run evidence only: `mvn ... -DtagsToInclude=code.security.SecurityVuln -DfailIfNoTests=false`
- Exclude from CI: `mvn ... -DtagsToExclude=code.security.SecurityVuln`
