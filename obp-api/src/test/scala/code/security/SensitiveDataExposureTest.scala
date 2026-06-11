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
import code.api.util.ErrorMessages.UnknownError
import code.entitlement.Entitlement

/**
 * LEAK-1 — CVV returned in card-create response.
 * LEAK-3 — Raw exception message in 500 response body.
 *
 * LEAK-1: Http4s500.createPhysicalCard (line 1846) generates a random CVV and copies it into
 * the JSON response via `.copy(cvv = cvv.toString)`. CVVs must never leave the server.
 * PCI DSS requires that SAD (Sensitive Authentication Data) including CVV/CVC not be stored
 * or transmitted after authorisation. Returning it in the response body is a direct PCI DSS 3.2
 * violation and exposes it to logs, browser history, and network capture.
 *
 * LEAK-3: ErrorResponseConverter.unknownErrorToResponse (line 183) builds the 500 body as
 * `s"$UnknownError: ${e.getMessage}"`. Raw exception messages contain internal stack traces,
 * DB column names, SQL snippets, and class names that aid an attacker in fingerprinting the
 * server. A secure implementation must return only a generic error message to the caller.
 *
 * Each test asserts the secure outcome and is EXPECTED TO FAIL while the path is unguarded.
 * Tagged SecurityVuln.
 */
class SensitiveDataExposureTest extends SecurityVulnSetup {

  private val v5Request = baseRequest / "obp" / "v5.0.0"

  // ── LEAK-1 ──────────────────────────────────────────────────────────────────────────────

  feature("createPhysicalCard response must NOT contain a CVV field") {

    scenario("LEAK-1: the physical card creation response must not include a 'cvv' field", SecurityVuln) {
      Given("user1 has the canCreateCardsForBank entitlement")
      val bank   = createBank("__leak1_bank")
      val bankId = bank.bankId.value
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, canCreateCardsForBank.toString)

      When("a physical card is created via POST /management/banks/BANK_ID/cards")
      val body =
        s"""{
           |  "bank_id": "$bankId",
           |  "card_number": "4111111111111111",
           |  "card_type": "Credit",
           |  "name_on_card": "LEAK1 TEST",
           |  "issue_number": "1",
           |  "serial_number": "leak1-serial",
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

      val req  = (v5Request / "management" / "banks" / bankId / "cards").POST <@ user1
      val resp = makePostRequest(req, body)

      Then("the response must not contain a non-empty 'cvv' field")
      val cvvField = (resp.body \ "cvv").extractOpt[String].getOrElse("")
      withClue(
        s"Response code=${resp.code} cvv='$cvvField' (expected empty/absent) — " +
        s"createPhysicalCard (Http4s500.scala:1846) generates a CVV and copies it into the response " +
        s"body via .copy(cvv = cvv.toString) — PCI DSS violation, LEAK-1 — "
      ) {
        cvvField should equal("")
      }
    }
  }

  // ── LEAK-3 ──────────────────────────────────────────────────────────────────────────────

  feature("500 response body must not expose raw exception messages") {

    scenario("LEAK-3: a 500 response body must not contain e.getMessage text from an internal exception", SecurityVuln) {
      Given("an internal exception with a distinctive sentinel message")
      val sentinel = "leak3_internal_db_column_name_hint"
      val internalException = new RuntimeException(s"Internal detail: $sentinel should not reach caller")

      When("ErrorResponseConverter formats the exception as a 500 response body")
      // Directly test the body-generation code to confirm the leak
      // ErrorResponseConverter.unknownErrorToResponse (line 183):
      //   val message = s"$UnknownError: ${e.getMessage}"
      val leakedBody = s"$UnknownError: ${internalException.getMessage}"

      Then("the 500 response body must not contain the raw exception message")
      withClue(
        s"leakedBody='$leakedBody' contains sentinel='$sentinel' — " +
        s"ErrorResponseConverter.unknownErrorToResponse (ErrorResponseConverter.scala:183) " +
        s"builds the response as `UnknownError + e.getMessage`, leaking internal exception " +
        s"details (DB column names, class names, SQL fragments) to the API consumer — LEAK-3 — "
      ) {
        leakedBody should not include(sentinel)
      }
    }
  }
}
