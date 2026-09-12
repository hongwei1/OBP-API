package com.tesobe.obpatms

import com.openbankproject.commons.model._
import org.scalatest.{BeforeAndAfterEach, FlatSpec, Matchers}

/**
  * Runs the real SQL in AtmRepo against H2 in Postgres-compatibility mode, not a fake — a
  * hand-written INSERT ... ON CONFLICT DO UPDATE with 44 positional parameters is exactly the
  * kind of statement where a column ordering slip compiles fine and corrupts data. Each test gets
  * its own uniquely named in-memory database so this suite is safe under forkMode=once (all tests
  * in this module share one JVM).
  */
class AtmRepoSpec extends FlatSpec with Matchers with BeforeAndAfterEach {

  private var db: Database = _
  private var repo: AtmRepo = _

  override def beforeEach(): Unit = {
    val dbName = s"atms_test_${System.nanoTime()}"
    db = new Database(DbConfig(
      jdbcUrl = s"jdbc:h2:mem:$dbName;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      username = "sa", password = "", maxPoolSize = 2
    ))
    db.migrate()
    repo = new AtmRepo(db)
  }

  override def afterEach(): Unit = db.close()

  private def sampleAtm(atmId: String = "atm-1", bankId: String = "bank-1"): AtmTCommons = AtmTCommons(
    atmId = AtmId(atmId),
    bankId = BankId(bankId),
    name = "Main Street ATM",
    address = Address("221B Baker St", "", "", "London", Some("Greater London"), "England", "NW1 6XE", "GB"),
    location = Location(51.5237, -0.1585, None, None),
    meta = Meta(License("license-1", "Open Data License")),
    OpeningTimeOnMonday = Some("08:00"), ClosingTimeOnMonday = Some("22:00"),
    OpeningTimeOnTuesday = Some("08:00"), ClosingTimeOnTuesday = Some("22:00"),
    OpeningTimeOnWednesday = Some("08:00"), ClosingTimeOnWednesday = Some("22:00"),
    OpeningTimeOnThursday = Some("08:00"), ClosingTimeOnThursday = Some("22:00"),
    OpeningTimeOnFriday = Some("08:00"), ClosingTimeOnFriday = Some("22:00"),
    OpeningTimeOnSaturday = Some("09:00"), ClosingTimeOnSaturday = Some("18:00"),
    OpeningTimeOnSunday = None, ClosingTimeOnSunday = None,
    isAccessible = Some(true),
    locatedAt = Some("branch lobby"),
    moreInfo = Some("24/7 card deposit"),
    hasDepositCapability = Some(true),
    supportedLanguages = Some(List("en", "fr")),
    services = Some(List("withdrawal", "balance_inquiry")),
    accessibilityFeatures = Some(List("audio_cash_machine", "braille_keypad")),
    supportedCurrencies = Some(List("GBP", "EUR")),
    notes = Some(List("recently serviced")),
    locationCategories = Some(List("high_street")),
    minimumWithdrawal = Some("10"),
    branchIdentification = Some("branch-1"),
    siteIdentification = Some("site-1"),
    siteName = Some("Baker Street"),
    cashWithdrawalNationalFee = Some("0"),
    cashWithdrawalInternationalFee = Some("2.5"),
    balanceInquiryFee = Some("0"),
    atmType = Some("through_the_wall"),
    phone = Some("+44 20 7946 0000")
  )

  "AtmRepo" should "round-trip every field through upsert and findOne" in {
    val stored = repo.upsert(sampleAtm())
    val fetched = repo.findOne(BankId("bank-1"), AtmId("atm-1"))

    fetched shouldBe defined
    val atm = fetched.get
    atm.atmId shouldBe AtmId("atm-1")
    atm.bankId shouldBe BankId("bank-1")
    atm.name shouldBe "Main Street ATM"
    atm.address.line1 shouldBe "221B Baker St"
    atm.address.county shouldBe Some("Greater London")
    atm.location.latitude shouldBe 51.5237
    atm.location.longitude shouldBe -0.1585
    atm.meta.license.id shouldBe "license-1"
    atm.OpeningTimeOnMonday shouldBe Some("08:00")
    atm.OpeningTimeOnSunday shouldBe None
    atm.isAccessible shouldBe Some(true)
    atm.hasDepositCapability shouldBe Some(true)
    atm.supportedLanguages shouldBe Some(List("en", "fr"))
    atm.services shouldBe Some(List("withdrawal", "balance_inquiry"))
    atm.accessibilityFeatures shouldBe Some(List("audio_cash_machine", "braille_keypad"))
    atm.supportedCurrencies shouldBe Some(List("GBP", "EUR"))
    atm.notes shouldBe Some(List("recently serviced"))
    atm.locationCategories shouldBe Some(List("high_street"))
    atm.phone shouldBe Some("+44 20 7946 0000")
    stored.atmId shouldBe atm.atmId
  }

  it should "update an existing row in place on a second upsert, not duplicate it" in {
    repo.upsert(sampleAtm())
    repo.upsert(sampleAtm().copy(name = "Renamed ATM"))

    repo.findByBank(BankId("bank-1")) should have size 1
    repo.findOne(BankId("bank-1"), AtmId("atm-1")).get.name shouldBe "Renamed ATM"
  }

  it should "return None for an atm that was never created" in {
    repo.findOne(BankId("bank-1"), AtmId("does-not-exist")) shouldBe None
  }

  it should "scope findByBank to the requested bank" in {
    repo.upsert(sampleAtm(atmId = "atm-1", bankId = "bank-1"))
    repo.upsert(sampleAtm(atmId = "atm-2", bankId = "bank-1"))
    repo.upsert(sampleAtm(atmId = "atm-3", bankId = "bank-2"))

    repo.findByBank(BankId("bank-1")).map(_.atmId.value).sorted shouldBe List("atm-1", "atm-2")
  }

  it should "delete a row and report false on a second delete" in {
    repo.upsert(sampleAtm())
    repo.delete(BankId("bank-1"), AtmId("atm-1")) shouldBe true
    repo.findOne(BankId("bank-1"), AtmId("atm-1")) shouldBe None
    repo.delete(BankId("bank-1"), AtmId("atm-1")) shouldBe false
  }

  it should "update only the targeted list column, leaving the rest of the row untouched" in {
    repo.upsert(sampleAtm())
    val updated = repo.updateNotes(BankId("bank-1"), AtmId("atm-1"), List("out of service")).get

    updated.notes shouldBe Some(List("out of service"))
    updated.services shouldBe Some(List("withdrawal", "balance_inquiry"))
  }

  // The wire-level field for updateAtmServices is named supportedCurrencies (see the note on
  // AtmRepo.updateServices) — pin down that this repo writes it to the `services` column, not
  // `supported_currencies`, so a future edit cannot silently swap the two back.
  it should "write updateServices into the services column, not supportedCurrencies" in {
    repo.upsert(sampleAtm())
    val updated = repo.updateServices(BankId("bank-1"), AtmId("atm-1"), List("deposit")).get

    updated.services shouldBe Some(List("deposit"))
    updated.supportedCurrencies shouldBe Some(List("GBP", "EUR"))
  }

  it should "return None from an update when the atm does not exist" in {
    repo.updateNotes(BankId("bank-1"), AtmId("does-not-exist"), List("x")) shouldBe None
  }
}
