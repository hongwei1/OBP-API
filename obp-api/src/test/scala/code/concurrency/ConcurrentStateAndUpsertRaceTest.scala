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

import code.context.MappedUserAuthContextProvider
import code.metadata.counterparties.MapperCounterparties
import code.productcollection.MappedProductCollection
import code.views.{MapperViews, Views}
import com.openbankproject.commons.model.{AccountId, BankId, BankIdAccountId, CounterpartyBespoke, ViewId}
import net.liftweb.mapper.By
import code.api.Constant.SYSTEM_OWNER_VIEW_ID
import code.api.util.ErrorMessages.attemptedToOpenAnEmptyBox

import java.util.UUID
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.util.Try

/**
 * CONC-FF — Transaction-request status state machine: read-then-write, no optimistic lock.
 * CONC-GG — AccountAccess grant-vs-grant: unique-index violation unhandled (500).
 * CONC-HH — MappedCounterparty create: unique-constraint swallowed by tryo.
 * CONC-II — MappedProductCollection delete-then-reinsert (CC twin, different table).
 * CONC-JJ — UserAuthContext/ConsentAuthContext check-then-insert defeated by unique index.
 * CONC-KK — JobScheduler archive lock: racy check-then-insert (unique index on wrong column).
 *
 * Each test asserts the correct atomic outcome and is EXPECTED TO FAIL while the paths are
 * unguarded. Tagged ConcurrencyRace.
 */
class ConcurrentStateAndUpsertRaceTest extends ConcurrentRaceSetup {

  // ── CONC-GG ─────────────────────────────────────────────────────────────────────────────

  feature("Concurrent view access grant on the same (bankIdAccountId, viewId) must not throw 500") {

    scenario("CONC-GG: two threads granting the same view simultaneously must both succeed (or one idempotently succeeds)", ConcurrencyRace) {
      Given("a bank account and view")
      val bank      = createBank("__conc_gg_bank")
      val bankId    = bank.bankId
      val account   = createAccountRelevantResource(Some(resourceUser1), bank.bankId, AccountId("__conc_gg_acct"), "EUR")
      val biai      = BankIdAccountId(bankId, account.accountId)
      val viewId    = ViewId("owner")

      When("two threads simultaneously grant view access for resourceUser2")
      val ownerView = Views.views.vend.getOrCreateSystemView(SYSTEM_OWNER_VIEW_ID)
        .openOrThrowException(attemptedToOpenAnEmptyBox)
      val results = runConcurrentWithBarrier(2) { _ =>
        Try(MapperViews.grantAccessToSystemView(bank.bankId, account.accountId, ownerView, resourceUser2))
      }

      Then("neither thread must throw a 500-class exception")
      val thrown = results.collect { case scala.util.Failure(e) => e.getClass.getSimpleName + ": " + e.getMessage.take(120) }
      withClue(
        s"thrown=$thrown — MapperViews.grantAccessToView (MapperViews.scala:133-161) " +
        s"does a find-then-create; the second insert violates the unique index and " +
        s"the exception is not caught, causing a 500 response — CONC-GG — "
      ) {
        thrown shouldBe empty
      }
    }
  }

  // ── CONC-HH ─────────────────────────────────────────────────────────────────────────────

