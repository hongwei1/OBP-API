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

import code.api.util.APIUtil
import code.setup.APIResponse
import dispatch.Req
import org.scalatest.Tag

import scala.concurrent.Future
import scala.concurrent.duration._

/**
 * Tag for the load / saturation scenarios (Tier B).
 *
 * Kept SEPARATE from ConcurrencyRace on purpose. ConcurrencyRace scenarios are fast,
 * deterministic correctness checks; these deliberately saturate a resource and take
 * tens of seconds each, so CI must be able to exclude them independently:
 *   run only these:   mvn ... scalatest:test -DtagsToInclude=code.concurrency.LoadScenario -DfailIfNoTests=false
 *   exclude from CI:  mvn ... scalatest:test -DtagsToExclude=code.concurrency.LoadScenario
 */
object LoadScenario extends Tag("code.concurrency.LoadScenario")

/**
 * Shared helpers for the load / saturation scenarios.
 *
 * These drive load from inside the JVM using the same fan-out primitive as the race
 * suites (fireConcurrently over the shared dispatch client) rather than an external
 * generator. That is a deliberate trade-off:
 *   + zero install, runnable in CI, no k6/toxiproxy/docker daemon needed
 *   + reuses the existing ConcurrentRaceSetup fixtures and DB assertions
 *   - the generator shares CPU with the server under test
 *
 * MEASUREMENT CAVEAT — read before trusting any number these produce.
 * Load generator, API server, Postgres/H2 and Redis all share the same few cores here.
 * Absolute latencies are therefore NOT capacity numbers and must never be quoted as
 * throughput limits. What these scenarios CAN establish reliably is *relative* and
 * *directional*: does a slow endpoint starve an unrelated fast one, does
 * threads_awaiting_connection climb, does a counter stay bounded. Assertions below are
 * written against those relative properties, never against an absolute millisecond budget.
 */
trait LoadScenarioSetup extends ConcurrentRaceSetup {

  private implicit val loadEc: scala.concurrent.ExecutionContext =
    scala.concurrent.ExecutionContext.Implicits.global

  def v5_1_0_Request: Req = baseRequest / "obp" / "v5.1.0"

  /** Anonymous server-side sleep endpoint — pure thread-pool pressure with no DB, no auth,
    * no connector. Ideal for isolating "is the request path itself starved?" from
    * "is the DB pool exhausted?". */
  def godotRequest(sleepMs: Long): Req =
    (v5_1_0_Request / "waiting-for-godot").GET <<? Map("sleep" -> sleepMs.toString)

  /** Run `mk` n times concurrently, recording per-request wall-clock latency in millis. */
  def fireTimed(n: Int, timeout: scala.concurrent.duration.FiniteDuration = 180.seconds)(
      mk: Int => Future[APIResponse]): List[(APIResponse, Long)] =
    fireConcurrently(n, timeout) { i =>
      val started = System.nanoTime()
      mk(i).map { resp => (resp, (System.nanoTime() - started) / 1000000L) }
    }

  /** Nearest-rank percentile. Small samples here, so no interpolation — the rank is
    * reported alongside so a reader can see how few points back the number. */
  def percentile(values: Seq[Long], p: Double): Long =
    if (values.isEmpty) -1L
    else {
      val sorted = values.sorted
      val rank = math.ceil(p / 100.0 * sorted.size).toInt
      sorted(math.max(0, math.min(sorted.size - 1, rank - 1)))
    }

  def latencySummary(label: String, latencies: Seq[Long]): String = {
    val p50 = percentile(latencies, 50)
    val p99 = percentile(latencies, 99)
    val lo = if (latencies.isEmpty) -1L else latencies.min
    val hi = if (latencies.isEmpty) -1L else latencies.max
    s"$label n=${latencies.size} min=$lo p50=$p50 p99=$p99 max=$hi"
  }

  /** Hikari pool counters read straight off the MXBean — the same numbers the
    * /obp/v6.0.0/system/database/pool endpoint serves, but without needing an
    * authenticated HTTP round trip (and without needing CanGetDatabasePoolInfo). */
  def poolSnapshot(): (Int, Int, Int, Int) = {
    val pool = APIUtil.vendor.HikariDatasource.ds.getHikariPoolMXBean
    if (pool == null) (-1, -1, -1, -1)
    else (pool.getActiveConnections, pool.getIdleConnections, pool.getTotalConnections, pool.getThreadsAwaitingConnection)
  }

  def poolMaxSize: Int = APIUtil.vendor.HikariDatasource.config.getMaximumPoolSize
}
