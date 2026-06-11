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
import code.api.util.ApiRole.canCreateAnyTransactionRequest
import code.entitlement.Entitlement
import com.openbankproject.commons.model.AccountId

/**
 * IDOR-1 — getTransactionRequest reads any txReq by ID, ignoring path bank/account.
 *
 * Http4s400.getTransactionRequest (line 6287) calls
 * NewStyle.function.getTransactionRequestImpl(TransactionRequestId(id), ...)
 * with NO cross-check against the BANK_ID / ACCOUNT_ID in the URL path and no
 * CAN_SEE_TRANSACTION_REQUESTS capability check. Any user with a valid view on ANY
 * account can fetch any other user's transaction request by its UUID.
 *
 * The test creates a transaction request under user1's account and then asserts that
 * user2 (who owns a different account) cannot read it — which is the secure outcome.
 * EXPECTED TO FAIL while the path check is absent. Tagged SecurityVuln.
 */
class TransactionRequestIdorTest extends SecurityVulnSetup {

  private val v4Request = baseRequest / "obp" / "v4.0.0"

  feature("getTransactionRequest must only be accessible by the account owner or privileged role") {

    scenario("IDOR-1: user2 reading user1's transaction request by ID must receive 4xx", SecurityVuln) {
      Given("user1 creates a transaction request on their own account")
      // Create a FROM account for user1
      val bank1    = createBank("__idor1_bank")
      val bankId1  = bank1.bankId.value
      val account1 = createAccountRelevantResource(Some(resourceUser1), bank1.bankId, AccountId("__idor1_acct_from"), "EUR")
      val account2 = createAccountRelevantResource(Some(resourceUser1), bank1.bankId, AccountId("__idor1_acct_to"), "EUR")
      val viewId   = "owner"

      Entitlement.entitlement.vend.addEntitlement(bankId1, resourceUser1.userId, canCreateAnyTransactionRequest.toString)

      val txReqBody =
        s"""{
           |  "to": {
           |    "bank_id":    "$bankId1",
           |    "account_id": "${account2.accountId.value}"
           |  },
           |  "value": { "currency": "EUR", "amount": "1.00" },
           |  "description": "idor1 test"
           |}""".stripMargin

      val createReq  = (v4Request / "banks" / bankId1 / "accounts" / account1.accountId.value / viewId / "transaction-request-types" / "ACCOUNT" / "transaction-requests").POST <@ user1
      val createResp = makePostRequest(createReq, txReqBody)
      val txReqId    = (createResp.body \ "id").extractOpt[String].getOrElse("")

      if (txReqId.nonEmpty) {
        When("user2 tries to read user1's transaction request using the same ID but user2's path params")
        val bank2    = createBank("__idor1_bank2")
        val bankId2  = bank2.bankId.value
        val acct2    = createAccountRelevantResource(Some(resourceUser2), bank2.bankId, AccountId("__idor1_acct_user2"), "EUR")

        val readReq  = (v4Request / "banks" / bankId2 / "accounts" / acct2.accountId.value / viewId / "transaction-requests" / txReqId).GET <@ user2
        val readResp = makeGetRequest(readReq)

        Then("user2 must receive 4xx — the txReq does not belong to user2's account")
        withClue(
          s"GET returned ${readResp.code} body=${readResp.body} (expected 4xx) — " +
          s"getTransactionRequest (Http4s400.scala:6287) calls getTransactionRequestImpl(txReqId) " +
          s"without checking that the returned txReq's account matches the path BANK_ID/ACCOUNT_ID — " +
          s"IDOR-1: any user can read any txReq by UUID — "
        ) {
          readResp.code should be >= 400
          readResp.code should be < 500
        }
      }
    }
  }
}
