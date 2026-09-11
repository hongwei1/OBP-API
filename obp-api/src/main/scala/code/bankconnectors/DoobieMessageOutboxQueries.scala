package code.bankconnectors

import code.api.util.DoobieUtil
import doobie._
import doobie.implicits._
import doobie.implicits.javasql._  // Provides Meta instances for java.sql.Timestamp

import java.sql.Timestamp

object DoobieMessageOutboxQueries {

  /**
   * Claim one outbox row for the caller's relay pass, returning the number of rows claimed
   * (1 = this caller owns the row, 0 = somebody else got it first or the row moved on).
   *
   * `MessageOutboxRelay.relayOnePass` selects the due rows and then publishes them, which
   * without a claim is a read-then-act race: two relays — two OBP replicas, or a redeploy
   * overlapping the outgoing pod — read the same PENDING rows and both publish them. For
   * OPEN_CORRIDOR that means the same credit notification and the same settlement
   * instruction reach the bank's vhost twice.
   *
   * The claim is a guarded UPDATE rather than `SELECT ... FOR UPDATE SKIP LOCKED` because
   * the relay runs on a scheduler thread with no http4s request scope, where
   * `DoobieUtil.runUpdate` falls back to a transactor that commits when the statement
   * returns — a row lock taken that way is released immediately and excludes nobody (the
   * same reasoning is spelled out on `DoobieUtil.hasRequestScopeConnection`). A conditional
   * UPDATE needs no held lock: the database applies it to at most one caller, and the
   * affected-row count is the verdict. It follows the guarded-update pattern already used
   * for consent state transitions in `DoobieConsentSchedulerQueries`.
   *
   * `attempts` doubles as the claim token: the caller passes the value it read, so a second
   * relay working from the same snapshot matches nothing once the first has incremented it.
   * Bumping `attempts` and `updated_at` here also re-arms the relay's exponential backoff,
   * so a row being published right now does not look due to anyone else. A JVM that dies
   * mid-publish simply leaves the row PENDING with one more attempt recorded — the
   * at-least-once redelivery this table exists for.
   *
   * @param rowId        primary key of the row to claim
   * @param seenAttempts the `attempts` value the caller read; the claim only applies if the
   *                     row still carries it
   * @param status       the status the row must still be in (PENDING)
   * @param now          the new `updated_at`, bound as a parameter rather than taken from a
   *                     `NOW()` the shipped drivers do not all spell the same way
   */
  def claimRowForRelay(
    rowId: Long,
    seenAttempts: Int,
    status: String,
    now: Timestamp
  ): Int = DoobieUtil.runUpdate(
    sql"""UPDATE message_outbox
             SET attempts = attempts + 1,
                 updated_at = $now
           WHERE id = $rowId
             AND status = $status
             AND attempts = $seenAttempts""".update.run
  )
}
