package code.customerlinks

import code.setup.ServerSetup
import net.liftweb.common.Full

class CustomerLinkProviderTest extends ServerSetup {

  val bankId1 = "cl-bank-1"
  val customerId1 = "cl-cust-1"
  val otherBankId1 = "cl-bank-2"
  val otherCustomerId1 = "cl-cust-2"

  private def provider = CustomerLinkX.customerLink.vend

  private def deleteAll(): Unit = provider.bulkDeleteCustomerLinks()

  override def beforeAll() = { super.beforeAll(); deleteAll() }
  override def afterEach() = { super.afterEach(); deleteAll() }

  feature("CustomerLink provider CRUD") {
    scenario("create then get by id, bank, customer") {
      val created = provider.createCustomerLink(bankId1, customerId1, otherBankId1, otherCustomerId1, "parent")
      created.isDefined should equal(true)
      val linkId = created.map(_.customerLinkId).openOr("")
      linkId.replace("-", "").size should equal(32)
      created.map(_.dateInserted).openOr(null) should not be null

      provider.getCustomerLinkById(linkId).map(_.relationshipTo).openOr("") should equal("parent")
      provider.getCustomerLinksByBankId(bankId1).openOr(Nil).size should equal(1)
      provider.getCustomerLinksByCustomerId(customerId1).openOr(Nil).size should equal(1)
    }

    scenario("update relationship by id") {
      val created = provider.createCustomerLink(bankId1, customerId1, otherBankId1, otherCustomerId1, "parent")
      val linkId = created.map(_.customerLinkId).openOr("")

      provider.updateCustomerLinkById(linkId, "sibling").map(_.relationshipTo).openOr("") should equal("sibling")
      provider.getCustomerLinkById(linkId).map(_.relationshipTo).openOr("") should equal("sibling")
    }

    scenario("delete by id") {
      val created = provider.createCustomerLink(bankId1, customerId1, otherBankId1, otherCustomerId1, "parent")
      val linkId = created.map(_.customerLinkId).openOr("")

      scala.concurrent.Await.result(provider.deleteCustomerLinkById(linkId), scala.concurrent.duration.Duration("10s")) should equal(Full(true))
      provider.getCustomerLinkById(linkId).isDefined should equal(false)
    }
  }
}
