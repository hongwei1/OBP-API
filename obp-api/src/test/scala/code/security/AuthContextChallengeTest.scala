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
package code.security

import code.api.util.APIUtil.OAuth._
import code.api.util.ErrorMessages
import code.context.{MappedUserAuthContextUpdate, MappedUserAuthContextUpdateProvider}
import net.liftweb.json.Serialization.write

import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * SEC-5 — IDOR on auth-context-update challenge answer.
 *
 * Two vulnerabilities in the auth-context-update challenge flow:
 *
 *  A) MISSING OWNERSHIP CHECK (IDOR):
 *     answerUserAuthContextUpdateChallenge (Http4s500.scala:779-807) resolves the
 *     AUTH_CONTEXT_UPDATE_ID from the URL and calls NewStyle.function.checkAnswer without
 *     verifying that the authenticated user is the owner of that update record.
 *     Any authenticated user can answer any other user's challenge by guessing / knowing
 *     the update ID (a UUID — not secret, and leaked in the create response).
 *
 *  B) WEAK CHALLENGE (8-digit plain comparison):
 *     MappedUserAuthContextUpdate.mChallenge generates a value from
 *     csprng.nextInt(99999999), which is at most 8 decimal digits. The comparison in
 *     MappedUserAuthContextUpdateProvider.checkAnswer is a plain String == with no
 *     hash and no per-attempt rate-limit beyond the single-request TTL check. An
 *     attacker who can submit concurrent requests (bypassing the TTL check per hazard K)
 *     has a search space of ≤ 100 000 000 values.
 *
 * The test asserts the secure outcome for the IDOR:
 *   user2 answering user1's challenge must be rejected with 403 / not-found.
 * Currently the endpoint allows it (200 / ACCEPTED) — red bar confirms the vulnerability.
 * Tagged SecurityVuln.
 */
class AuthContextChallengeTest extends SecurityVulnSetup {

  private val v5_0_0_Request = baseRequest / "obp" / "v5.0.0"

  feature("answerUserAuthContextUpdateChallenge must reject answers from non-owners") {

    scenario("SEC-5: user2 answering user1's auth-context-update must be rejected with 403 or not-found", SecurityVuln) {
      Given("user1 has an auth-context-update record with a known challenge")
      val bank   = createBank("__sec5_authctx_bank")
      val bankId = bank.bankId.value

      val updateFuture = MappedUserAuthContextUpdateProvider.createUserAuthContextUpdates(
        userId     = resourceUser1.userId,
        consumerId = "sec5-consumer",
        key        = "PHONE_NUMBER",
        value      = "+49123456789"
      )
      val updateBox = Await.result(updateFuture, 30.seconds)
      val update    = updateBox.openOrThrowException("createUserAuthContextUpdates failed in test setup")
      val updateId  = update.userAuthContextUpdateId
      val challenge = update.challenge

      When("user2 (a different authenticated user) submits the correct challenge answer for user1's update")
      val answerBody = write(Map("answer" -> challenge))
      val req = (v5_0_0_Request / "banks" / bankId / "users" / "current" / "auth-context-updates" / updateId / "challenge")
        .POST <@ user2
      val resp = makePostRequest(req, answerBody)

      Then("the response must be 403 or 404 — only the owning user may answer their own challenge")
      withClue(
        s"user2 answered user1's challenge (updateId=$updateId) and got HTTP ${resp.code} " +
        s"(expected 403 or 404) — answerUserAuthContextUpdateChallenge calls checkAnswer(updateId, ...) " +
        s"without verifying cc.user.userId == update.userId; any authenticated user can resolve any " +
        s"other user's update by ID (Http4s500.scala:779-807) — "
      ) {
        resp.code should (equal(403) or equal(404))
      }
    }
  }
}