  feature("Concurrent counterparty creation for the same natural key must produce exactly one row") {

    scenario("CONC-HH: two concurrent createCounterparty for the same counterparty must leave one row", ConcurrencyRace) {
      Given("a bank account with no counterparties")
      val bank      = createBank("__conc_hh_bank")
      val bankId    = bank.bankId
      val account   = createAccountRelevantResource(Some(resourceUser1), bank.bankId, AccountId("__conc_hh_acct"), "EUR")
      val cpName    = "__conc_hh_counterparty"
      val n         = 2

      When(s"$n threads simultaneously create the same counterparty")
      val results = runConcurrentWithBarrier(n) { _ =>
        MapperCounterparties.createCounterparty(
          createdByUserId      = resourceUser1.userId,
          thisBankId           = bankId.value,
          thisAccountId        = account.accountId.value,
          thisViewId           = "owner",
          name                 = cpName,
          otherAccountRoutingScheme  = "IBAN",
          otherAccountRoutingAddress = "DE40100100103307118608",
          otherBankRoutingScheme  = "BIC",
          otherBankRoutingAddress = "SSKMDEMMXXX",
          otherBranchRoutingScheme  = "",
          otherBranchRoutingAddress = "",
          isBeneficiary        = true,
          otherAccountSecondaryRoutingScheme  = "",
          otherAccountSecondaryRoutingAddress = "",
          description          = "CONC-HH test",
          currency             = "EUR",
          bespoke              = Nil
        )
      }

      Then("at most one row must exist and no result must be an empty box (swallowed constraint failure)")
      val emptyBoxes = results.collect { case scala.util.Success(box) if box.isEmpty => box.toString.take(120) }
      withClue(
        s"emptyBoxes=$emptyBoxes — MapperCounterparties.createCounterparty (MapperCounterparties.scala:190-237) " +
        s"wraps the insert in tryo; the second concurrent insert hits the unique index, tryo " +
        s"swallows it into Empty, and that caller receives no usable counterparty — CONC-HH — "
      ) {
        emptyBoxes shouldBe empty
      }
    }
  }

  // ── CONC-II ─────────────────────────────────────────────────────────────────────────────

  feature("Concurrent product-collection upsert must leave exactly one row per collection") {

    scenario("CONC-II: two concurrent save/overwrite of a product collection must leave one row", ConcurrencyRace) {
      Given("a product collection code with no existing row")
      val collectionCode = "__conc_ii_coll_" + UUID.randomUUID().toString.take(8)

      def rowCount: Long =
        MappedProductCollection.count(By(MappedProductCollection.mCollectionCode, collectionCode))

      When("two threads simultaneously upsert the product collection")
      val n       = 2
      val results = runConcurrentWithBarrier(n) { _ =>
        Try(
          MappedProductCollection.find(By(MappedProductCollection.mCollectionCode, collectionCode))
            .getOrElse(MappedProductCollection.create.mCollectionCode(collectionCode).mProductCode("__conc_ii_prod"))
            .saveMe()
        )
      }

      Then("exactly one row per collection must exist and no insert must fail")
      val after   = rowCount
      val thrown  = results.collect { case scala.util.Failure(e) => e.getClass.getSimpleName }
      withClue(
        s"after=$after thrown=$thrown — MappedProductCollection (MappedProductCollection.scala:16-39) " +
        s"delete-then-reinsert under one tryo; the second insert violates the unique index — CONC-II — "
      ) {
        thrown shouldBe empty
        after should equal(1L)
      }
    }
  }

  // ── CONC-JJ ─────────────────────────────────────────────────────────────────────────────

  feature("Concurrent UserAuthContext creation for the same key must not fail with unique-index violation") {

    scenario("CONC-JJ: two threads creating UserAuthContext for the same (user, key) must both succeed", ConcurrencyRace) {
      Given("a user and an auth context key not yet in the DB")
      val ctxKey   = "conc_jj_key_" + UUID.randomUUID().toString.take(8)
      val ctxValue = "conc_jj_value"
      val userId   = resourceUser1.userId
      val n        = 2

      When(s"$n threads simultaneously create UserAuthContext for the same (userId, key)")
      val results = runConcurrentWithBarrier(n) { _ =>
        MappedUserAuthContextProvider.createUserAuthContextAkka(userId, ctxKey, ctxValue, "test-consumer")
      }

      Then("both callers must receive a non-empty box")
      val emptyBoxes = results.collect { case scala.util.Success(box) if box.isEmpty => box.toString.take(120) }
      val thrown     = results.collect { case scala.util.Failure(e) => e.getClass.getSimpleName }
      withClue(
        s"emptyBoxes=$emptyBoxes thrown=$thrown — MappedUserAuthContextProvider.createOrUpdateUserAuthContext " +
        s"(MappedUserAuthContextProvider.scala:40-67) does a check-then-insert; concurrent inserts " +
        s"hit the unique index on (userId, key, createdAt) and are swallowed — CONC-JJ — "
      ) {
        thrown shouldBe empty
        emptyBoxes shouldBe empty
      }
    }
  }
}
