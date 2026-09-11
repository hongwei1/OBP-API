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

import code.scheduler.JobScheduler
import net.liftweb.mapper.By

import java.util.UUID
import scala.util.Success

/**
 * The `jobscheduler` table is the leader lock that stops two OBP instances from running the
 * same scheduled job at once. It only works if the database refuses a second row for a job
 * name — the schedulers acquire by "find no row, then INSERT", which on its own is a plain
 * check-then-act race that both contenders can win.
 *
 * These scenarios pin the two properties that make it a lock rather than a hint:
 *
 *  K1. A second acquire of a held lock is refused — deterministic, and the direct regression
 *      guard for `UniqueIndex(Name)`. Without that index the second INSERT simply succeeds
 *      and both callers believe they hold the lock.
 *  K2. Under a barrier fan-out exactly one of N simultaneous acquirers wins, and the losers
 *      come back with a verdict rather than an escaped exception — the constraint violation
 *      has to read as "someone else got there first", not as a crashed scheduler tick.
 */
class ConcurrentSchedulerLockRaceTest extends ConcurrentRaceSetup {

  /** A job name unique to one scenario, so suites sharing this JVM's H2 never collide. */
  private def freshJobName(prefix: String): String = s"$prefix-${UUID.randomUUID()}"

  private def lockRowCount(jobName: String): Long =
    JobScheduler.count(By(JobScheduler.Name, jobName))

  feature("JobScheduler leader lock under concurrent acquisition") {

    scenario("K1: a lock that is already held cannot be taken a second time", ConcurrencyRace) {
      val jobName = freshJobName("LockTakenTwice")

      Given("one instance holds the lock")
      val first = JobScheduler.tryAcquire(jobName, "instance-a", UUID.randomUUID().toString)
      first.isDefined should equal(true)

      When("a second instance tries to take the same lock")
      val second = JobScheduler.tryAcquire(jobName, "instance-b", UUID.randomUUID().toString)

      Then("it is refused, and the table still holds exactly one row for that job")
      withClue(
        "JobScheduler.tryAcquire returned a lock to a second caller while the first still holds it. " +
        "Mutual exclusion here comes from UniqueIndex(Name) on the jobscheduler table; without it " +
        "'find no row, then create' lets every contender insert its own row and every contender " +
        "then runs the job — "
      ) {
        second.isDefined should equal(false)
      }
      lockRowCount(jobName) should equal(1L)

      And("releasing it lets the next caller in")
      JobScheduler.delete_!(first.openOrThrowException("first acquire must have produced a row"))
      val third = JobScheduler.tryAcquire(jobName, "instance-b", UUID.randomUUID().toString)
      third.isDefined should equal(true)
      JobScheduler.delete_!(third.openOrThrowException("third acquire must have produced a row"))
    }

    scenario("K2: exactly one of many simultaneous acquirers wins, and losers get a verdict not an exception", ConcurrencyRace) {
      val jobName = freshJobName("LockFanOut")
      val contenders = 12

      When(s"$contenders instances try to take the same lock at the same instant")
      val results = runConcurrentWithBarrier(contenders) { i =>
        JobScheduler.tryAcquire(jobName, s"instance-$i", UUID.randomUUID().toString)
      }

      Then("no contender saw an escaping exception — losing the race is a return value")
      val escaped = results.collect { case scala.util.Failure(e) => e }
      withClue(s"${escaped.size} of $contenders acquirers threw instead of returning: " +
               s"${escaped.map(_.toString).take(3).mkString(" | ")} — ") {
        escaped shouldBe empty
      }

      And("exactly one of them holds the lock")
      val winners = results.collect { case Success(box) if box.isDefined => box }
      withClue(s"winners=${winners.size} of $contenders, rows=${lockRowCount(jobName)}: more than one " +
               "acquirer holding the same lock means the job runs concurrently on every winner — ") {
        winners.size should equal(1)
      }
      lockRowCount(jobName) should equal(1L)

      winners.foreach(w => JobScheduler.delete_!(w.openOrThrowException("winner must have produced a row")))
    }

  }
}
