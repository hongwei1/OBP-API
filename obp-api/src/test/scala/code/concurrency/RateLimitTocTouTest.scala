/**
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */
package code.concurrency

import code.api.cache.Redis
import code.api.util.RateLimitingJson.CallLimit
import code.api.util.{CallContext, RateLimitingUtil}
import net.liftweb.common.Empty

import java.util.UUID

/**
 * X. Consumer rate-limit check-then-increment TOCTOU.
 *
 * `RateLimitingUtil.underCallLimits` decides admission with `underConsumerLimits`
 * (a Redis GET/TTL read, "calls + 1 <= limit") and only afterwards calls
 * `incrementConsumerCounters` (a separate TTL + INCR/SET). Between the read and the
 * increment there is no atomicity — no Lua script, no INCR-first-then-compare — so N
 * callers can all observe the same under-limit count and all be admitted, letting a
 * consumer exceed its configured limit.
 *
 * WHY THIS TEST LIVES AT THE UTIL LAYER, NOT OVER HTTP
 * ----------------------------------------------------
 * CONCURRENCY_HAZARDS.md lists this hazard (X) as "Real and high-impact (limit bypass),
 * but active-limit lookup is cached ~1 hour -> HTTP-layer timing unreliable -> would be
 * flaky", and therefore left it untested. That objection is specifically about the
 * *upstream* lookup that populates `CallContext.rateLimiting`, not about the
 * check-then-increment itself.
 *
 * So this test injects the `CallLimit` straight onto the CallContext and calls the real
 * public `underCallLimits`. The cached lookup is never consulted, the production
 * check-then-increment path runs verbatim, and the race window is forced open with a
 * barrier instead of hoped for via HTTP timing. That removes the flakiness the earlier
 * audit was worried about while still exercising the genuinely-unfixed code.
 *
 * Asserts the correct outcome (never admit more than the limit), so it is EXPECTED TO
 * FAIL while the check-then-increment remains non-atomic.
 * Tagged ConcurrencyRace.
 */
class RateLimitTocTouTest extends ConcurrentRaceSetup {

  /** A limit that only constrains the period under test; -1 disables the others. */
  private def perMinuteOnly(consumerId: String, limit: Long) = CallLimit(
    consumer_id = consumerId,
    api_name    = None,
    api_version = None,
    bank_id     = None,
    per_second  = -1,
    per_minute  = limit,
    per_hour    = -1,
    per_day     = -1,
    per_week    = -1,
    per_month   = -1
  )

  feature("Consumer rate limiting admits no more calls than the configured limit") {

    scenario("X: concurrent calls at the boundary must not bypass the per-minute limit",
             ConcurrencyRace, KnownOpenHazard) {

      Given("a reachable Redis and consumer limits enabled")
      // Redis-unavailable makes underConsumerLimits fail OPEN, which would admit every
      // call and produce a red bar for the wrong reason. Skip rather than lie.
      assume(Redis.isRedisReady, "Redis is not reachable — rate-limit counters cannot be exercised")
      assume(RateLimitingUtil.useConsumerLimits, "use_consumer_limits is false — rate limiting disabled")

      // A fresh consumer id per run keeps the Redis counter isolated from sibling suites
      // (the whole JVM shares one server and one Redis in forkMode=once).
      val consumerId = s"toctou-${UUID.randomUUID().toString.take(12)}"
      val limit      = 5L
      val n          = 20
      val callLimit  = perMinuteOnly(consumerId, limit)

      When(s"$n callers hit underCallLimits concurrently with a per-minute limit of $limit")
      // All n threads meet at a barrier before entering the critical section, so the
      // read-then-increment windows genuinely overlap.
      val results = runConcurrentWithBarrier(n) { _ =>
        RateLimitingUtil.underCallLimits((Empty, Some(CallContext(rateLimiting = Some(callLimit)))))
      }

      // Exceeding a limit is signalled by fullBoxOrException throwing a 429 APIFailure,
      // so an admitted call is exactly a Success here.
      val accepted = results.count(_.isSuccess)
      val rejected = results.size - accepted
      info(s"accepted=$accepted rejected=$rejected limit=$limit (consumer $consumerId)")

      Then(s"at most $limit calls may be admitted")
      withClue(
        s"Rate limit bypassed: $accepted of $n concurrent calls were admitted but the limit is $limit. " +
        s"underConsumerLimits read the counter and incrementConsumerCounters wrote it as two " +
        s"separate Redis operations, so every caller saw the same under-limit value. ") {
        accepted.toLong should be <= limit
      }
    }
  }
}
