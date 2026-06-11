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

import code.api.util.{OBPConsumerId, OBPFromDate, OBPToDate}
import code.metrics.{MappedMetric, MappedMetrics}

import java.util.{Calendar, Date}
import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * SEC-1 — SQL injection via the metrics aggregate query.
 *
 * MappedMetrics.sqlFriendly builds raw SQL by interpolating user-supplied strings with
 * no escaping: `s"'$value'"`. OBPConsumerId (and other OBPQueryParam subclasses) flow
 * into the raw SQL at MappedMetrics.scala:427 and executed by DBUtil.runQuery. A
 * classic OR-injection payload such as `' OR '1'='1` widens the WHERE clause to match
 * all rows, bypassing the intended consumer filter.
 *
 * The test asserts the secure outcome (0 rows for a payload that matches nothing by
 * name), so it is EXPECTED TO FAIL while the path is unguarded. Tagged SecurityVuln.
 */
class MetricsSqlInjectionTest extends SecurityVulnSetup {

  feature("Metrics aggregate query must treat consumer_id filter as a literal, not interpret SQL in it") {

    scenario("SEC-1: an OR-injection payload in OBPConsumerId must return 0 matching rows, not all rows", SecurityVuln) {
      Given("two metric rows seeded with a known consumer id")
      val knownConsumerId = "__sec1_real_consumer"
      val now        = new Date()
      val yesterday  = { val c = Calendar.getInstance(); c.add(Calendar.DATE, -1); c.getTime }
      val tomorrow   = { val c = Calendar.getInstance(); c.add(Calendar.DATE,  1); c.getTime }

      val m1 = MappedMetric.create
        .userId("sec1-user-1")
        .url("http://example.com/sec1")
        .date(now)
        .duration(1L)
        .userName("sec1user")
        .appName("sec1app")
        .developerEmail("dev@example.com")
        .consumerId(knownConsumerId)
        .implementedByPartialFunction("fn")
        .implementedInVersion("v7.0.0")
        .verb("GET")
        .httpCode(200)
        .correlationId(java.util.UUID.randomUUID().toString)
        .responseBody("")
        .sourceIp("127.0.0.1")
        .targetIp("127.0.0.1")
        .apiInstanceId("sec1")
        .consentReferenceId("")
        .saveMe()

      val m2 = MappedMetric.create
        .userId("sec1-user-2")
        .url("http://example.com/sec1b")
        .date(now)
        .duration(2L)
        .userName("sec1user2")
        .appName("sec1app")
        .developerEmail("dev2@example.com")
        .consumerId(knownConsumerId)
        .implementedByPartialFunction("fn2")
        .implementedInVersion("v7.0.0")
        .verb("GET")
        .httpCode(200)
        .correlationId(java.util.UUID.randomUUID().toString)
        .responseBody("")
        .sourceIp("127.0.0.1")
        .targetIp("127.0.0.1")
        .apiInstanceId("sec1")
        .consentReferenceId("")
        .saveMe()

      When("getAllAggregateMetricsFuture is called with an OR-injection payload in OBPConsumerId")
      val injectionPayload = "nope' OR '1'='1"
      val params = List(
        OBPFromDate(yesterday),
        OBPToDate(tomorrow),
        OBPConsumerId(injectionPayload)
      )
      val resultBox = Await.result(
        MappedMetrics.getAllAggregateMetricsFuture(params, isNewVersion = true),
        30.seconds
      )

      Then("the payload must be treated as a literal consumer_id string — 0 matching rows")
      val totalCount = resultBox.map(_.headOption.map(_.totalCount).getOrElse(0)).getOrElse(0)
      withClue(
        s"totalCount=$totalCount (expected 0) — sqlFriendly does string interpolation with no escaping; " +
        s"the payload `$injectionPayload` becomes `'nope' OR '1'='1'` in the raw SQL, turning the " +
        s"consumer filter into 1=1 and returning all rows instead of 0 — " +
        "(MappedMetrics.scala:196-202 / :427) — "
      ) {
        totalCount should equal(0)
      }

      m1.delete_!
      m2.delete_!
    }
  }
}
