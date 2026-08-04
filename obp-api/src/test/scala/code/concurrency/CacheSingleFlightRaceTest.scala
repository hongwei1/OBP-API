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

import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger
import scala.concurrent.duration._

/**
 * CS. Cache stampede — memoize has no single-flight guard.
 *
 * `Redis.memoizeSyncWithRedis` / `memoizeWithRedis` delegate straight to scalacache's
 * `memoizeSync` / `memoize`, whose macro is a plain check-then-compute-then-store. There
 * is no per-key lock, no in-flight promise sharing, no request coalescing. So when a hot
 * key expires (or is cold on start-up) and N requests miss simultaneously, every one of
 * them runs the underlying computation — the expensive thing the cache exists to avoid.
 *
 * In production the wrapped computation is a connector call or a DB query, so a stampede
 * on one popular account multiplies straight into N backend calls and N Hikari
 * connections, which is the amplifier in the pool-exhaustion chain.
 *
 * This test counts how many times the underlying block actually executes when N callers
 * miss the same key together. The correct behaviour for a cache that protects a backend
 * is exactly once; asserting that means this is EXPECTED TO FAIL until a single-flight
 * guard is added.
 * Tagged ConcurrencyRace.
 */
class CacheSingleFlightRaceTest extends ConcurrentRaceSetup {

  feature("Cache protects the underlying computation from concurrent misses") {

    scenario("CS: N concurrent misses on one cold key must compute only once", ConcurrencyRace) {

      Given("a reachable Redis and a key that has never been populated")
      // With Redis down scalacache falls back to computing every time, which would make
      // this red for an unrelated reason. Skip instead of reporting a false hazard.
      assume(Redis.isRedisReady, "Redis is not reachable — cache behaviour cannot be exercised")

      // Fresh key per run: a leftover value from a sibling suite would turn every caller
      // into a cache HIT and hide the stampede entirely.
      val cacheKey = s"single-flight-${UUID.randomUUID().toString.take(12)}"
      val computations = new AtomicInteger(0)
      val n = 12

      When(s"$n callers miss the same cold key simultaneously")
      // The barrier is what makes this deterministic: without it the first caller can
      // finish and populate the cache before the rest arrive, and everyone else hits.
      val results = runConcurrentWithBarrier(n) { _ =>
        Redis.memoizeSyncWithRedis(Some(cacheKey))(10.seconds) {
          computations.incrementAndGet()
          // Stand in for the connector/DB call a real cached block wraps. Long enough
          // that all N callers are inside the miss window together.
          Thread.sleep(200)
          s"value-for-$cacheKey"
        }
      }

      val computed = computations.get()
      val succeeded = results.count(_.isSuccess)
      info(s"callers=$n succeeded=$succeeded underlying computations=$computed")

      Then("the underlying computation must have run exactly once")
      withClue(
        s"Cache stampede: $computed of $n concurrent misses each ran the underlying computation. " +
        s"memoizeSyncWithRedis delegates to scalacache's check-then-compute-then-store with no " +
        s"per-key single-flight guard, so every concurrent miss re-executes the wrapped block. " +
        s"In production that block is a connector/DB call, so this multiplies backend load and " +
        s"Hikari connection demand by the concurrency factor. ") {
        computed should equal(1)
      }
    }
  }
}
