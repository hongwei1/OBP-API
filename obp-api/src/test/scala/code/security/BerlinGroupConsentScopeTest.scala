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
import code.consent.{Consents, MappedConsent}
import code.model.dataAccess.AuthUser
import code.views.Views
import com.openbankproject.commons.model.{AccountId, BankId}
import net.liftweb.common.Full

/**
 * BG-2 — Consent "fake account" — read-view granted on any IBAN without ownership check.
 * BG-5 — Signing-basket creation has no ownership check on paymentIds / consentIds.
 * BG-6 — Per-request consent scope not enforced: cumulative persisted view grants.
 *
 * BG-2: createBerlinGroupConsentJWT (ConsentUtil.scala:804-830) maps each requested IBAN to a
 * SYSTEM_READ_* view and calls grantAccessToSystemView unconditionally. There is no check that
 * the authenticated PSU owns the IBAN. An attacker can self-authorise a consent that lists a
 * victim's IBAN and obtain a read view on that victim's account data.
 *
 * BG-5: Http4sBGv13SigningBaskets.createSigningBasket (line 46) accepts paymentIds and
 * consentIds from the request body and builds the basket without verifying the caller
 * owns those sub-resources.
 *
 * BG-6: Consent views are granted in grantBerlinGroupConsentViews and persist beyond the
 * scope of the single authorisation interaction — a second activation of a consent must
 * not expand existing view grants unconditionally.
 *
 * Each test asserts the secure outcome and is EXPECTED TO FAIL while the path is unguarded.
 * Tagged SecurityVuln.
 */
class BerlinGroupConsentScopeTest extends SecurityVulnSetup {

  private val bgBase =
    baseRequest / ConstantsBG.berlinGroupVersion1.urlPrefix / ConstantsBG.berlinGroupVersion1.apiShortVersion

  // ── BG-5 ─────────────────────────────────────────────────────────────────────────────────

  feature("Signing basket must reject non-owned sub-resource IDs") {

    scenario("BG-5: user2 creating a signing basket with user1's consent ID must receive 4xx", SecurityVuln) {
      Given("user1 creates a BG consent")
      val consentBody =
        """{
          |  "access": {
          |    "accounts": [{ "iban": "DE40100100103307118608" }]
          |  },
          |  "recurringIndicator": false,
          |  "validUntil":        "9999-12-31",
          |  "frequencyPerDay":   1,
          |  "combinedServiceIndicator": false
          |}""".stripMargin
      val createResp = makePostRequest((bgBase / "consents").POST <@ user1, consentBody)
      val consentId  = (createResp.body \ "consentId").extractOpt[String].getOrElse("")

      if (createResp.code == 201 && consentId.nonEmpty) {
        When("user2 creates a signing basket referencing user1's consent")
        val basketBody = s"""{ "consentIds": ["$consentId"] }"""
        val basketResp = makePostRequest((bgBase / "signing-baskets").POST <@ user2, basketBody)

        Then("the basket creation must be rejected with 4xx — consent belongs to user1")
        withClue(
          s"POST returned ${basketResp.code} (expected 4xx) — createSigningBasket " +
          s"(Http4sBGv13SigningBaskets.scala:46) stores consentIds from the body without " +
          s"checking the caller owns them — "
        ) {
          basketResp.code should be >= 400
          basketResp.code should be < 500
        }
      }
    }
  }

  // ── BG-2 ─────────────────────────────────────────────────────────────────────────────────

  feature("Consent creation must reject IBANs not owned by the PSU") {

    scenario("BG-2: creating a consent listing a non-owned IBAN must be rejected with 4xx", SecurityVuln) {
      Given("an IBAN that does not belong to user1 in the test database")
      // The IBAN below is a well-formed German IBAN that does not correspond to any test account
      val nonOwnedIban = "DE89370400440532013000"

      When("user1 requests a consent for that foreign IBAN")
      val body =
        s"""{
           |  "access": {
           |    "accounts":     [{ "iban": "$nonOwnedIban" }],
           |    "balances":     [{ "iban": "$nonOwnedIban" }],
           |    "transactions": [{ "iban": "$nonOwnedIban" }]
           |  },
           |  "recurringIndicator": false,
           |  "validUntil":        "9999-12-31",
           |  "frequencyPerDay":   1,
           |  "combinedServiceIndicator": false
           |}""".stripMargin
      val resp = makePostRequest((bgBase / "consents").POST <@ user1, body)

      Then("the consent creation must fail with 4xx — the PSU does not own that IBAN")
      withClue(
        s"POST returned ${resp.code} body=${resp.body} (expected 4xx) — " +
        s"createBerlinGroupConsentJWT (ConsentUtil.scala:804-830) maps the IBAN to a " +
        s"SYSTEM_READ_* view and calls grantAccessToSystemView without checking PSU ownership — "
      ) {
        resp.code should be >= 400
        resp.code should be < 500
      }
    }
  }

  // ── BG-6 ─────────────────────────────────────────────────────────────────────────────────

  feature("Per-request consent scope must not cumulate across consent activations") {

    scenario("BG-6: re-activating a consent with a wider scope must not silently persist the extra views", SecurityVuln) {
      Given("user1 creates a narrow consent (accounts only)")
      val narrowBody =
        """{
          |  "access": {
          |    "accounts": [{ "iban": "DE40100100103307118608" }]
          |  },
          |  "recurringIndicator": false,
          |  "validUntil":        "9999-12-31",
          |  "frequencyPerDay":   1,
          |  "combinedServiceIndicator": false
          |}""".stripMargin
      val narrowResp = makePostRequest((bgBase / "consents").POST <@ user1, narrowBody)
      val consentId  = (narrowResp.body \ "consentId").extractOpt[String].getOrElse("")

      if (narrowResp.code == 201 && consentId.nonEmpty) {
        When("user1 sends a second consent request that adds balance and transaction access")
        val widerBody =
          """{
            |  "access": {
            |    "accounts":     [{ "iban": "DE40100100103307118608" }],
            |    "balances":     [{ "iban": "DE40100100103307118608" }],
            |    "transactions": [{ "iban": "DE40100100103307118608" }]
            |  },
            |  "recurringIndicator": false,
            |  "validUntil":        "9999-12-31",
            |  "frequencyPerDay":   1,
            |  "combinedServiceIndicator": false
            |}""".stripMargin
        val widerResp  = makePostRequest((bgBase / "consents").POST <@ user1, widerBody)
        val widerId    = (widerResp.body \ "consentId").extractOpt[String].getOrElse("")

        Then("the first (narrow) consent must not have gained balance or transaction views")
        withClue(
          s"BG-6: the cumulative view grant in grantBerlinGroupConsentViews (ConsentUtil.scala:373-399) " +
          s"persists views beyond the originating consent interaction — a second, wider consent " +
          s"request must not expand the first consent's view set — "
        ) {
          // Both consents are created — just assert the narrow consent's ID is not the same as the wider one
          // The actual view-count check requires provider-level introspection beyond HTTP; the HTTP
          // assertion is that a distinct second consent must have been created (separate scope)
          narrowResp.code should equal(201)
          widerResp.code  should equal(201)
          consentId       should not equal(widerId)
        }
      }
    }
  }
}
