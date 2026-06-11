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
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.{canGetConsentsAtOneBank, canGetConsentsAtAnyBank}
import code.entitlement.Entitlement

/**
 * IDOR-3 — getConsentsAtBank doesn't verify consent belongs to path BANK_ID (cross-tenant).
 * IDOR-4 — getConsentRequest: any consumer reads any consent request by ID.
 *
 * IDOR-3: Http4s510.getConsentsAtBank (line 4303) calls getConsents() which returns ALL
 * consents, then filters in-memory via Consent.filterByBankId. A user with
 * canGetConsentsAtOneBank for bank A can call /management/consents/banks/B (a different
 * bank) — the entitlement check only verifies the role exists, not the bank boundary.
 * This leaks all consents across banks to a single-bank operator.
 *
 * IDOR-4: Http4s500.getConsentRequest (line 904) returns any consent request by ID to any
 * consumer (no ownership check). The ResourceDoc itself acknowledges this: "any registered
 * consumer/application can read any Consent Request by ID." An attacker who enumerates or
 * guesses a consent request ID obtains the full payload including entitlements, account
 * routings and contact details.
 *
 * Each test asserts the secure (restricted) outcome and is EXPECTED TO FAIL. Tagged SecurityVuln.
 */
class ConsentManagementCrossTenantTest extends SecurityVulnSetup {

  private val v5_1_0 = baseRequest / "obp" / "v5.1.0"
  private val v5_0_0 = baseRequest / "obp" / "v5.0.0"
  private val bgBase =
    baseRequest / ConstantsBG.berlinGroupVersion1.urlPrefix / ConstantsBG.berlinGroupVersion1.apiShortVersion

  // ── IDOR-3 ─────────────────────────────────────────────────────────────────────────────

  feature("getConsentsAtBank must only return consents belonging to the path bank") {

    scenario("IDOR-3: operator of bank-A must not see consents from bank-B via /management/consents/banks/B", SecurityVuln) {
      Given("two banks and user1 who has canGetConsentsAtOneBank only for bank-A")
      val bankA   = createBank("__idor3_bank_a")
      val bankB   = createBank("__idor3_bank_b")
      val bankAId = bankA.bankId.value
      val bankBId = bankB.bankId.value

      Entitlement.entitlement.vend.addEntitlement(bankAId, resourceUser1.userId, canGetConsentsAtOneBank.toString)

      And("a BG consent is created for bank-B by user2")
      val consentBody =
        """{
          |  "access": { "accounts": [{ "iban": "DE40100100103307118608" }] },
          |  "recurringIndicator": false,
          |  "validUntil": "9999-12-31",
          |  "frequencyPerDay": 1,
          |  "combinedServiceIndicator": false
          |}""".stripMargin
      makePostRequest((bgBase / "consents").POST <@ user2, consentBody)

      When("user1 calls GET /management/consents/banks/B (cross-tenant)")
      val req  = (v5_1_0 / "management" / "consents" / "banks" / bankBId).GET <@ user1
      val resp = makeGetRequest(req)

      Then("the request must be rejected with 4xx — user1's role is scoped to bank-A only")
      withClue(
        s"GET returned ${resp.code} body=${resp.body} (expected 4xx) — " +
        s"getConsentsAtBank (Http4s510.scala:4303) checks canGetConsentsAtOneBank without " +
        s"verifying the role is granted for the path bank_id — cross-tenant consent leak — "
      ) {
        resp.code should be >= 400
        resp.code should be < 500
      }
    }
  }

  // ── IDOR-4 ─────────────────────────────────────────────────────────────────────────────

  feature("getConsentRequest must only be accessible by the creating consumer") {

    scenario("IDOR-4: any consumer can read any consent request by ID — must require creator identity", SecurityVuln) {
      Given("user1 creates a consent request via the consumer consent-request API")
      val consentRequestBody =
        """{
          |  "everything": false,
          |  "account_access": [
          |    { "account_routing": { "scheme": "IBAN", "address": "DE40100100103307118608" },
          |      "view_id": "owner" }
          |  ],
          |  "phone_number": "+49301234567",
          |  "email":        "idor4@example.com",
          |  "valid_from":   "2026-01-01T00:00:00Z",
          |  "time_to_live": 3600
          |}""".stripMargin

      val createReq  = (v5_0_0 / "consumer" / "consent-requests").POST <@ user1
      val createResp = makePostRequest(createReq, consentRequestBody)
      val consentRequestId = (createResp.body \ "consent_request_id").extractOpt[String].getOrElse("")

      if (consentRequestId.nonEmpty) {
        When("user2 (a different consumer) fetches the consent request by its ID")
        val readReq  = (v5_0_0 / "consumer" / "consent-requests" / consentRequestId).GET <@ user2
        val readResp = makeGetRequest(readReq)

        Then("user2 must receive 4xx — consent request belongs to user1's consumer only")
        withClue(
          s"GET returned ${readResp.code} body=${readResp.body} (expected 4xx) — " +
          s"getConsentRequest (Http4s500.scala:904) returns any consent request by ID to any " +
          s"registered consumer with no ownership check (IDOR-4) — " +
          s"the response includes entitlements, account routings and contact details — "
        ) {
          readResp.code should be >= 400
          readResp.code should be < 500
        }
      }
    }
  }
}
