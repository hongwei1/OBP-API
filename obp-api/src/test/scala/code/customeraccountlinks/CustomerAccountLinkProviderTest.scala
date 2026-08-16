package code.customeraccountlinks

import code.setup.ServerSetup
import net.liftweb.common.Full

class CustomerAccountLinkProviderTest extends ServerSetup {

  val customerId1 = "cal-cust-1"
  val bankId1 = "cal-bank-1"
  val accountId1 = "cal-acc-1"
  val customerId2 = "cal-cust-2"
  val accountId2 = "cal-acc-2"

  private def provider = CustomerAccountLinkX.customerAccountLink.vend

  private def deleteAll(): Unit = provider.bulkDeleteCustomerAccountLinks()

  override def beforeAll() = { super.beforeAll(); deleteAll() }
  override def afterEach() = { super.afterEach(); deleteAll() }

  feature("CustomerAccountLink provider CRUD") {
    scenario("create then get a link by id, customer, account") {
      val created = provider.createCustomerAccountLink(customerId1, bankId1, accountId1, "owner")
      created.isDefined should equal(true)
      val linkId = created.map(_.customerAccountLinkId).openOr("")
      linkId.replace("-", "").size should equal(32)

      provider.getCustomerAccountLinkById(linkId).map(_.relationshipType).openOr("") should equal("owner")
      provider.getCustomerAccountLinkByCustomerId(customerId1).isDefined should equal(true)
      provider.getCustomerAccountLinksByCustomerId(customerId1).openOr(Nil).size should equal(1)
      provider.getCustomerAccountLinksByBankIdAccountId(bankId1, accountId1).openOr(Nil).size should equal(1)
      provider.getCustomerAccountLinksByAccountId(bankId1, accountId1).openOr(Nil).size should equal(1)
    }

    scenario("get-or-create is idempotent on (customer, bank, account)") {
      val first = provider.getOrCreateCustomerAccountLink(customerId1, bankId1, accountId1, "owner")
      val second = provider.getOrCreateCustomerAccountLink(customerId1, bankId1, accountId1, "owner")
      first.map(_.customerAccountLinkId) should equal(second.map(_.customerAccountLinkId))
      provider.getCustomerAccountLinks.openOr(Nil).size should equal(1)
    }

    scenario("update relationship type by id") {
      val created = provider.createCustomerAccountLink(customerId2, bankId1, accountId2, "owner")
      val linkId = created.map(_.customerAccountLinkId).openOr("")

      val updated = provider.updateCustomerAccountLinkById(linkId, "authorized")
      updated.map(_.relationshipType).openOr("") should equal("authorized")
      provider.getCustomerAccountLinkById(linkId).map(_.relationshipType).openOr("") should equal("authorized")
    }

    scenario("delete by id") {
      val created = provider.createCustomerAccountLink(customerId1, bankId1, accountId1, "owner")
      val linkId = created.map(_.customerAccountLinkId).openOr("")

      scala.concurrent.Await.result(provider.deleteCustomerAccountLinkById(linkId), scala.concurrent.duration.Duration("10s")) should equal(Full(true))
      provider.getCustomerAccountLinkById(linkId).isDefined should equal(false)
    }
  }
}
