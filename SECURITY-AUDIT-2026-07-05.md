# OBP-API Security & Stability Audit — 2026-07-05

> Scope: whole-codebase read-only audit on branch `develop-obp` (commit `6b1022688`), focused on three angles: (1) authentication/authorization/credentials/injection, (2) dynamic code execution/injection/SSRF, (3) concurrency/resource exhaustion/stability. Not a diff review — no PR/branch comparison was in scope. Three parallel sub-agents, ~340k tokens combined.

## Critical — act immediately

### 1. Dynamic code execution = Remote Code Execution
`obp-api/src/main/scala/code/api/util/DynamicUtil.scala`

The project has a runtime "compile and execute user-supplied code" feature (JS / Java / Scala). All three paths are unguarded:

- **GraalVM JS context wide open** (line 463): `HostAccess.ALL` + `allowHostClassLookup(_ => true)` — submitted JS can call `Java.type('java.lang.Runtime').exec(...)` directly.
- **Scala ToolBox compiles and immediately executes** (line 56): request-body `methodBody` is compiled then `func()` is run; dependency validation defaults to off (`dynamic_code_compile_validate_enable=false`).
- **Sandbox is a silent no-op on JDK24+** (line 211): JEP 486 removed `SecurityManager`; the project runs JDK25. The code's own comment says "file, network and reflection access unguarded."
- **Java JSR-223 eval path** (line 498): same trust boundary, medium severity only because it depends on a working `java` script engine being present.
- **Possible secondary injection via case-class codegen** (line 127): `toCaseObject` string-templates Scala source from JSON field names; unescaped identifiers could reach the same ToolBox sink — flagged for follow-up, not confirmed.

**Trigger**: any principal holding `canCreateConnectorMethod`, `canCreate*DynamicMessageDoc`, or `canCreate*DynamicResourceDoc` (bank-level admin, not necessarily super-admin) has effective host RCE.

**Recommendation**: treat these entitlements as equivalent to shell access; never delegate to tenant/bank-level admins. Evaluate disabling dynamic code compilation in production entirely.

## High — address soon

### 2. RabbitMQ RPC reply has no timeout → site-wide cascading failure
`obp-api/src/main/scala/code/bankconnectors/rabbitmq/RabbitMQUtils.scala:60`

Reply wait is a bare `Promise.future` with no timeout. Adapter outage or dropped message → request hangs forever, leaking one channel + one reply queue per call. **Worst part**: the hung POST still holds its HikariCP connection — ≥10 in-flight requests during an adapter outage drains the default pool of 10 → **every DB-touching request across the whole site returns 500; recovery requires a restart.**

### 3. Live secrets committed in default.props
`obp-api/src/main/resources/props/default.props`

Uncommented, production-looking secrets: OIDC `client_secret` (lines 481, 467), `importer_secret` (line 138); `production.default.props` repeats `importer_secret` and carries an MSSQL password. Any operator deploying with the repo's default props unmodified (a known common OBP mistake) exposes live credentials that are also permanently in git history. **These need rotation regardless of code fix.**

### 4. Consent HMAC secret + token logged in cleartext at DEBUG
`obp-api/src/main/scala/code/api/util/JwtUtil.scala:80`

If DEBUG logging is ever enabled for troubleshooting, every consent verification writes the full consent JWT **and its HMAC signing secret** to logs. Anyone with log read access can forge arbitrary consent JWTs for that consent → full account/data access within (and beyond) the granted scope.

### 5. HikariCP pool defaults to 10, each write request holds a connection for its full lifetime
`obp-api/src/main/scala/bootstrap/liftweb/CustomDBVendor.scala:31`

Any slow downstream + ≥10 concurrent writes exhausts the pool; even auth-check queries then queue and time out at 30s, so GETs fail too. The project's own CLAUDE.md documents this exact failure mode (tests bumped to pool=20) but **production default remains 10**.

### 6. Rate limiting fails open when Redis is unavailable
`obp-api/src/main/scala/code/api/util/RateLimitingUtil.scala:212`

