package com.tesobe.obpatms

import com.openbankproject.commons.dto._
import com.openbankproject.commons.model._
import com.openbankproject.commons.util.JsonSerializers
import org.json4s.native.JsonMethods.parse
import org.json4s.native.Serialization.write
import org.scalatest.{BeforeAndAfterEach, FlatSpec, Matchers}

/**
  * Exercises the handler exactly the way RabbitMqServer does: build the same OutBound* case
  * class the core's RabbitMQConnector_vOct2024 would publish, serialize it with the same
  * Formats, hand the raw JSON string to `handle`, and extract the raw JSON string it returns
  * back into the InBound* type the core would extract. This is the fidelity check for the wire
  * protocol itself, not just this module's own repos — a change to obp-commons' DTOs or a typo
  * in a messageId string here would show up as a decode failure, not a silently wrong answer.
  */
class MessageHandlerSpec extends FlatSpec with Matchers with BeforeAndAfterEach {

  private implicit val formats = JsonSerializers.nullTolerateFormats

  private var db: Database = _
  private var handler: MessageHandler = _

  override def beforeEach(): Unit = {
    val dbName = s"handler_test_${System.nanoTime()}"
    db = new Database(DbConfig(
      jdbcUrl = s"jdbc:h2:mem:$dbName;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      username = "sa", password = "", maxPoolSize = 2
    ))
    db.migrate()
    handler = new MessageHandler(new AtmRepo(db), new BranchRepo(db))
  }

  override def afterEach(): Unit = db.close()

  private val cc = OutboundAdapterCallContext(correlationId = "corr-1")

  private def sampleAtm: AtmTCommons = AtmTCommons(
    atmId = AtmId("atm-1"), bankId = BankId("bank-1"), name = "Main Street ATM",
    address = Address("221B Baker St", "", "", "London", None, "England", "NW1 6XE", "GB"),
    location = Location(51.5, -0.15, None, None),
    meta = Meta(License("license-1", "Open Data License")),
    OpeningTimeOnMonday = None, ClosingTimeOnMonday = None, OpeningTimeOnTuesday = None, ClosingTimeOnTuesday = None,
    OpeningTimeOnWednesday = None, ClosingTimeOnWednesday = None, OpeningTimeOnThursday = None, ClosingTimeOnThursday = None,
    OpeningTimeOnFriday = None, ClosingTimeOnFriday = None, OpeningTimeOnSaturday = None, ClosingTimeOnSaturday = None,
    OpeningTimeOnSunday = None, ClosingTimeOnSunday = None,
    isAccessible = Some(true), locatedAt = None, moreInfo = None, hasDepositCapability = Some(false),
    supportedLanguages = Some(List("en")), services = Some(List("withdrawal")), accessibilityFeatures = None,
    supportedCurrencies = Some(List("GBP")), notes = None, locationCategories = None, minimumWithdrawal = None,
    branchIdentification = None, siteIdentification = None, siteName = None, cashWithdrawalNationalFee = None,
    cashWithdrawalInternationalFee = None, balanceInquiryFee = None, atmType = None, phone = None
  )

  "MessageHandler" should "answer obp_create_or_update_atm then obp_get_atm with the same data" in {
    val createReq = write(OutBoundCreateOrUpdateAtm(cc, sampleAtm))
    val createReply = handler.handle("obp_create_or_update_atm", createReq).get
    val created = parse(createReply).extract[InBoundCreateOrUpdateAtm]

    created.status.hasNoError shouldBe true
    created.data.name shouldBe "Main Street ATM"

    val getReq = write(OutBoundGetAtm(cc, BankId("bank-1"), AtmId("atm-1")))
    val getReply = handler.handle("obp_get_atm", getReq).get
    val fetched = parse(getReply).extract[InBoundGetAtm]

    fetched.status.hasNoError shouldBe true
    fetched.data.services shouldBe Some(List("withdrawal"))
  }

  it should "answer obp_get_atm for an unknown atm with a 404 status and no exception" in {
    val getReq = write(OutBoundGetAtm(cc, BankId("bank-1"), AtmId("does-not-exist")))
    val getReply = handler.handle("obp_get_atm", getReq).get
    val json = parse(getReply)

    (json \ "status" \ "errorCode").extract[String] shouldBe "404"
  }

