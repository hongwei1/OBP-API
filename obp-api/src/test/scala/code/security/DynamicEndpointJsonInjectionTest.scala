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

import code.api.dynamic.endpoint.helper.DynamicEndpointHelper
import net.liftweb.json._

/**
 * INJECT-1 — JSON injection via unescaped serverUrl in DynamicEndpointHelper.
 *
 * DynamicEndpointHelper.changeOpenApiVersionHost (lines 86-112) string-interpolates the
 * caller-supplied `newHost` directly into a JSON template:
 *
 *   val newServers = json.parse(s"""{
 *     "servers": [
 *       {"url": "$newHost"}
 *     ]
 *   }""")
 *
 * A payload such as `http://x.com","extra":"injected` breaks out of the `url` string and
 * inserts arbitrary JSON fields into the servers object. A more targeted payload can inject
 * additional `servers` entries that point to attacker-controlled hosts, redirecting API clients.
 *
 * A secure implementation must JSON-escape the newHost value before interpolation, or use
 * a proper JSON library to construct the object (e.g. JObject with JString(newHost)).
 *
 * The test asserts that the parsed result contains ONLY the expected `url` field and no
 * injected keys. EXPECTED TO FAIL while string interpolation is used. Tagged SecurityVuln.
 */
class DynamicEndpointJsonInjectionTest extends SecurityVulnSetup {

  feature("DynamicEndpointHelper.changeOpenApiVersionHost must not allow JSON injection via newHost") {

    scenario("INJECT-1: a newHost with embedded quotes must not inject extra JSON fields", SecurityVuln) {
      Given("a minimal OpenAPI spec and a malicious newHost payload")
      val spec =
        """{
          |  "openapi": "3.0.3",
          |  "info": { "title": "Inject test", "version": "1.0.0" },
          |  "servers": [{ "url": "http://legitimate.example.com" }],
          |  "paths": {}
          |}""".stripMargin

      val maliciousHost = """http://x.com","injected":"yes"""

      When("changeOpenApiVersionHost is called with the malicious newHost")
      val result = scala.util.Try {
        DynamicEndpointHelper.changeOpenApiVersionHost(spec, maliciousHost)
      }

      Then("the result must not contain the injected key 'injected'")
      withClue(
        s"result=$result (expected no 'injected' key) — " +
        s"DynamicEndpointHelper.changeOpenApiVersionHost (DynamicEndpointHelper.scala:95-100) " +
        s"interpolates newHost into a raw JSON template string; a payload containing quote chars breaks " +
        s"out of the url string and injects arbitrary JSON fields — INJECT-1 — "
      ) {
        result match {
          case scala.util.Success(json) =>
            val parsed   = parse(json)
            val servers  = (parsed \ "servers").asInstanceOf[JArray].arr
            val first    = servers.headOption.getOrElse(JNothing)
            val injected = (first \ "injected").extractOpt[String]
            injected should equal(None)
          case scala.util.Failure(e) =>
            // A parse failure of the injected JSON is acceptable — the injection was detected
            succeed
        }
      }
    }

    scenario("INJECT-1b: the url field must contain the exact newHost value without interpretation", SecurityVuln) {
      Given("a spec and a newHost containing a backslash (common JSON escape test)")
      val spec =
        """{
          |  "openapi": "3.0.3",
          |  "info": { "title": "Inject test 1b", "version": "1.0.0" },
          |  "servers": [{ "url": "http://original.example.com" }],
          |  "paths": {}
          |}""".stripMargin

      val hostWithSpecialChars = "http://legit.example.com/path?a=1&b=<2>"

      When("changeOpenApiVersionHost is called with a host containing special chars")
      val result = scala.util.Try {
        DynamicEndpointHelper.changeOpenApiVersionHost(spec, hostWithSpecialChars)
      }

      Then("the url in the result must exactly match the supplied newHost, properly escaped")
      withClue(
        s"result=$result — the url field must survive round-trip as '$hostWithSpecialChars' — INJECT-1b — "
      ) {
        result match {
          case scala.util.Success(json) =>
            val parsed = parse(json)
            val url    = ((parsed \ "servers")(0) \ "url").extractOpt[String].getOrElse("")
            url should equal(hostWithSpecialChars)
          case scala.util.Failure(e) =>
            fail(s"changeOpenApiVersionHost threw for a benign host: ${e.getMessage}")
        }
      }
    }
  }
}
