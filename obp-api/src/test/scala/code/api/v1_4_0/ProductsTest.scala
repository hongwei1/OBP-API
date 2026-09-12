package code.api.v1_4_0

import code.api.util.APIUtil.OAuth._
import code.api.v1_4_0.JSONFactory1_4_0.{ProductJson, ProductsJson}
import code.bankconnectors.Connector
import code.products.MappedProduct
import code.setup.{DefaultUsers, ServerSetup}
import com.openbankproject.commons.model.{BankId, License, Meta, Product, ProductCode}
import net.liftweb.mapper.By

import scala.concurrent.Await
import scala.concurrent.duration._

class ProductsTest extends ServerSetup with DefaultUsers with V140ServerSetup {

  val BankWithLicense = BankId("testBank1")
  val BankWithoutLicense = BankId("testBank2")

  // Have to repeat the constructor parameters from the trait
  case class ProductImpl(bankId: BankId,
                        code : ProductCode,
                        parentProductCode : ProductCode,
                        name : String,
                        category: String,
                        family : String,
                        superFamily : String,
                        moreInfoUrl: String,
                        termsAndConditionsUrl: String,
                        details: String,
                        description: String,
                        meta: Meta) extends Product

  val fakeMeta = Meta(
    License (
      id = "example-data-license",
      name = "Example Data License"
  )
  )

  val fakeMetaNoLicense = Meta(
    License (
      id = "",
      name = ""
    )
  )


  val fakeProduct1 = ProductImpl(BankWithLicense, ProductCode("prod1"), ProductCode(""), "name 1", "cat 1", "family 1", "super family 1", "http://www.example.com/moreinfo1.html", "http://www.example.com/termsAndConditionsUrl1.html","", "", fakeMeta)
  val fakeProduct2 = ProductImpl(BankWithLicense, ProductCode("prod2"), ProductCode(""), "name 2", "cat 1", "family 1", "super family 1", "http://www.example.com/moreinfo2.html", "http://www.example.com/termsAndConditionsUrl2.html", "","", fakeMeta)

  // Belongs to the other bank, so it must not show up in BankWithLicense's list
  val fakeProduct3 = ProductImpl(BankWithoutLicense, ProductCode("prod3"), ProductCode(""), "name 3", "cat 1", "family 1", "super family 1", "http://www.example.com/moreinfo3.html", "http://www.example.com/termsAndConditionsUrl3.html", "","", fakeMetaNoLicense)

  def verifySameData(product: Product, productJson : ProductJson) = {
    product.name should equal (productJson.name)
    // Note: We don't currently return the BankId because its part of the URL, so we don't test for that.
    product.code.value should equal (productJson.code)
    product.category should equal (productJson.category)
    product.family should equal (productJson.family)
    product.meta.license.id should equal (productJson.meta.license.id)
    product.meta.license.name should equal (productJson.meta.license.name)
    product.moreInfoUrl should equal (productJson.more_info_url)
    product.name should equal (productJson.name)
    product.superFamily should equal (productJson.super_family)
  }

  private def seed(p: Product): Unit =
    Await.result(
      Connector.connector.vend.createOrUpdateProduct(
        bankId = p.bankId.value,
        code = p.code.value,
        parentProductCode = Some(p.parentProductCode.value),
        name = p.name,
        category = p.category,
        family = p.family,
        superFamily = p.superFamily,
        moreInfoUrl = p.moreInfoUrl,
        termsAndConditionsUrl = p.termsAndConditionsUrl,
        details = p.details,
        description = p.description,
        metaLicenceId = p.meta.license.id,
        metaLicenceName = p.meta.license.name,
        callContext = None
      ), 10.seconds)

  // The endpoint reads through Connector now, so a ProductsProvider mock would never be consulted:
  // the data has to be in the table the mapped connector reads. ServerSetup.beforeEach wipes the
  // tables before every scenario, so seeding has to happen per scenario rather than once per suite
  // — same as the atms and branches suites.
  override def beforeEach(): Unit = {
    super.beforeEach()
    List(fakeProduct1, fakeProduct2, fakeProduct3).foreach(seed)
  }

  override def afterEach(): Unit = {
    MappedProduct.bulkDelete_!!(By(MappedProduct.mBankId, BankWithLicense.value))
    MappedProduct.bulkDelete_!!(By(MappedProduct.mBankId, BankWithoutLicense.value))
    super.afterEach()
  }

  feature("Getting bank products") {

    scenario("We try to get products for a bank without a data license for product information") {
      When("We make a request")
      val request = (v1_4Request / "banks" / BankWithoutLicense.value / "products").GET <@(user1)
      val response = makeGetRequest(request)

      Then("We should get a 200")
      response.code should equal(200)

    }

    scenario("We try to get products for a bank with a data license for product information") {
      When("We make a request")
      val request = (v1_4Request / "banks" / BankWithLicense.value / "products").GET <@(user1)
      val response = makeGetRequest(request)

      Then("We should get a 200")
      response.code should equal(200)

      And("We should get the right json format containing a list of products")
      val wholeResponseBody = response.body
      val responseBodyOpt = wholeResponseBody.extractOpt[ProductsJson]
      responseBodyOpt.isDefined should equal(true)

      val responseBody = responseBodyOpt.get

      And("We should get the products of that bank and no other bank's")
      val products = responseBody.products

      // Order of Products in the list is arbitrary. Only this bank's two products are expected:
      // the third fixture belongs to BankWithoutLicense, and the query is scoped by bank.
      products.size should equal(2)
      val first = products(0)
      if (first.code == fakeProduct1.code.value) {
        verifySameData(fakeProduct1, first)
        verifySameData(fakeProduct2, products(1))
      } else if (first.code == fakeProduct2.code.value) {
        verifySameData(fakeProduct2, first)
        verifySameData(fakeProduct1, products(1))
      } else {
        fail("Incorrect products")
      }
    }
  }
}