Redis outage/latency → rate limiting is bypassed entirely → an unbounded surge hits the DB pool and connectors with no guard, exactly when the system is already degraded. Exploitable for cascading DoS amplification.

### 7. SSRF via dynamic-endpoint / method-routing outbound URL
`obp-api/src/main/scala/code/bankconnectors/rest/RestConnector_vMar2019.scala:7090`

Outbound target URL for dynamic endpoints is user-controllable (OpenAPI `servers[0].url` or the MethodRouting `url` field) with no internal-address allow/deny list. A principal with `canCreateDynamicEndpoint` can make OBP fetch `169.254.169.254` (cloud metadata) or internal-only services and relay the response back.

### 8. Idempotency middleware allows cross-client response leakage + unbounded Redis writes
`obp-api/src/main/scala/code/api/util/http4s/IdempotencyMiddleware.scala:184`

Anonymous clients share one scope; two callers using the same short `Idempotency-Key` + same body hash can receive each other's cached response (data leak/staleness). Each unique key writes a full response body to Redis for 24h with no quota — can be used to fill Redis and trigger finding #6's fail-open, a self-reinforcing DoS.

### 9. MethodRouting cache disabled by default → DB QPS amplified tens of times
`obp-api/src/main/scala/code/api/util/NewStyle.scala:3301`

`methodRouting.cache.ttl.seconds` defaults to `"0"` (no caching); `StarConnector` queries the routing table on every intercepted call, and a single API request typically triggers dozens of connector calls. Compounds with finding #5 to accelerate pool exhaustion.

## Medium — schedule

| # | Issue | Location |
|---|---|---|
| 10 | RabbitMQ connection pool capped at 5; >5 concurrent connector calls queue for 30s; combined with #2's leak can hit broker `channel_max` | `RabbitMQConnectionPool.scala:60` |
| 11 | `InternalConnector` hardcodes `Await.result(..., 5.minutes)`; one stuck downstream call can block a thread-pool worker for 5 minutes | `Connector.scala:237` |
| 12 | Request-scoped transaction connection propagation across thread/EC boundaries is fragile; a "bare" `Future` that escapes the TTL-wrapped execution context can silently fall back to an auto-commit connection, causing **partial commits that cannot be rolled back** (data inconsistency) | `RequestScopeConnection.scala:168` |
| 13 | `verifyOidcClient` compares `client_secret` with non-constant-time `==` — timing oracle for secret enumeration | `Http4s600.scala:1821` |
| 14 | Username enumeration: distinct "user not found" vs "wrong password" messages, usernames logged at INFO on every attempt | `AuthUser.scala:748` |
| 15 | `SecureLoggingDemo` logs `client_secret`/`access_token` in cleartext; it's ordinary callable code, not test-scoped | `SecureLoggingDemo.scala:25` |

## Confirmed safe (cross-checked, worth recording to avoid re-litigating)

- **SQL injection**: Doobie query layers are fully parameterized; every `Fragment.const`-spliced identifier is validated/whitelisted first. No injection found.
- **Consent JWT verification order**: signature is verified before status/expiry/consumer checks; per-consent HMAC secret means `alg:none`/algorithm-confusion is not reachable.
- **Password storage**: BCrypt via Lift `MappedPassword`; no plaintext/weak-hash fallback in the verification path.
- **Endpoint registration order NPE**: a full script scan of every `Http4s*.scala` found 41 "declaration after registration" instances — all are `lazy val`, which is safe by Scala semantics (no premature `null` capture as CLAUDE.md's cautionary pattern would imply). No site-wide NPE risk found.
- **AMQP deserialization**: `ObjectInputStream` path has an `ObjectInputFilter` allowlist — mitigated.

## Suggested priority order

1. **Add a timeout to RabbitMQ RPC replies (#2)** — single point of failure that can take down the whole site; small change, large payoff.
2. **Rotate and remove secrets from default.props (#3)** — already potentially exposed; risk grows the longer it sits.
3. **Tighten dynamic-code entitlements (#1)** — evaluate disabling the feature in production, or at minimum restrict who can hold the relevant roles.
