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

import code.messageoutbox.MessageOutbox
import net.liftweb.mapper.By

import java.util.UUID
import scala.util.Success

/**
 * `MessageOutboxRelay` publishes the outbox rows that carry OBP's asynchronous Interface C
 * messages to a bank's RabbitMQ vhost — credit notifications and settlement instructions.
 * `MessageOutbox.pending()` returns the same rows to every caller, so if two relays run at
 * once (two replicas, or the incoming pod of a rolling update overlapping the outgoing one)
 * both publish the same message: the bank is told twice to credit the same beneficiary.
 *
 * These scenarios pin the claim that prevents it:
 *
 *  N1. Two relays working from the same snapshot: exactly one may claim each row.
 *  N2. A barrier fan-out over one row: exactly one of N simultaneous relays claims it, and
 *      the row's `attempts` advances by exactly one — i.e. the claim is the increment, not a
 *      read-modify-write that several callers can interleave on.
 *  N3. A claim is a lease over a snapshot, not a permanent lock: once the claimer finishes
 *      and the row is genuinely due again, the next pass can claim it (at-least-once
 *      redelivery must survive a relay that died mid-publish).
 */
class ConcurrentMessageOutboxRelayRaceTest extends ConcurrentRaceSetup {

  private def enqueueRow(): MessageOutbox =
    MessageOutbox.enqueue(
      outboxType = MessageOutbox.TYPE_OPEN_CORRIDOR,
      subjectId = s"settlement-${UUID.randomUUID()}",
      subjectIdType = MessageOutbox.SUBJECT_TYPE_SETTLEMENT_ID,
      operationName = "obp_settlement_instruction",
      targetId = s"test-bank-${UUID.randomUUID()}",
      payloadJson = """{"amount":"1.00"}"""
    )

  private def reload(row: MessageOutbox): MessageOutbox =
    MessageOutbox.find(By(MessageOutbox.id, row.id.get))
      .openOrThrowException(s"outbox row ${row.id.get} must still exist")

  feature("Message outbox relay under concurrent passes") {

    scenario("N1: two relays reading the same pending snapshot — only one may publish a row", ConcurrencyRace) {
      Given("a PENDING outbox row that both relays have read")
      val row = enqueueRow()
      val relayA = reload(row)
      val relayB = reload(row)

      When("both try to claim it")
      val claimedByA = MessageOutbox.claimForRelay(relayA)
      val claimedByB = MessageOutbox.claimForRelay(relayB)

      Then("exactly one claim succeeds")
      withClue(
        s"claimedByA=$claimedByA claimedByB=$claimedByB: both relays claimed the same row, so both " +
        "will publish it — for OPEN_CORRIDOR that is the same credit notification / settlement " +
        "instruction delivered to the bank's vhost twice — "
      ) {
        List(claimedByA, claimedByB).count(identity) should equal(1)
      }

      And("the row records exactly one attempt, not two")
      reload(row).attempts should equal(1)
      reload(row).status should equal(MessageOutbox.STATUS_PENDING)
    }

    scenario("N2: exactly one of many simultaneous relays claims a row", ConcurrencyRace) {
      val row = enqueueRow()
      val relays = 12

      When(s"$relays relays read the row and try to claim it at the same instant")
      // Each contender gets its own in-memory copy loaded BEFORE the barrier, which is what a
      // real relay pass holds: MessageOutbox.pending() hands every relay its own snapshot.
      val snapshots = (0 until relays).map(_ => reload(row)).toVector
      val results = runConcurrentWithBarrier(relays) { i => MessageOutbox.claimForRelay(snapshots(i)) }

      Then("no relay saw an escaping exception")
      val escaped = results.collect { case scala.util.Failure(e) => e }
      withClue(s"${escaped.size} of $relays claims threw: ${escaped.map(_.toString).take(3).mkString(" | ")} — ") {
        escaped shouldBe empty
      }

      And("exactly one of them owns the row")
      val winners = results.count { case Success(true) => true; case _ => false }
      withClue(s"winners=$winners of $relays, attempts=${reload(row).attempts}: every winner publishes " +
               "the message, so more than one winner is a duplicate delivery — ") {
        winners should equal(1)
      }

      And("the claim advanced attempts by exactly one — it is the increment, not a read-modify-write")
      reload(row).attempts should equal(1)
    }

    scenario("N3: after the claiming pass ends the row can be claimed again (at-least-once redelivery)", ConcurrencyRace) {
      Given("a row already claimed once by a relay that did not reach a terminal state")
      val row = enqueueRow()
      MessageOutbox.claimForRelay(reload(row)) should equal(true)
      reload(row).attempts should equal(1)

      When("a later pass reads the row afresh and claims it")
      // A stale snapshot (attempts as they were before the first claim) must NOT be claimable —
      // that is what stops a concurrent relay from re-publishing mid-flight.
      val stale = row
      MessageOutbox.claimForRelay(stale) should equal(false)

      val fresh = reload(row)
      val reclaimed = MessageOutbox.claimForRelay(fresh)

      Then("the fresh read succeeds, so a relay that died mid-publish does not park the row forever")
      reclaimed should equal(true)
      reload(row).attempts should equal(2)

      And("a DELIVERED row is never claimed again")
      fresh.Status(MessageOutbox.STATUS_DELIVERED).saveMe()
      MessageOutbox.claimForRelay(reload(row)) should equal(false)
    }
  }
}
