package code.concurrency

import code.api.util.{APIUtil, FutureUtil}
import org.scalatest.{FlatSpec, Matchers}

import java.util.UUID
import scala.concurrent.{Await, Future, Promise}
import scala.concurrent.duration._
import scala.util.{Failure, Success, Try}

/**
 * A: futureWithLimits must self-heal the open-futures counter.
 *
 * THE HAZARD:
 *   incrementFutureCounter runs synchronously; decrementFutureCounter only ran inside the
 *   wrapped Future's .map/.recover. A Future that never completes (hung backend) never
 *   decremented, so openFuturesCount for that service grew unbounded, ratcheting
 *   APIUtil.getBackOffFactor to its worst tier (1024) — canOpenFuture's modulo check then
 *   rejected ~all subsequent calls with ServiceIsTooBusy, permanently, until JVM restart.
 *
 * The fix adds a reaper TimerTask (3-arg futureWithLimits overload) that decrements the
 * counter on a timeout ceiling if the wrapped Future hasn't completed by then, guarded by
 * an AtomicBoolean so the reaper and the Future's own completion can never both decrement.
 *
 * This is a pure Future/counter test — it does not touch the DB or HTTP layer, so it does
 * not extend ConcurrentRaceSetup/ServerSetupWithTestData (avoids an unnecessary full Lift
 * server boot). Tagged ConcurrencyRace for consistency with the rest of the suite.
 */
class ConcurrentBackoffCounterSelfHealTest extends FlatSpec with Matchers {

  private implicit val ec: scala.concurrent.ExecutionContext = scala.concurrent.ExecutionContext.Implicits.global

  private def openFuturesCount(serviceName: String): Int =
    APIUtil.serviceNameCountersMap.getOrDefault(serviceName, (0, 0))._2

  /**
   * Poll until the counter reaches `expected`, up to `timeout`, and return what it finally was.
   *
   * The decrements this suite asserts on happen in a reaper TimerTask and in the wrapped Future's
   * onComplete — both asynchronous, neither ordered against `Await.result` returning. Sleeping a
   * fixed amount and then asserting therefore encodes a guess about scheduling latency, and the
   * guess is wrong exactly when the machine is busy: this suite runs in a four-shard parallel
   * build, and "no regression when it completes immediately" failed there with count=1 while
   * passing every time in isolation. Polling waits only as long as it must and fails honestly
   * when the value genuinely never arrives.
   */
  private def awaitCount(serviceName: String, expected: Int, timeout: FiniteDuration): Int = {
    val deadline = timeout.fromNow
    while (openFuturesCount(serviceName) != expected && deadline.hasTimeLeft()) Thread.sleep(10)
    openFuturesCount(serviceName)
  }

  "futureWithLimits" should "self-heal the open-futures counter when the wrapped Future never completes" taggedAs ConcurrencyRace in {
    val serviceName = s"__conc_backoff_selfheal_${UUID.randomUUID.toString.take(8)}"
    val neverCompletes = Promise[Unit]().future

    FutureUtil.futureWithLimits(neverCompletes, serviceName, reaperTimeoutMillis = 200)

    val counted = awaitCount(serviceName, expected = 0, timeout = 5.seconds)
    withClue(s"openFuturesCount=$counted after the reaper should have fired: ") {
      counted shouldBe 0
    }
    APIUtil.canOpenFuture(serviceName) shouldBe true
  }

  it should "not double-decrement when the underlying Future completes late, after the reaper already fired" taggedAs ConcurrencyRace in {
    val serviceName = s"__conc_backoff_idempotent_${UUID.randomUUID.toString.take(8)}"
    val promise = Promise[String]()

    val wrapped = FutureUtil.futureWithLimits(promise.future, serviceName, reaperTimeoutMillis = 200)
    // The scenario requires the reaper to have fired BEFORE the Future completes, so wait for its
    // decrement rather than assuming a sleep outlasted it.
    awaitCount(serviceName, expected = 0, timeout = 5.seconds) shouldBe 0
    promise.success("late value")
    Await.result(wrapped, 5.seconds)
    // The onComplete callback runs after Await returns. A second decrement would take the counter
    // to -1, so settle briefly and assert it did not move at all.
    Thread.sleep(200)

    withClue(s"openFuturesCount=${openFuturesCount(serviceName)}: reaper and completion both firing must not double-decrement: ") {
      openFuturesCount(serviceName) shouldBe 0
    }
  }

  it should "behave exactly like the original Future when it completes immediately (no regression)" taggedAs ConcurrencyRace in {
    val serviceNameOk = s"__conc_backoff_ok_${UUID.randomUUID.toString.take(8)}"
    val serviceNameFail = s"__conc_backoff_fail_${UUID.randomUUID.toString.take(8)}"

    val okResult = Try(Await.result(FutureUtil.futureWithLimits(Future.successful("ok"), serviceNameOk, reaperTimeoutMillis = 5000), 5.seconds))
    val failResult = Try(Await.result(FutureUtil.futureWithLimits(Future.failed[String](new RuntimeException("boom")), serviceNameFail, reaperTimeoutMillis = 5000), 5.seconds))

    okResult shouldBe Success("ok")
    failResult match {
      case Failure(e) => e.getMessage shouldBe "boom"
      case Success(_) => fail("expected the failed Future to propagate its failure")
    }

    // Await.result returns when the wrapped Future completes; the decrement happens in its
    // onComplete, which is scheduled separately. Poll for it instead of racing it.
    val okCount = awaitCount(serviceNameOk, expected = 0, timeout = 5.seconds)
    withClue(s"openFuturesCount(ok)=$okCount: ") { okCount shouldBe 0 }
    val failCount = awaitCount(serviceNameFail, expected = 0, timeout = 5.seconds)
    withClue(s"openFuturesCount(fail)=$failCount: ") { failCount shouldBe 0 }
  }
}
