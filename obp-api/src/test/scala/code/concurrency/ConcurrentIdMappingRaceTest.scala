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
package code.concurrency

import code.customer.internalMapping.MappedCustomerIdMappingProvider
import code.model.dataAccess.internalMapping.{AccountIdMapping, MappedAccountIdMappingProvider}
import net.liftweb.mapper.By

import java.util.UUID

/**
 * CONC-DD — AccountIdMapping duplicate mappings (BB twin, different table).
 * CONC-EE — CustomerIdMapping duplicate mappings (BB twin, different table).
 *
 * CONC-DD: MappedAccountIdMappingProvider.getOrCreateAccountId (line 12-41) does a find-then-
 * create with no surrounding transaction and no unique index on mAccountPlainTextReference.
 * Two concurrent calls for the same reference both see Empty, both insert → two mapping rows
 * for one reference → downstream lookups resolve the reference ambiguously.
 *
 * CONC-EE: MappedCustomerIdMappingProvider.getOrCreateCustomerId is structurally identical —
 * same find-then-create pattern, same table without a unique constraint on the natural key.
 * Same race, same ambiguous-lookup consequence.
 *
 * Both tests assert the correct outcome (exactly-one-row per natural key) and are EXPECTED
 * TO FAIL while the unique constraint is absent. Tagged ConcurrencyRace.
 */
class ConcurrentIdMappingRaceTest extends ConcurrentRaceSetup {

  feature("Concurrent getOrCreate on internal id-mapping tables must stay single-valued") {

    scenario("CONC-DD: concurrent getOrCreateAccountId for one reference must create exactly one row", ConcurrencyRace) {
      Given("a plain-text account reference that has no mapping row yet")
      val reference = "__conc_dd_ref_" + UUID.randomUUID().toString

      def rowCount: Long =
        AccountIdMapping.count(By(AccountIdMapping.mAccountPlainTextReference, reference))

      val before = rowCount
      val n      = 8

      When(s"$n threads concurrently getOrCreateAccountId for the same reference")
      val results = runConcurrentWithBarrier(n) { _ =>
        MappedAccountIdMappingProvider.getOrCreateAccountId(reference)
      }

      Then("exactly one mapping row must exist and all callers must agree on the same accountId")
      val after       = rowCount
      val created     = after - before
      val distinctIds = results
        .collect { case scala.util.Success(box) if box.isDefined => box.openOrThrowException("checked").value }
        .distinct
      val failures    = results.collect { case scala.util.Failure(e) => e.getClass.getSimpleName }

      withClue(
        s"before=$before after=$after created=$created distinctIds=${distinctIds.size} failures=$failures " +
        s"(expected: 1 row, 1 distinct id) — MappedAccountIdMappingProvider.getOrCreateAccountId " +
        s"(MappedAccountIdMappingProvider.scala:12-41) is a find-then-create with no transaction " +
        s"guard and no unique index on mAccountPlainTextReference — CONC-DD — "
      ) {
        failures shouldBe empty
        created should equal(1L)
        distinctIds.size should equal(1)
      }
    }

    scenario("CONC-EE: concurrent getOrCreateCustomerId for one reference must create exactly one row", ConcurrencyRace) {
      Given("a plain-text customer reference that has no mapping row yet")
      val reference = "__conc_ee_ref_" + UUID.randomUUID().toString

      import code.customer.internalMapping.MappedCustomerIdMapping
      def rowCount: Long =
        MappedCustomerIdMapping.count(By(MappedCustomerIdMapping.mCustomerPlainTextReference, reference))

      val before = rowCount
      val n      = 8

      When(s"$n threads concurrently getOrCreateCustomerId for the same reference")
      val results = runConcurrentWithBarrier(n) { _ =>
        MappedCustomerIdMappingProvider.getOrCreateCustomerId(reference)
      }

      Then("exactly one mapping row must exist and all callers must agree on the same customerId")
      val after       = rowCount
      val created     = after - before
      val distinctIds = results
        .collect { case scala.util.Success(box) if box.isDefined => box.openOrThrowException("checked").value }
        .distinct
      val failures    = results.collect { case scala.util.Failure(e) => e.getClass.getSimpleName }

      withClue(
        s"before=$before after=$after created=$created distinctIds=${distinctIds.size} failures=$failures " +
        s"(expected: 1 row, 1 distinct id) — MappedCustomerIdMappingProvider.getOrCreateCustomerId " +
        s"(MappedCustomerIdMappingProvider.scala:11-40) has the same find-then-create race as BB/DD — CONC-EE — "
      ) {
        failures shouldBe empty
        created should equal(1L)
        distinctIds.size should equal(1)
      }
    }
  }
}
