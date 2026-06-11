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

import code.api.util.ApiRole.{canCreateDynamicEndpoint, canCreateMethodRouting}
import code.api.util.APIUtil.OAuth._
import code.entitlement.Entitlement

/**
 * SSRF-2 — Dynamic-endpoint swagger `servers[].url` SSRF.
 * SSRF-3 — MethodRouting `url` param SSRF.
 *
 * SSRF-2: DynamicEndpointHelper.parseDynamicEndpoint (line 227) extracts the first `servers[].url`
 * from the submitted OpenAPI spec and stores it verbatim. When a dynamic endpoint is later
 * invoked, AkkaHttpClient.makeRequest is called with that serverUrl prefix — no host allowlist
 * check. An authenticated user with canCreateDynamicEndpoint can register a swagger spec whose
 * `servers[0].url` is `http://127.0.0.1` or `http://169.254.169.254`, causing OBP to make
 * server-side requests to those internal hosts on every invocation of the dynamic endpoint.
 *
 * SSRF-3: createMethodRouting (Http4s310.scala:4046-4085) saves the caller-supplied `url`
 * field of a MethodRouting directly to the DB with no host allowlist. RestConnector reads
 * this URL and dispatches outbound HTTP calls to it (RestConnector_vMar2019.scala:7075-7108).
 *
 * Both tests assert the 4xx rejection at creation time and are EXPECTED TO FAIL while no
 * host-allowlist check exists. Tagged SecurityVuln.
 */
class DynamicEndpointSsrfTest extends SecurityVulnSetup {

  private val v4Request = baseRequest / "obp" / "v4.0.0"
  private val v3_1_0    = baseRequest / "obp" / "v3.1.0"

  // ── SSRF-2 ──────────────────────────────────────────────────────────────────────────────

  feature("createDynamicEndpoint must reject OpenAPI specs with internal server URLs") {

    scenario("SSRF-2: swagger spec with servers[0].url=http://127.0.0.1 must be rejected with 4xx", SecurityVuln) {
      Given("user1 has the canCreateDynamicEndpoint entitlement")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canCreateDynamicEndpoint.toString)

      When("a swagger spec with a loopback server URL is submitted")
      val swaggerWithLoopback =
        """{
          |  "openapi": "3.0.3",
          |  "info": { "title": "SSRF-2 Test", "version": "1.0.0" },
          |  "servers": [{ "url": "http://127.0.0.1:8080" }],
          |  "paths": {
          |    "/ssrf2-test": {
          |      "get": {
          |        "operationId": "ssrf2TestGet",
          |        "summary": "SSRF-2 evidence endpoint",
          |        "responses": { "200": { "description": "ok" } }
          |      }
          |    }
          |  }
          |}""".stripMargin

      val req  = (v4Request / "management" / "dynamic-endpoints").POST <@ user1
      val resp = makePostRequest(req, swaggerWithLoopback)

      Then("the spec must be rejected with 4xx — loopback URLs must not be allowed as server base")
      withClue(
        s"POST returned ${resp.code} body=${resp.body} (expected 4xx) — " +
        s"DynamicEndpointHelper.parseDynamicEndpoint extracts servers[0].url verbatim (line 227) " +
        s"and later passes it to AkkaHttpClient.makeRequest (AkkaHttpClient.scala:102-103) — " +
        s"SSRF via internal host 127.0.0.1 — "
      ) {
        resp.code should be >= 400
        resp.code should be < 500
      }
    }

    scenario("SSRF-2b: swagger spec with servers[0].url=http://169.254.169.254 must be rejected", SecurityVuln) {
      Given("user1 has the canCreateDynamicEndpoint entitlement (already granted)")

      When("a swagger spec targeting the cloud instance-metadata endpoint is submitted")
      val swaggerWithMetadata =
        """{
          |  "openapi": "3.0.3",
          |  "info": { "title": "SSRF-2b Test", "version": "1.0.0" },
          |  "servers": [{ "url": "http://169.254.169.254" }],
          |  "paths": {
          |    "/latest/meta-data/": {
          |      "get": {
          |        "operationId": "ssrf2bMetadata",
          |        "summary": "SSRF-2b cloud metadata evidence",
          |        "responses": { "200": { "description": "ok" } }
          |      }
          |    }
          |  }
          |}""".stripMargin

      val req  = (v4Request / "management" / "dynamic-endpoints").POST <@ user1
      val resp = makePostRequest(req, swaggerWithMetadata)

      Then("the spec must be rejected with 4xx — cloud metadata URLs must not be allowed")
      withClue(
        s"POST returned ${resp.code} (expected 4xx) — " +
        s"servers[0].url=http://169.254.169.254 would reach the EC2/GCP/Azure instance-metadata " +
        s"endpoint on every invocation, leaking IAM credentials (SSRF-2) — "
      ) {
        resp.code should be >= 400
        resp.code should be < 500
      }
    }
  }

  // ── SSRF-3 ──────────────────────────────────────────────────────────────────────────────

  feature("createMethodRouting must reject MethodRouting URLs targeting internal hosts") {

    scenario("SSRF-3: MethodRouting with url=http://127.0.0.1 must be rejected with 4xx", SecurityVuln) {
      Given("user1 has the canCreateMethodRouting entitlement")
      Entitlement.entitlement.vend.addEntitlement("", resourceUser1.userId, canCreateMethodRouting.toString)

      When("a MethodRouting with a loopback url is created")
      val body =
        """{
          |  "method_name":    "getBank",
          |  "connector_name": "rest_vMar2019",
          |  "is_bank_id_exact_match": false,
          |  "bank_id_pattern": "*",
          |  "parameters": [
          |    { "key": "url", "value": "http://127.0.0.1:9999/internal" }
          |  ]
          |}""".stripMargin

      val req  = (v3_1_0 / "management" / "method_routings").POST <@ user1
      val resp = makePostRequest(req, body)

      Then("the MethodRouting must be rejected with 4xx — loopback URL must not be persisted")
      withClue(
        s"POST returned ${resp.code} body=${resp.body} (expected 4xx) — " +
        s"createMethodRouting (Http4s310.scala:4046) saves the `url` parameter without host validation; " +
        s"RestConnector_vMar2019 then dispatches calls to this URL " +
        s"(RestConnector_vMar2019.scala:7075-7108) — SSRF-3 — "
      ) {
        resp.code should be >= 400
        resp.code should be < 500
      }
    }
  }
}
