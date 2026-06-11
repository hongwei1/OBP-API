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
import code.api.util.ApiRole.canCreateCardsForBank
import code.entitlement.Entitlement

/**
 * IDOR-2 — Card-attribute create/update endpoints have NO authorization check.
 *
 * Http4s310.createCardAttribute (line 3685) uses executeFutureWithBodyCreated which does not
 * require an authenticated user. There is no hasEntitlement or withUser call — any caller
 * (even unauthenticated) can POST to /management/banks/BANK_ID/cards/CARD_ID/attribute.
 * The updateCardAttribute endpoint (line 3739) has the same pattern.
 *
 * A secure implementation must require at minimum the canCreateCardAttributeAtOneBank role
 * (or equivalent) and that the caller is at the same bank as the card.
 *
 * The test asserts the secure outcome (4xx for an unauthenticated card-attribute create)
 * and is EXPECTED TO FAIL while no auth check exists. Tagged SecurityVuln.
 */
class CardAttributeAuthzTest extends SecurityVulnSetup {

  private val v3_1_0 = baseRequest / "obp" / "v3.1.0"

  feature("createCardAttribute must require an authenticated caller with appropriate role") {

    scenario("IDOR-2: unauthenticated card-attribute creation must return 401", SecurityVuln) {
      Given("a bank and a physical card seeded directly via provider")
      val bank   = createBank("__idor2_bank")
      val bankId = bank.bankId.value

      // Seed a physical card using the provider so we have a real card ID
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, canCreateCardsForBank.toString)
      val cardBody =
        s"""{
           |  "bank_id": "$bankId",
           |  "card_number": "4111111111111111",
           |  "card_type": "Credit",
           |  "name_on_card": "IDOR2 TEST",
           |  "issue_number": "1",
           |  "serial_number": "idor2-serial",
           |  "valid_from_date": "2020-01-01T00:00:00Z",
           |  "expires_date":    "2030-12-31T00:00:00Z",
           |  "enabled":         true,
           |  "cancellation_reason": "",
           |  "cvv": "",
           |  "card_networks": [],
           |  "allows": [],
           |  "replacement": { "requested_date": "2020-01-01T00:00:00Z", "reason_requested": "" },
           |  "pin_reset": [],
           |  "collected": { "date": "2020-01-01T00:00:00Z", "agent": "" },
           |  "posted": { "date": "2020-01-01T00:00:00Z", "agent": "" },
           |  "customer_id": ""
           |}""".stripMargin

      val v5Request   = baseRequest / "obp" / "v5.0.0"
      val cardResp    = makePostRequest((v5Request / "management" / "banks" / bankId / "cards").POST <@ user1, cardBody)
      val cardId      = (cardResp.body \ "card_id").extractOpt[String].getOrElse("fake-card-id")

      When("an unauthenticated request attempts to create a card attribute")
      val attrBody =
        """{
          |  "name":  "IDOR2_ATTR",
          |  "type":  "STRING",
          |  "value": "injected-value"
          |}""".stripMargin

      val unauthReq  = (v3_1_0 / "management" / "banks" / bankId / "cards" / cardId / "attribute").POST
      val unauthResp = makePostRequest(unauthReq, attrBody)

      Then("the request must return 401 — no authentication provided")
      withClue(
        s"POST returned ${unauthResp.code} body=${unauthResp.body} (expected 401) — " +
        s"createCardAttribute (Http4s310.scala:3685) uses executeFutureWithBodyCreated without " +
        s"a user check — no authentication is required, any caller can write card metadata — "
      ) {
        unauthResp.code should equal(401)
      }
    }

    scenario("IDOR-2b: card-attribute creation without the required role must return 403", SecurityVuln) {
      Given("a bank and card (reuse or create a new one), user2 has no card management role")
      val bank   = createBank("__idor2b_bank")
      val bankId = bank.bankId.value
      val cardId = "idor2b-card-fake-id"

      When("user2 (no role) attempts to create a card attribute")
      val attrBody =
        """{
          |  "name":  "IDOR2B_ATTR",
          |  "type":  "STRING",
          |  "value": "injected-value"
          |}""".stripMargin

      val req  = (v3_1_0 / "management" / "banks" / bankId / "cards" / cardId / "attribute").POST <@ user2
      val resp = makePostRequest(req, attrBody)

      Then("the request must return 403 — user2 lacks the card attribute role")
      withClue(
        s"POST returned ${resp.code} body=${resp.body} (expected 403) — " +
        s"createCardAttribute has no role check; any authenticated user can write card metadata — "
      ) {
        resp.code should equal(403)
      }
    }
  }
}
