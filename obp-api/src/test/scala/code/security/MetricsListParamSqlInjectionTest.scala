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

import code.metrics.{MappedMetric, MappedMetrics}
import code.api.util.OBPFromDate
import code.api.util.OBPToDate

import java.util.{Calendar, Date, UUID}

/**
 * SQLi-2 — Metrics list-param SQL injection via extendLikeQuery.
 *
 * MappedMetrics.extendLikeQuery (MappedMetrics.scala:335-354) builds raw SQL by directly
 * interpolating user-supplied strings: `s"'${params.head}'"`. The filter params
 * exclude_app_names, exclude_url_patterns, and exclude_implemented_by_partial_functions
 * flow from HTTP query params → OBPQueryParam list → extendLikeQuery → raw SQL.
 *
 * A classic OR-injection payload such as `app' OR '1'='1` widens the WHERE clause to
 * match all rows, bypassing the intended exclusion filter and potentially leaking all
 * metric records. This is a distinct sink from SEC-1 (aggregate query via OBPConsumerId).
 *
 * The test asserts the secure outcome (OR-injection payload treated as a literal, returning
 * only rows whose app_name exactly matches the payload string — which is 0) and is
 * EXPECTED TO FAIL while extendLikeQuery does not escape. Tagged SecurityVuln.
 */
class MetricsListParamSqlInjectionTest extends SecurityVulnSetup {

  feature("Metrics exclude params must be treated as literals, not raw SQL") {

    scenario("SQLi-2a: OR-injection in exclude_app_names must not widen the exclusion set", SecurityVuln) {
      Given("two metric rows with a known app_name")
      val knownApp = "__sqli2_app_" + UUID.randomUUID().toString.take(8)
      val now      = new Date()
      val past     = { val c = Calendar.getInstance(); c.add(Calendar.DATE, -1); c.getTime }
      val future   = { val c = Calendar.getInstance(); c.add(Calendar.DATE,  1); c.getTime }

      def seed(suffix: String) = MappedMetric.create
        .userId("sqli2-user")
        .url("http://example.com/sqli2")
        .date(now)
        .duration(1L)
        .userName("sqli2user")
        .appName(knownApp)
        .developerEmail("dev@example.com")
        .consumerId("sqli2consumer")
        .implementedByPartialFunction("getSqli2")
        .implementedInVersion("v5.1.0")
        .verb("GET")
        .httpCode(200)
        .correlationId(UUID.randomUUID().toString)
        .responseBody("")
        .sourceIp("127.0.0.1")
        .targetIp("127.0.0.1")
        .apiInstanceId("sqli2")
        .consentReferenceId("")
        .saveMe()

      val m1 = seed("a")
      val m2 = seed("b")

      When("getAllMetrics is called with an OR-injection payload in the exclude_app_names OBPQueryParam")
      // OBPExcludeAppNames is the query param key; inject via a custom OBPQueryParam subtype
      // We call getAllMetrics directly with a raw param list that exercises extendLikeQuery
      val injectionPayload = s"$knownApp' OR '1'='1"
      import code.api.util.OBPExcludeAppNames
      val params = List(
        OBPFromDate(past),
        OBPToDate(future),
        OBPExcludeAppNames(List(injectionPayload))
      )
      val resultMetrics = MappedMetrics.getAllMetrics(params)

      Then("the payload must not widen the exclusion — rows with app_name = knownApp must still be returned")
      val knownRows = resultMetrics.count(_.getAppName() == knownApp)
      withClue(
        s"knownRows=$knownRows (expected 2) — extendLikeQuery concatenates list params as raw SQL; " +
        s"the payload `$injectionPayload` produces `app_name NOT LIKE ('$knownApp' OR '1'='1')` " +
        s"which excludes ALL rows (OR '1'='1' always true) instead of just the injected string, " +
        s"so knownRows becomes 0 instead of 2 (MappedMetrics.scala:335-354) — "
      ) {
        // If injection works, the exclusion filter removes all rows → knownRows = 0 (FAIL proves vuln)
        // Secure implementation treats payload as literal → LIKE '...' matches nothing → knownRows = 2
        knownRows should equal(2)
      }

      m1.delete_!
      m2.delete_!
    }
  }
}
