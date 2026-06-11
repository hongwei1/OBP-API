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
import code.api.util.ApiRole.CanCreateWebhook
import code.api.util.ApiTrigger
import code.entitlement.Entitlement
import net.liftweb.json.Serialization.write

/**
 * SEC-4 — Webhook SSRF via unvalidated URL.
 *
 * The createAccountWebhook handler (Http4s310.scala:2483-2506) saves the caller-supplied
 * `url` field directly to the DB with no host allowlist check. WebhookHttpClient then
 * dispatches HTTP requests to that URL when the trigger fires. An authenticated user with
 * canCreateWebhook can register a webhook targeting loopback (127.0.0.1), link-local
 * (169.254.x.x — cloud instance-metadata), or any other internal host, causing the OBP
 * server to make server-side requests on the attacker's behalf.
 *
 * A secure implementation must validate the URL against an allowlist of permitted external
 * hosts (or at minimum block RFC-1918 / link-local / loopback ranges) at creation time and
 * reject the request with 4xx when the target host is internal.
 *
 * The test asserts the secure outcome (4xx rejection for a localhost URL), so it is
 * EXPECTED TO FAIL while the path is unguarded. Tagged SecurityVuln.
 */
class WebhookSsrfTest extends SecurityVulnSetup {

  private val v3_1_0_Request = baseRequest / "obp" / "v3.1.0"

  feature("createAccountWebhook must reject URLs targeting internal/loopback hosts") {

    scenario("SEC-4: creating a webhook with url=http://127.0.0.1:22 must be rejected with 4xx", SecurityVuln) {
      Given("a bank and user1 holding the canCreateWebhook entitlement")
      val bank   = createBank("__sec4_ssrf_bank")
      val bankId = bank.bankId.value
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanCreateWebhook.toString)

      When("a webhook is created with a localhost target URL")
      val ssrfUrl = "http://127.0.0.1:22"
      val body = write(Map(
        "trigger_name"  -> ApiTrigger.onBalanceChange.toString,
        "url"           -> ssrfUrl,
        "http_method"   -> "POST",
        "http_protocol" -> "HTTP/1.1",
        "is_active"     -> "true",
        "account_id"    -> ""
      ))
      val req  = (v3_1_0_Request / "banks" / bankId / "account-web-hooks").POST <@ user1
      val resp = makePostRequest(req, body)

      Then("the request must be rejected with 4xx — localhost URLs must not be allowed")
      withClue(
        s"POST returned ${resp.code} body=${resp.body} (expected 4xx) — " +
        s"createAccountWebhook stores the caller-supplied URL with no host allowlist check; " +
        s"a webhook targeting $ssrfUrl is saved and will cause the server to make a loopback " +
        s"connection when the trigger fires (WebhookHttpClient.scala:129-144) — "
      ) {
        resp.code should be >= 400
        resp.code should be < 500
      }
    }

    scenario("SEC-4b: creating a webhook with url=http://169.254.169.254/latest/meta-data/ must be rejected with 4xx", SecurityVuln) {
      Given("a bank and user1 holding the canCreateWebhook entitlement")
      val bank   = createBank("__sec4b_ssrf_bank")
      val bankId = bank.bankId.value
      Entitlement.entitlement.vend.addEntitlement(bankId, resourceUser1.userId, CanCreateWebhook.toString)

      When("a webhook is created with the cloud instance-metadata URL")
      val metadataUrl = "http://169.254.169.254/latest/meta-data/"
      val body = write(Map(
        "trigger_name"  -> ApiTrigger.onBalanceChange.toString,
        "url"           -> metadataUrl,
        "http_method"   -> "GET",
        "http_protocol" -> "HTTP/1.1",
        "is_active"     -> "true",
        "account_id"    -> ""
      ))
      val req  = (v3_1_0_Request / "banks" / bankId / "account-web-hooks").POST <@ user1
      val resp = makePostRequest(req, body)

      Then("the request must be rejected with 4xx — cloud metadata endpoints must not be allowed")
      withClue(
        s"POST returned ${resp.code} body=${resp.body} (expected 4xx) — " +
        s"the EC2/GCP/Azure instance-metadata endpoint $metadataUrl is reachable from the OBP server; " +
        s"a webhook pointing there leaks cloud credentials to the attacker when triggered — "
      ) {
        resp.code should be >= 400
        resp.code should be < 500
      }
    }
  }
}
