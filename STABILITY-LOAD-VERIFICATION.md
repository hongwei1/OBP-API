# OBP-API Stability & Load Verification

Companion to `obp-api/src/test/scala/code/concurrency/CONCURRENCY_HAZARDS.md`.
That document covers **correctness** hazards (lost updates, duplicate inserts, state-machine
races). This one covers **saturation and resilience** — what happens to connections, threads
and caches under concurrency — and records what has actually been *measured* rather than
inferred from reading source.

> **Status**: Phases 0, 1 and part of 3 are done and reproducible. The Tier-B saturation
> scenarios (S1/S2/S3/S5/S6) have their harness in place (`LoadScenarioSetup`) but the
> individual scenarios are not written yet. Nothing in this document is a capacity number —
> see *Measurement limits* before quoting anything.

---

## 1. Reference — the three framings

### A. Dimensions to watch

| # | Dimension | What to check | Known OBP-API weak point |
|---|---|---|---|
| 1 | Resource pools | size / acquire timeout / connections per request / held across external calls | Hikari default **10** (`CustomDBVendor.scala:31`); write transaction holds a connection across the whole route body |
| 2 | Thread pools / ECs | blocking calls on shared compute pools; dedicated blocking pool? | `Connector.scala:236-237` `Await.result` on the shared fork-join EC — inherited by *every* connector |
| 3 | Concurrency correctness | rate-limit atomicity, cache single-flight, shared mutable state, double-spend | rate limit is check-then-increment; `memoize` has no single-flight |
| 4 | Backpressure / unbounded growth | unbounded fan-out, unbounded queues, pagination caps | `Http4s600.scala:798` `Future.sequence` whose width is the client-supplied `obp_limit`, which has no hard cap |
| 5 | Cache behaviour | stampede, TTL jitter, cold-start herd, negative caching | no single-flight (measured below) |
| 6 | Timeouts / fault propagation | per-layer deadlines, cancellation, fail-open vs fail-closed | rate limiting fails **open** when Redis is down |
| 7 | Dependency degradation | behaviour when DB/Redis/adapter fail; what readiness is bound to | `/status` returns 503 when Redis is down (`StatusPage.scala:51`) |
| 8 | Lifecycle | cold-start herd, warm-up, graceful shutdown | not yet measured |
| 9 | Background tasks | scheduler exception guards, lock lease self-healing | not yet measured |
| 10 | Observability | are pools/threads/GC visible; log storms | **no metrics export at all** — no Prometheus, Micrometer, Dropwizard or JMX exporter in any `pom.xml` |

### B. How to verify — reading source only produces hypotheses

| Method | Purpose | How it is done here |
|---|---|---|
| Load / saturation | find the knee | JVM-internal driver (`LoadScenarioSetup`), not an external generator — see *Why no k6* |
| Fault injection | verify propagation | process-level: `service redis-server stop`, `kill -TERM` |
| Soak | catch leaks | repeat a path and watch pool counters return to baseline |
| Resource monitoring | locate the bottleneck | `monitor/poll-endpoints.sh` + `monitor/jvm-snapshot.sh` |
| Concurrency unit tests | catch races | `ConcurrentRaceSetup` barrier primitives |

### C. Scenarios worth pressing

1. Slow downstream + high concurrency → pool and thread-pool starvation
2. Hot-key concurrency → cache stampede **(measured — see 3.2)**
3. Large `obp_limit` + concurrency → unbounded fan-out
4. Rate-limit boundary burst → limit bypass **(measured — see 3.1)**
5. Redis outage → readiness amplification
6. Restart under load → drain and cold start

---

## 2. Phase 0 — re-anchoring the premise (the most important result)

`CONCURRENCY_HAZARDS.md` states **"19 PASSED (all hazards fixed) · 0 FAILED"**. Re-running that
suite before building anything on top of it produced two corrections.

### 2.1 Scenario A was passing vacuously

