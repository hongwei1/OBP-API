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

import code.transactionChallenge.MappedChallengeProvider
import code.api.util.APIUtil
import com.openbankproject.commons.model.enums.ChallengeType
import com.openbankproject.commons.model.enums.StrongCustomerAuthenticationStatus
import net.liftweb.common.{Box, Full}

import java.util.UUID

/**
 * RACE-1 — Challenge attempt counter race: brute-force limit bypassable via parallel requests.
 *
 * MappedChallengeProvider.validateChallengeAnswerInDB (line 76-91) increments the attempt
 * counter (line 78) BEFORE checking if the limit is exceeded (line 84). With no
 * database-level lock, two concurrent requests can both read `currentAttemptCounterValue < 3`
 * before either write completes, allowing more than the configured number of guesses through.
 *
 * Example: allowedAnswerTransactionRequestChallengeAttempts = 3.
 * Thread 1: reads counter=0, writes counter=1, check 0<3 passes, proceeds with guess.
 * Thread 2: reads counter=0 (not yet flushed), writes counter=1, check 0<3 passes, proceeds.
 * Both get an extra attempt past the enforcement window.
 *
 * A secure implementation must use a DB-level atomic increment+check (e.g. a UPDATE ... WHERE
 * attempt_counter < limit followed by checking rows-affected), or wrap in a DB transaction
 * with SELECT FOR UPDATE.
 *
 * The test asserts that after exactly `limit` incorrect attempts, all subsequent attempts are
 * rejected, even when submitted concurrently. EXPECTED TO FAIL while unguarded. Tagged ConcurrencyRace.
 */
class ConcurrentChallengeAttemptTest extends ConcurrentRaceSetup {

  feature("Challenge attempt counter must be enforced atomically — no bypass via concurrent requests") {

    scenario("RACE-1: submitting limit+2 wrong answers concurrently must result in exactly limit attempts accepted", ConcurrencyRace) {
      Given("a challenge with a known wrong answer and the configured attempt limit")
      val limit      = APIUtil.allowedAnswerTransactionRequestChallengeAttempts
      val wrongAnswer = "obviously-wrong-" + UUID.randomUUID().toString

      // Seed a challenge directly via provider
      val challengeId = UUID.randomUUID().toString
      val salt        = org.mindrot.jbcrypt.BCrypt.gensalt()
      val expected    = org.mindrot.jbcrypt.BCrypt.hashpw("correct-answer", salt).substring(0, 44)

      MappedChallengeProvider.saveChallenge(
        challengeId             = challengeId,
        transactionRequestId    = UUID.randomUUID().toString,
        salt                    = salt,
        expectedAnswer          = expected,
        expectedUserId          = resourceUser1.userId,
        scaMethod               = None,
        scaStatus               = Some(StrongCustomerAuthenticationStatus.received),
        consentId               = None,
        basketId                = None,
        authenticationMethodId  = None,
        challengeType           = ChallengeType.OBP_TRANSACTION_REQUEST_CHALLENGE.toString
      )

      When(s"${limit + 2} wrong-answer submissions are sent concurrently")
      val n       = limit + 2
      val results = runConcurrentWithBarrier(n) { _ =>
        MappedChallengeProvider.validateChallenge(challengeId, wrongAnswer, Some(resourceUser1.userId))
      }

      Then(s"exactly $limit submissions must be in-window (challenge.open), the rest must be rejected (counter >= limit)")
      val inWindow  = results.collect { case scala.util.Success(box) if box.isDefined => box.openOrThrowException("checked") }
        .count(c => !c.successful)
      val limitMsg  = results.collect { case scala.util.Success(Full(c)) if c.attemptCounter >= limit => c.challengeId }
      withClue(
        s"inWindowCount=$inWindow limitBreachCount=${limitMsg.size} — " +
        s"MappedChallengeProvider.validateChallengeAnswerInDB (MappedChallengeProvider.scala:76-91) " +
        s"reads-then-writes the attempt counter with no transaction lock; concurrent submissions " +
        s"can both read counter < limit before either write flushes, bypassing the brute-force guard — RACE-1 — "
      ) {
        inWindow should be <= limit
      }
    }
  }
}
