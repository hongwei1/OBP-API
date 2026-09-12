package code.api.v1_4_0

import code.api.util.APIUtil.OAuth._
import code.api.util.OBPQueryParam
import code.api.v1_4_0.JSONFactory1_4_0.{AtmJson, AtmsJson}
import code.atms.MappedAtm
import code.bankconnectors.Connector
import net.liftweb.mapper.By
import scala.concurrent.Await
import scala.concurrent.duration._
import code.setup.DefaultUsers
import com.openbankproject.commons.model.{LicenseT, _}
import net.liftweb.common.Box

class AtmsTest extends V140ServerSetup with DefaultUsers {

  val bankWithLicense = BankId("testBank1")
  val bankWithoutLicense = BankId("testBank2")

  // Have to repeat the constructor parameters from the trait
  case class AtmTImpl(atmId : AtmId,
                      bankId: BankId,
                      name : String,
                      address : AddressT,
                      location : LocationT,
                      meta : MetaT,

                      // Opening times
                      OpeningTimeOnMonday : Option[String],
                      ClosingTimeOnMonday : Option[String],

                      OpeningTimeOnTuesday : Option[String],
                      ClosingTimeOnTuesday : Option[String],

                      OpeningTimeOnWednesday : Option[String],
                      ClosingTimeOnWednesday : Option[String],

                      OpeningTimeOnThursday : Option[String],
                      ClosingTimeOnThursday: Option[String],

                      OpeningTimeOnFriday : Option[String],
                      ClosingTimeOnFriday : Option[String],

                      OpeningTimeOnSaturday : Option[String],
                      ClosingTimeOnSaturday : Option[String],

                      OpeningTimeOnSunday: Option[String],
                      ClosingTimeOnSunday : Option[String],

                      // Easy access for people who use wheelchairs etc. "Y"=true "N"=false ""=Unknown
                      isAccessible : Option[Boolean],

                      locatedAt : Option[String],
                      moreInfo : Option[String],
                      hasDepositCapability : Option[Boolean],
                      supportedLanguages: Option[List[String]]= None,
                      services: Option[List[String]] = None,
                      accessibilityFeatures: Option[List[String]] = None,
                      supportedCurrencies: Option[List[String]] = None,
                      notes: Option[List[String]] = None,
                      locationCategories: Option[List[String]]= None,
                      minimumWithdrawal: Option[String] = None,
                      branchIdentification: Option[String] = None,
                      siteIdentification: Option[String] = None,
                      siteName: Option[String] = None,
                      cashWithdrawalNationalFee: Option[String] = None,
                      cashWithdrawalInternationalFee: Option[String] = None,
                      balanceInquiryFee: Option[String] = None,
                      atmType: Option[String] = None,
                      phone: Option[String] = None,
                    ) extends AtmT

  case class AddressImpl(line1 : String, line2 : String, line3 : String, city : String, county : Option[String],
                         state : String, postCode : String, countryCode : String) extends AddressT


  val fakeAddress1 = AddressImpl("Duckerstrasse 86", "Prenzlauerberg", "lala", "Berlin", Some("a county"), "Berlin", "10437", "DE")
  val fakeAddress2 = fakeAddress1.copy(line1 = "00000")

  val fakeMeta = new MetaT {
    val license = new LicenseT {
      override def id: String = "sample-license"
      override def name: String = "Sample License"
    }
  }

  val fakeMetaNoLicense = new MetaT {
    val license = new LicenseT {
      override def id: String = ""
      override def name: String = ""
    }
  }

  val fakeLocation = new LocationT {
   override def latitude: Double = 11.11
   override def longitude: Double = 22.22
  }


  val fakeLocation2 = new LocationT {
    override def latitude: Double = 11.1111
    override def longitude: Double = 22.2222
  }

  val fakeOpeningTime = Some("10:00")
  val fakeClosingTime = Some("18:00")

  val fakeIsAccessible = Some(true)
  val fakeBranchType = Some("Main")
  val fakeMoreInfo = Some("Not available when it's snowing.")
  val fakehasDepositCapability = Some(true)
  val fakeSupportedLanguages = Some(List("es"))



  val fakeAtm1 = AtmTImpl(AtmId("atm1"), bankWithLicense, "Atm 1", fakeAddress1, fakeLocation, fakeMeta,
    fakeOpeningTime,fakeClosingTime,
    fakeOpeningTime,fakeClosingTime,
    fakeOpeningTime,fakeClosingTime,
    fakeOpeningTime,fakeClosingTime,
    fakeOpeningTime,fakeClosingTime,
    fakeOpeningTime,fakeClosingTime,
    fakeOpeningTime,fakeClosingTime,
    fakeIsAccessible,
    fakeBranchType,
    fakeMoreInfo,
    fakehasDepositCapability,
    fakeSupportedLanguages)
  
