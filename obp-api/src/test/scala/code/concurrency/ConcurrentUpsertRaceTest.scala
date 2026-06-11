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

import code.productcollectionitem.{MappedProductCollectionItem, MappedProductCollectionItemProvider}
import code.transaction.internalMapping.{MappedTransactionIdMappingProvider, TransactionIdMapping}
import net.liftweb.mapper.By

import java.util.UUID
import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * Two more get-or-create races outside the already-covered set, both proven by source audit and
 * sibling to existing scenarios:
 *
 *  BB. TransactionIdMapping duplicate (check-then-insert, sibling of D) —
 *      MappedTransactionIdMappingProvider.getOrCreateTransactionId does find-then-create with no
 *      surrounding transaction and NO tryo. The only unique indexes are UniqueIndex(TransactionId)
 *      and UniqueIndex(TransactionId, TransactionPlainTextReference); there is NO unique index on
 *      TransactionPlainTextReference alone. Each create mints a fresh random TransactionId, so two
 *      concurrent calls for the same external reference both miss the find and both insert
 *      successfully (distinct ids, no constraint fires) → two mapping rows for one reference, and
 *      a later lookup resolves the reference ambiguously.
 *
 *  CC. ProductCollectionItem delete-then-insert (unique-constraint swallowed by tryo, sibling of W) —
 *      MappedProductCollectionItemProvider.getOrCreateProductCollectionItem deletes the collection's
 *      items then re-inserts one row per member, with .saveMe and NO per-insert guard. The whole body
 *      is wrapped in tryo, and MappedProductCollectionItem carries UniqueIndex(mCollectionCode,
 *      mMemberProductCode). Two concurrent resets for the same collection both read the pre-state,
 *      both try to insert the same (collection, member) tuple → the second insert violates the unique
 *      index, the tryo swallows it into an Empty/Failure box, and that caller gets no usable list —
 *      the get-or-create contract is defeated just as in W.
 *
 * Both assert the correct (exactly-one-mapping / every-caller-usable) outcome, so they are EXPECTED
 * TO FAIL while the paths are unguarded. Tagged ConcurrencyRace.
 */
class ConcurrentUpsertRaceTest extends ConcurrentRaceSetup {

  feature("Concurrent get-or-create on internal mapping tables must stay single-valued and graceful") {

    scenario("BB: concurrent getOrCreateTransactionId for one reference must create exactly one mapping", ConcurrencyRace) {
      Given("an external transaction reference that has no mapping row yet")
      val reference = "__conc_bb_ref_" + UUID.randomUUID.toString

      def mappingCount: Long =
        TransactionIdMapping.count(By(TransactionIdMapping.TransactionPlainTextReference, reference))

      val before = mappingCount
      val n      = 8

      When(s"$n threads concurrently getOrCreateTransactionId for the same reference")
      val results = runConcurrentWithBarrier(n) { _ =>
        MappedTransactionIdMappingProvider.getOrCreateTransactionId(reference)
      }

      Then("exactly one mapping row must exist and all callers must agree on the same transactionId")
      val after          = mappingCount
      val created        = after - before
      val distinctIds    = results.collect { case scala.util.Success(box) if box.isDefined => box.openOrThrowException("checked").value }.distinct
      val failures       = results.collect { case scala.util.Failure(e) => e.getClass.getSimpleName + ": " + e.getMessage }
      withClue(
        s"before=$before after=$after created=$created distinctIds=${distinctIds.size} failures=$failures " +
        s"(expected: 1 row, 1 distinct id) — getOrCreateTransactionId find-then-create is unguarded and " +
        s"TransactionPlainTextReference has no unique index, so concurrent callers mint duplicate mappings — "
      ) {
        failures shouldBe empty
        created should equal(1L)
        distinctIds.size should equal(1)
      }
    }

    scenario("CC: concurrent getOrCreateProductCollectionItem for one collection must leave one row per member and serve every caller", ConcurrencyRace) {
      Given("a product collection code with a fixed member set and no items yet")
      val collectionCode = "__conc_cc_coll_" + UUID.randomUUID.toString.take(8)
      val memberCodes    = List("__conc_cc_prod_a", "__conc_cc_prod_b", "__conc_cc_prod_c")

      def itemCount: Long =
        MappedProductCollectionItem.count(By(MappedProductCollectionItem.mCollectionCode, collectionCode))

      val before = itemCount
      val n      = 2

      When(s"$n threads concurrently getOrCreateProductCollectionItem for the same collection")
      val results = runConcurrentWithBarrier(n) { _ =>
        Await.result(
          MappedProductCollectionItemProvider.getOrCreateProductCollectionItem(collectionCode, memberCodes),
          30.seconds
        )
      }

      Then("every caller must receive a usable list and exactly one row per member must remain")
      val after      = itemCount
      val thrown     = results.collect { case scala.util.Failure(e) => e.getClass.getSimpleName + ": " + e.getMessage.take(120) }
      val emptyBoxes = results.collect { case scala.util.Success(box) if box.isEmpty => box.toString.take(120) }
      withClue(
        s"before=$before after=$after thrown=$thrown emptyBoxes=$emptyBoxes " +
        s"(expected: no throws, no empty boxes, ${memberCodes.size} rows) — getOrCreateProductCollectionItem " +
        s"deletes-then-reinserts under a single tryo; the second concurrent insert hits " +
        s"UniqueIndex(mCollectionCode,mMemberProductCode) and the tryo swallows it into a Failure box, " +
        s"leaving that caller with no usable list — "
      ) {
        thrown shouldBe empty
        emptyBoxes shouldBe empty
        after should equal(memberCodes.size.toLong)
      }
    }
  }
}
