package code.socialmedia

import code.setup.ServerSetup

import java.util.Date

class SocialMediaProviderTest extends ServerSetup {

  private def provider = SocialMediaHandle.socialMediaHandleProvider.vend

  feature("SocialMedia provider") {
    scenario("add then get social media handles for a customer") {
      val customerNumber = "social-cust-001"
      val dateAdded = new Date(1600000000000L)
      val dateActivated = new Date(1600000100000L)

      Given("a social media handle is added")
      provider.addSocialMedias(customerNumber, "Twitter", "@obp_test", dateAdded, dateActivated) should equal(true)

      When("we get social media handles for the customer")
      val found = provider.getSocialMedias(customerNumber)

      Then("the handle round-trips")
      found.size should equal(1)
      val sm = found.head
      sm.customerNumber should equal(customerNumber)
      sm.`type` should equal("Twitter")
      sm.handle should equal("@obp_test")
      sm.dateAdded.getTime should equal(dateAdded.getTime)
      sm.dateActivated.getTime should equal(dateActivated.getTime)
    }

    scenario("get social media handles for an unknown customer is empty") {
      provider.getSocialMedias("no-such-customer-number").size should equal(0)
    }
  }
}