  val fakeAtm2 = AtmTImpl(AtmId("atm2"), bankWithLicense, "Atm 2", fakeAddress2, fakeLocation2, fakeMeta,
    fakeOpeningTime,fakeClosingTime,
    fakeOpeningTime,fakeClosingTime,
    fakeOpeningTime,fakeClosingTime,
    fakeOpeningTime,fakeClosingTime,
    fakeOpeningTime,fakeClosingTime,
    fakeOpeningTime,fakeClosingTime,
    fakeOpeningTime,fakeClosingTime,
    fakeIsAccessible,
    fakeBranchType,
    fakeMoreInfo,
    fakehasDepositCapability,
    fakeSupportedLanguages)
  val fakeAtm3 = AtmTImpl(AtmId("atm3"), bankWithLicense, "Atm 3", fakeAddress2, fakeLocation, fakeMetaNoLicense,
    fakeOpeningTime,fakeClosingTime,
    fakeOpeningTime,fakeClosingTime,
    fakeOpeningTime,fakeClosingTime,
    fakeOpeningTime,fakeClosingTime,
    fakeOpeningTime,fakeClosingTime,
    fakeOpeningTime,fakeClosingTime,
    fakeOpeningTime,fakeClosingTime,
    fakeIsAccessible,
    fakeBranchType,
    fakeMoreInfo,
    fakehasDepositCapability,
    fakeSupportedLanguages) // Should not be returned


  // TODO Extend to more fields
  def verifySameData(atm: AtmT, atmJson : AtmJson) = {
    atm.name should equal (atmJson.name)
    atm.atmId should equal(AtmId(atmJson.id))
    atm.address.line1 should equal(atmJson.address.line_1)
    atm.address.line2 should equal(atmJson.address.line_2)
    atm.address.line3 should equal(atmJson.address.line_3)
    atm.address.city should equal(atmJson.address.city)
    atm.address.state should equal(atmJson.address.state)
    atm.address.countryCode should equal(atmJson.address.country)
    atm.address.postCode should equal(atmJson.address.postcode)
    atm.location.latitude should equal(atmJson.location.latitude)
    atm.location.longitude should equal(atmJson.location.longitude)
  }

  /*
  So we can test the API layer, rather than the connector, use a mock connector.
   */
  // ServerSetup.beforeEach wipes the tables and recreates the banks before every scenario, so
  // rows seeded once per suite would be gone by the first request. Seed per scenario, as the
  // v3.0.0 suite does.
  override def beforeEach(): Unit = {
    super.beforeEach()
    // The endpoint reads through Connector now, so the data has to be in the table the mapped
    // connector reads, not in a provider mock. The fixtures already carry bankWithLicense.
    List(fakeAtm1, fakeAtm2, fakeAtm3).foreach { a =>
      Await.result(Connector.connector.vend.createOrUpdateAtm(a, None), 10.seconds)
    }
  }

  override def afterEach(): Unit = {
    MappedAtm.bulkDelete_!!(By(MappedAtm.mBankId, bankWithLicense.value))
    super.afterEach()
  }

  feature("Getting bank ATMs") {

    scenario("We try to get ATMs for a bank without a data license for ATM information") {

      When("We make a request")
      val request = (v1_4Request / "banks" / bankWithoutLicense.value / "atms").GET <@ user1
      val response = makeGetRequest(request)

      Then("We should get a 200")
      response.code should equal(200)

    }

    scenario("We try to get ATMs for a bank with a data license for ATM information") {
      When("We make a request")
      val request = (v1_4Request / "banks" / bankWithLicense.value / "atms").GET <@ user1
      val response = makeGetRequest(request)

      Then("We should get a 200")
      response.code should equal(200)

      And("We should get the right json format containing a list of ATMs")
      val wholeResponseBody = response.body
      val responseBodyOpt = wholeResponseBody.extractOpt[AtmsJson]
      responseBodyOpt.isDefined should equal(true)

      val responseBody = responseBodyOpt.get

      And("We should get the right atms")
      val atms = responseBody.atms

      // Order of ATMs in the list is arbitrary
      atms.size should equal(3)
      val first = atms(0)
      if(first.id == fakeAtm1.atmId.value) {
        verifySameData(fakeAtm1, first)
        verifySameData(fakeAtm2, atms(1))
      } else if (first.id == fakeAtm2.atmId.value) {
        verifySameData(fakeAtm2, first)
        verifySameData(fakeAtm1, atms(1))
      } else {
        fail("Incorrect ATMs")
      }

    }
  }

}