`test.default.props.template` does **not** set `transactionRequests_enabled`, so it defaults to
false. Scenario A (`ConcurrentTransferRaceTest`, "N concurrent transfers must not lose balance
updates") fires 10 transfers and then asserts:

```scala
val completed = responses.count { r => r.code == 201 && status == COMPLETED }   // → 0
actualDebited should equal(completed * debitPerTransfer)                        // → 0 == 0 ✅
```

With transaction requests disabled every request returns `400 OBP-40018`, so `completed` is 0 and
the assertion is trivially satisfied. **The green bar for the money-movement lost-update hazard
does not demonstrate that the hazard is fixed** — it demonstrates that no transfer ran.

Baseline run, template props unmodified: **18 passed, 1 failed** (scenario B failed openly on the
same `OBP-40018`, which is what exposed the misconfiguration).

### 2.2 With transaction requests enabled, A and B time out

Setting `transactionRequests_enabled=true` and re-running: scenarios **A and B both fail with
`Read timeout to localhost:8016 after 60000 ms`**, reproducibly across runs.

Thread dumps taken during the run (`jcmd Thread.print` every 8 s, 25 samples) show:

- **no threads in `HikariPool.getConnection`** at any sample → this is *not* pool exhaustion;
- during the concurrent-write window, 8 `pool-3-thread-N` workers parked ~9 s inside
  `LocalMappedConnector.saveHistoricalTransaction → DoobieBankAccountQueries.updateBalance`,
  which is the `SELECT FOR UPDATE` serialisation that `CONCURRENCY_HAZARDS.md` documents as the
  *fix* for hazards A/S — i.e. expected contention, not a defect;
- total threads stayed flat (52–55) through the A/B window with only 7 `Await` frames.

**Root cause of the A/B 60-second hang is still open.** It is not thread starvation and not pool
exhaustion on the evidence collected. It must be resolved before scenario A can prove anything
about the lost-update hazard.

### 2.3 What this means

The suite's own conclusions should be read with the props configuration attached. Recommended
follow-ups, deliberately **not** done here because they widen blast radius beyond this change:

- decide whether `transactionRequests_enabled=true` belongs in the shared test template (it
  affects every suite, not just this one);
- give scenario A a guard so it cannot pass with `completed == 0`;
- root-cause the 60 s timeout.

---

## 3. Measured hazards

Both tests assert the **correct** outcome, so both are red until fixed — the same convention the
existing race suites use. Run them with:

```sh
mvn -pl obp-api scalatest:test -DfailIfNoTests=false \
  -DtagsToInclude=code.concurrency.ConcurrencyRace \
  -DwildcardSuites="code.concurrency.RateLimitTocTouTest,code.concurrency.CacheSingleFlightRaceTest"
```

### 3.1 Consumer rate-limit bypass — `RateLimitTocTouTest`

`RateLimitingUtil.underCallLimits` admits a call via `underConsumerLimits` (a Redis GET/TTL read,
`calls + 1 <= limit`) and only afterwards calls `incrementConsumerCounters` (a separate TTL +
INCR/SET). No Lua script, no INCR-first-then-compare. Concurrent callers all observe the same
under-limit count and are all admitted.

`CONCURRENCY_HAZARDS.md` lists this as hazard **X**, "Real and high-impact (limit bypass)", and
leaves it untested because "active-limit lookup is cached ~1 hour → HTTP-layer timing unreliable →
would be flaky". That objection applies to the *upstream lookup that populates*
`CallContext.rateLimiting`, not to the check-then-increment itself. This test therefore injects
the `CallLimit` straight onto a `CallContext`, calls the real public `underCallLimits`, and forces
the window open with a barrier. The cached lookup is never consulted.

**Measured, limit = 5, 20 concurrent callers, 4 runs:**

| Run | Admitted | Limit | Bypass factor |
|---|---:|---:|---:|
| 1 | 16 | 5 | 3.2× |
| 2 | 19 | 5 | 3.8× |
| 3 | 14 | 5 | 2.8× |
| 4 | 14 | 5 | 2.8× |

Never spuriously green — the flakiness concern that blocked this test does not materialise at the
util layer.

### 3.2 Cache stampede — `CacheSingleFlightRaceTest`

`Redis.memoizeSyncWithRedis` / `memoizeWithRedis` (`Redis.scala:264-270`) delegate straight to
scalacache's `memoizeSync` / `memoize`, a plain check-then-compute-then-store with no per-key lock
or in-flight promise sharing. Every concurrent miss re-runs the wrapped block — in production a
connector call or DB query, so the stampede multiplies backend load and Hikari demand by the
concurrency factor.

**Measured: 12 concurrent misses on one cold key → 7 underlying computations** (correct: 1).

---

## 4. Monitoring harness

`development/load-testing/monitor/`

- **`poll-endpoints.sh`** — DirectLogin, then sample `/obp/v6.0.0/system/database/pool`,
  `/status` and `/obp/v6.0.0/system/cache/info` into CSV.

  It **preflights the pool endpoint and exits on 403**. This matters: the handler
  (`Http4s600.scala:1146`) only calls `withUser`, which looks like authentication-only, but
  `ResourceDocMiddleware.validateRoles` enforces the roles declared on the ResourceDoc, and that
  doc declares `Some(canGetDatabasePoolInfo :: Nil)` (`Http4s600.scala:8110`). An authenticated
  user without the entitlement gets 403 on every sample; recording `-1` for a whole run would
  look like a working poller.

- **`jvm-snapshot.sh`** — periodic `jcmd Thread.print` / `GC.heap_info`, summarising total
  threads, threads parked in `Await`, BLOCKED threads and heap used. This is what produced the
  §2.2 evidence.

---

## 5. Measurement limits — read before quoting any number

- **4 cores, 15 GiB, no swap.** Load generator, API server, Postgres/H2 and Redis share those
  cores. Absolute latencies here are **not** capacity numbers and must never be quoted as
  throughput limits. Only *relative* and *directional* claims are supportable: does a slow
  endpoint starve an unrelated fast one, does `threads_awaiting_connection` climb, does a counter
  stay bounded.
- **The correctness suite runs on in-memory H2, not Postgres.** As `CONCURRENCY_HAZARDS.md` puts
  it: reproduced on H2 ⟹ Postgres has it too; *not* reproduced on H2 does **not** imply Postgres
  is safe.
- **Why no k6 / toxiproxy / RabbitMQ.** None are installed, and the Docker daemon is not running,
  so container-based fault injection is unavailable. Rather than block, load is driven from
  inside the JVM (`LoadScenarioSetup`) and faults are injected at process level. Trade-off: zero
  install and CI-runnable, but the generator shares CPU with the server under test.
- `hikari.maximumPoolSize` is **20** in the test profile and defaults to **10** everywhere else,
  so test-profile results are more forgiving than production defaults.

---

## 6. Tags

| Tag | Contents | CI |
|---|---|---|
| `code.concurrency.ConcurrencyRace` | correctness races; the suites carrying only this tag all pass | **runs** — they are regression guards |
| `code.concurrency.KnownOpenHazard` | asserts correct behaviour for a hazard confirmed still open, so red by design | excluded |
| `code.concurrency.LoadScenario` | saturation scenarios, tens of seconds each | excluded |

A known-open hazard cannot act as a regression guard — it would fail every build and train
people to ignore a red bar. So CI excludes `KnownOpenHazard` specifically rather than dropping
the whole `ConcurrencyRace` tag, which keeps the seven passing race suites running.

Scenarios for an open hazard carry **both** tags: `ConcurrencyRace` so the usual local command
still picks them up, and `KnownOpenHazard` so CI can filter them out. ScalaTest applies excludes
over includes, so the combination behaves as intended. **Remove `KnownOpenHazard` as soon as the
hazard is fixed** — that is what turns the scenario into a regression guard.

**How the exclusion is wired — this is a trap worth knowing.** `scalatest-maven-plugin` had its
`<tagsToExclude>` **hardcoded** in `obp-api/pom.xml`, and a hardcoded plugin `<configuration>`
value silently wins over `-DtagsToExclude` on the command line. Any `-DtagsToExclude=...` you
type is simply ignored — which is why the command previously printed in `CONCURRENCY_HAZARDS.md`
never actually excluded anything. The list is now the property `scalatest.tagsToExclude`
(default in the root `pom.xml`), so it is overridable:

```sh
# default — CI and a plain local run both skip KnownOpenHazard and LoadScenario
mvn -pl obp-api scalatest:test -DfailIfNoTests=false

# run the known-open hazards too (what you want when verifying a fix)
mvn -pl obp-api scalatest:test -DfailIfNoTests=false \
  -Dscalatest.tagsToExclude=code.external,GetBanksPerf

# narrow to one suite as usual
mvn -pl obp-api scalatest:test -DfailIfNoTests=false \
  -Dscalatest.tagsToExclude=code.external,GetBanksPerf \
  -DwildcardSuites="code.concurrency.RateLimitTocTouTest"
```

---

## 7. Open items

| Item | State |
|---|---|
| Root-cause the A/B 60 s timeout with transaction requests enabled | **open** |
| Scenario A guard against vacuous pass | open, deliberately out of scope here |
| S1 thread-pool saturation (`waiting-for-godot`) | harness ready, scenario not written |
| S2 hot-key stampede over HTTP | harness ready, scenario not written |
| S3 large `obp_limit` fan-out | harness ready, scenario not written |
| S5 Redis outage → readiness amplification | harness ready, scenario not written |
| S6 restart under load | harness ready, scenario not written |
| Connection-leak soak (`RequestScopeConnection` commit path) | not written — needs a deterministic commit failure, which H2 cannot express via deferred constraints |

### A note on the connection-leak hazard

`RequestScopeConnection.scala:213` calls `realConn.commit()` **without** a try/catch, while the
rollback branch at `:215` and the close at `:217` both have one. Because the branches are
sequenced with `*>`, a throwing `commit()` short-circuits and `close()` never runs — the
connection is never returned to the pool. This is confirmed by reading the source but is **not
yet covered by a test**: forcing `commit()` to throw deterministically needs a deferred-constraint
violation, which H2 does not support, so a meaningful test requires running this suite against
Postgres. Recorded here so the gap is explicit rather than silently absent.