  it should "answer obp_get_atms with every atm created for that bank" in {
    handler.handle("obp_create_or_update_atm", write(OutBoundCreateOrUpdateAtm(cc, sampleAtm)))
    handler.handle("obp_create_or_update_atm", write(OutBoundCreateOrUpdateAtm(cc, sampleAtm.copy(atmId = AtmId("atm-2")))))

    val reply = handler.handle("obp_get_atms", write(OutBoundGetAtms(cc, BankId("bank-1"), 100, 0, "", ""))).get
    val inbound = parse(reply).extract[InBoundGetAtms]

    inbound.data.map(_.atmId.value).sorted shouldBe List("atm-1", "atm-2")
  }

  it should "delete an atm and report it gone on a following get" in {
    handler.handle("obp_create_or_update_atm", write(OutBoundCreateOrUpdateAtm(cc, sampleAtm)))

    val deleteReply = handler.handle("obp_delete_atm", write(OutBoundDeleteAtm(cc, sampleAtm))).get
    parse(deleteReply).extract[InBoundDeleteAtm].data shouldBe true

    val getReply = handler.handle("obp_get_atm", write(OutBoundGetAtm(cc, BankId("bank-1"), AtmId("atm-1")))).get
    (parse(getReply) \ "status" \ "errorCode").extract[String] shouldBe "404"
  }

  // Field-naming note: OutBoundUpdateAtmServices carries the services list in a field literally
  // named supportedCurrencies (see AtmRepo.updateServices) — this test pins the handler down to
  // treating it as the services update the core intends, not a supportedCurrencies overwrite.
  it should "apply obp_update_atm_services to the services list, not supportedCurrencies" in {
    handler.handle("obp_create_or_update_atm", write(OutBoundCreateOrUpdateAtm(cc, sampleAtm)))

    val reply = handler.handle("obp_update_atm_services",
      write(OutBoundUpdateAtmServices(cc, BankId("bank-1"), AtmId("atm-1"), List("deposit", "balance_inquiry")))).get
    val updated = parse(reply).extract[InBoundUpdateAtmServices]

    updated.data.services shouldBe Some(List("deposit", "balance_inquiry"))
    updated.data.supportedCurrencies shouldBe Some(List("GBP"))
  }

  it should "answer obp_create_or_update_branch then obp_get_branch with the same data" in {
    val branch = BranchTCommons(
      branchId = BranchId("branch-1"), bankId = BankId("bank-1"), name = "Baker Street Branch",
      address = Address("221B Baker St", "", "", "London", None, "England", "NW1 6XE", "GB"),
      location = Location(51.5, -0.15, None, None), lobbyString = None, driveUpString = None,
      meta = Meta(License("license-1", "Open Data License")), branchRouting = None, lobby = None, driveUp = None,
      isAccessible = Some(true), accessibleFeatures = None, branchType = None, moreInfo = None,
      phoneNumber = Some("+44 20 7946 0000"), isDeleted = Some(false)
    )

    val createReply = handler.handle("obp_create_or_update_branch", write(OutBoundCreateOrUpdateBranch(cc, branch))).get
    parse(createReply).extract[InBoundCreateOrUpdateBranch].data.name shouldBe "Baker Street Branch"

    val getReply = handler.handle("obp_get_branch", write(OutBoundGetBranch(cc, BankId("bank-1"), BranchId("branch-1")))).get
    val fetched = parse(getReply).extract[InBoundGetBranch]
    fetched.data.phoneNumber shouldBe Some("+44 20 7946 0000")
  }

  it should "drop a message with an unrecognised messageId instead of replying" in {
    handler.handle("obp_some_unrouted_method", "{}") shouldBe None
  }

  it should "reply with a generic ErrorMessage instead of throwing on malformed JSON" in {
    val reply = handler.handle("obp_get_atm", "{ not json")
    reply shouldBe defined
    val json = parse(reply.get)
    ErrorMessage.isErrorMessage(json) shouldBe true
  }
}
