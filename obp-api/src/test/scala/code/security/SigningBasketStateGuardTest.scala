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
 * SEC-3 — Signing basket deletable after partial authorisation.
 *
 * Http4sBGv13SigningBaskets.deleteSigningBasket (Http4sBGv13SigningBaskets.scala:115-126)
 * calls SigningBasketX.signingBasketProvider.vend.deleteSigningBasket(basketid) with NO
 * status guard. The ResourceDoc description explicitly states "not deletable after a first
 * (partial) authorisation has been applied", but the DELETE handler does not enforce this
 * constraint — it deletes the basket regardless of its authorisation state.
 *
 * A secure implementation must check the basket's status (or whether any authorisation
 * sub-resources exist) and return 4xx when a first (partial) authorisation has been started.
 *
 * The test asserts the secure outcome (4xx after authorisation is initiated), so it is
 * EXPECTED TO FAIL while the path is unguarded. Tagged SecurityVuln.
 */
class SigningBasketStateGuardTest extends SecurityVulnSetup {

  private val bgBase =
    baseRequest / ConstantsBG.berlinGroupVersion1.urlPrefix / ConstantsBG.berlinGroupVersion1.apiShortVersion

  feature("DELETE signing-basket must be rejected after a first (partial) authorisation has been applied") {

    scenario("SEC-3: DELETE a signing basket that has a started authorisation must return 4xx, not 204", SecurityVuln) {
      Given("a signing basket created via POST")
      val createJson =
        """{
          |  "paymentIds": [
          |    "sec3-payment-aaa",
          |    "sec3-payment-bbb"
          |  ]
          |}""".stripMargin

      val createReq  = (bgBase / "signing-baskets").POST <@ user1
      val createResp = makePostRequest(createReq, createJson)
      createResp.code should equal(201)
      val basketId = createResp.body.extract[SigningBasketResponseJson].basketId

      When("an authorisation sub-resource is started for the basket")
      val authReq  = (bgBase / "signing-baskets" / basketId / "authorisations").POST <@ user1
      val authResp = makePostRequest(authReq, "{}")
      authResp.code should equal(201)
      val scaStatus = authResp.body.extract[StartPaymentAuthorisationJson].scaStatus
      scaStatus should not be empty

      Then("DELETE the basket must return 4xx — a partial authorisation has been applied")
      val deleteReq  = (bgBase / "signing-baskets" / basketId).DELETE <@ user1
      val deleteResp = makeDeleteRequest(deleteReq)
      withClue(
        s"DELETE returned ${deleteResp.code} (expected 4xx) — deleteSigningBasket calls " +
        s"deleteSigningBasket() with no status guard; the spec says 'not deletable after a " +
        s"first (partial) authorisation has been applied' but the handler ignores this constraint " +
        s"(Http4sBGv13SigningBaskets.scala:115-126) — "
      ) {
        deleteResp.code should be >= 400
        deleteResp.code should be < 500
      }
    }
  }
}
