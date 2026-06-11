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

import code.api.berlin.group.ConstantsBG
import code.api.berlin.group.v1_3.JSONFactory_BERLIN_GROUP_1_3.{SigningBasketResponseJson, StartPaymentAuthorisationJson}
import code.api.util.APIUtil.OAuth._

/**
 * BG-1 — Cross-user Berlin Group payment authorise+execute (SCA bypass IDOR).
 * BG-3 — Berlin Group payment cancellation IDOR.
 * BG-4 — Berlin Group consent status readable for any consent ID.
 *
 * BG-1: Http4sBGv13PIS.startPaymentAuthorisationAll (line 396) creates a challenge bound to
 * the CALLER's userId. The challenge-answer step (line 592) then calls
 * createTransactionAfterChallengeV210 which executes the payment using the STORED debtor
 * account — without re-checking that the caller is the account owner. An attacker who knows
 * a victim's paymentId can answer their own challenge and execute the victim's payment.
 *
 * BG-3: Http4sBGv13PIS.cancelPayment (line 130) fetches the payment by ID and immediately
 * cancels it. There is NO check that the authenticated caller is the original payment initiator
 * or owns the debtor account. Any authenticated user can cancel any payment by guessing its ID.
 *
 * BG-4: Http4sBGv13AIS.getConsentStatus (line 354) fetches a consent by ID with no ownership
 * check — any authenticated PSD2 user can read the status of any consent ID.
 *
 * Each test asserts the secure outcome (4xx for a cross-user operation) and is EXPECTED TO FAIL
 * while the paths are unguarded. Tagged SecurityVuln.
 */
class BerlinGroupPaymentAuthIdorTest extends SecurityVulnSetup {

  private val bgBase =
    baseRequest / ConstantsBG.berlinGroupVersion1.urlPrefix / ConstantsBG.berlinGroupVersion1.apiShortVersion

  // ── BG-3 ─────────────────────────────────────────────────────────────────────────────────

  feature("BG payment cancellation must reject requests from a non-owner") {

    scenario("BG-3: user2 cancelling a payment created by user1 must receive 4xx, not 202/204", SecurityVuln) {
      Given("user1 creates a SEPA payment")
      val paymentBody =
        """{
          |  "debtorAccount":    { "iban": "DE40100100103307118608" },
          |  "instructedAmount": { "currency": "EUR", "amount": "10.00" },
          |  "creditorAccount":  { "iban": "DE02100100109307118603" },
          |  "creditorName":     "BG3 Test Creditor"
          |}""".stripMargin
      val createReq  = (bgBase / "payments" / "sepa-credit-transfers").POST <@ user1
      val createResp = makePostRequest(createReq, paymentBody)
      // 201 or 200 — payment was initiated
      createResp.code should (equal(201) or equal(200))
      val paymentId = (createResp.body \ "paymentId").extractOpt[String]
        .getOrElse((createResp.body \ "transactionStatus").extractOpt[String].map(_ => "").getOrElse(""))

      // If payment creation fails for infra reasons, skip rather than false-fail
      if (paymentId.nonEmpty) {
        When("user2 attempts to cancel user1's payment")
        val cancelReq  = (bgBase / "payments" / "sepa-credit-transfers" / paymentId).DELETE <@ user2
        val cancelResp = makeDeleteRequest(cancelReq)

        Then("the cancellation must be rejected with 4xx — caller is not the payment owner")
        withClue(
          s"DELETE returned ${cancelResp.code} (expected 4xx) — cancelPayment (Http4sBGv13PIS.scala:130) " +
          s"fetches the payment by ID and cancels without verifying the caller owns the debtor account — "
        ) {
          cancelResp.code should be >= 400
          cancelResp.code should be < 500
        }
      }
    }
  }

  // ── BG-4 ─────────────────────────────────────────────────────────────────────────────────

  feature("BG consent status must only be readable by the consent owner") {

    scenario("BG-4: user2 reading the consent-status of a consent created by user1 must receive 403", SecurityVuln) {
      Given("user1 creates a BG consent")
      val consentBody =
        """{
          |  "access": {
          |    "accounts":     [{ "iban": "DE40100100103307118608", "currency": "EUR" }],
          |    "balances":     [{ "iban": "DE40100100103307118608", "currency": "EUR" }],
          |    "transactions": [{ "iban": "DE40100100103307118608", "currency": "EUR" }]
          |  },
          |  "recurringIndicator": false,
          |  "validUntil":        "9999-12-31",
          |  "frequencyPerDay":   1,
          |  "combinedServiceIndicator": false
          |}""".stripMargin
      val createReq  = (bgBase / "consents").POST <@ user1
      val createResp = makePostRequest(createReq, consentBody)
      val consentId  = (createResp.body \ "consentId").extractOpt[String].getOrElse("")

      if (createResp.code == 201 && consentId.nonEmpty) {
        When("user2 queries the consent status using its ID")
        val statusReq  = (bgBase / "consents" / consentId / "status").GET <@ user2
        val statusResp = makeGetRequest(statusReq)

        Then("the request must be rejected with 4xx — consent belongs to user1")
        withClue(
          s"GET returned ${statusResp.code} (expected 4xx) — getConsentStatus (Http4sBGv13AIS.scala:354) " +
          s"returns any consent by ID without checking the caller is the consent owner — "
        ) {
          statusResp.code should be >= 400
          statusResp.code should be < 500
        }
      }
    }
  }

  // ── BG-1 ─────────────────────────────────────────────────────────────────────────────────

  feature("BG payment authorisation challenge must be bound to the payment owner") {

    scenario("BG-1: user2 starting an authorisation for user1's payment must receive 4xx", SecurityVuln) {
      Given("user1 initiates a payment")
      val paymentBody =
        """{
          |  "debtorAccount":    { "iban": "DE40100100103307118608" },
          |  "instructedAmount": { "currency": "EUR", "amount": "1.00" },
          |  "creditorAccount":  { "iban": "DE02100100109307118603" },
          |  "creditorName":     "BG1 Test Creditor"
          |}""".stripMargin
      val createReq  = (bgBase / "payments" / "sepa-credit-transfers").POST <@ user1
      val createResp = makePostRequest(createReq, paymentBody)
      val paymentId  = (createResp.body \ "paymentId").extractOpt[String].getOrElse("")

      if (createResp.code == 201 && paymentId.nonEmpty) {
        When("user2 tries to start an authorisation sub-resource for user1's payment")
        val authBody =
          """{
            |  "scaAuthenticationData": "123456"
            |}""".stripMargin
        val authReq  = (bgBase / "payments" / "sepa-credit-transfers" / paymentId / "authorisations").POST <@ user2
        val authResp = makePostRequest(authReq, authBody)

        Then("authorisation must be rejected with 4xx — user2 does not own the payment")
        withClue(
          s"POST returned ${authResp.code} (expected 4xx) — startPaymentAuthorisationAll " +
          s"(Http4sBGv13PIS.scala:396) creates a challenge bound to the caller's userId but does not " +
          s"verify the caller is the payment initiator — IDOR allowing cross-user SCA — "
        ) {
          authResp.code should be >= 400
          authResp.code should be < 500
        }
      }
    }
  }
}
