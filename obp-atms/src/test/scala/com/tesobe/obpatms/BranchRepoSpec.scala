package com.tesobe.obpatms

import com.openbankproject.commons.model._
import org.scalatest.{BeforeAndAfterEach, FlatSpec, Matchers}

class BranchRepoSpec extends FlatSpec with Matchers with BeforeAndAfterEach {

  private var db: Database = _
  private var repo: BranchRepo = _

  override def beforeEach(): Unit = {
    val dbName = s"branches_test_${System.nanoTime()}"
    db = new Database(DbConfig(
      jdbcUrl = s"jdbc:h2:mem:$dbName;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
      username = "sa", password = "", maxPoolSize = 2
    ))
    db.migrate()
    repo = new BranchRepo(db)
  }

  override def afterEach(): Unit = db.close()

  private def sampleBranch(branchId: String = "branch-1", bankId: String = "bank-1"): BranchTCommons = BranchTCommons(
    branchId = BranchId(branchId),
    bankId = BankId(bankId),
    name = "Baker Street Branch",
    address = Address("221B Baker St", "", "", "London", Some("Greater London"), "England", "NW1 6XE", "GB"),
    location = Location(51.5237, -0.1585, None, None),
    lobbyString = None,
    driveUpString = None,
    meta = Meta(License("license-1", "Open Data License")),
    branchRouting = None,
    lobby = Some(Lobby(
      monday = List(OpeningTimes("09:00", "17:00")), tuesday = List(OpeningTimes("09:00", "17:00")),
      wednesday = List(OpeningTimes("09:00", "17:00")), thursday = List(OpeningTimes("09:00", "17:00")),
      friday = List(OpeningTimes("09:00", "17:00")), saturday = List(OpeningTimes("10:00", "14:00")),
      sunday = List(OpeningTimes("", ""))
    )),
    driveUp = Some(DriveUp(
      monday = OpeningTimes("08:00", "18:00"), tuesday = OpeningTimes("08:00", "18:00"),
      wednesday = OpeningTimes("08:00", "18:00"), thursday = OpeningTimes("08:00", "18:00"),
      friday = OpeningTimes("08:00", "18:00"), saturday = OpeningTimes("09:00", "13:00"),
      sunday = OpeningTimes("", "")
    )),
    isAccessible = Some(true),
    accessibleFeatures = Some("wheelchair_ramp"),
    branchType = Some("full_service"),
    moreInfo = Some("Parking available"),
    phoneNumber = Some("+44 20 7946 0000"),
    isDeleted = Some(false)
  )

  "BranchRepo" should "round-trip every field through upsert and findOne" in {
    repo.upsert(sampleBranch())
    val fetched = repo.findOne(BankId("bank-1"), BranchId("branch-1"))

    fetched shouldBe defined
    val branch = fetched.get
    branch.name shouldBe "Baker Street Branch"
    branch.address.city shouldBe "London"
    branch.location.latitude shouldBe 51.5237
    branch.lobby.get.monday shouldBe List(OpeningTimes("09:00", "17:00"))
    branch.lobby.get.saturday shouldBe List(OpeningTimes("10:00", "14:00"))
    branch.driveUp.get.monday shouldBe OpeningTimes("08:00", "18:00")
    branch.isAccessible shouldBe Some(true)
    branch.accessibleFeatures shouldBe Some("wheelchair_ramp")
    branch.phoneNumber shouldBe Some("+44 20 7946 0000")
    branch.isDeleted shouldBe Some(false)
  }

  it should "default branchRouting to scheme BRANCH_ID and the branch's own id when unset" in {
    repo.upsert(sampleBranch())
    val branch = repo.findOne(BankId("bank-1"), BranchId("branch-1")).get

    branch.branchRouting.get.scheme shouldBe "BRANCH_ID"
    branch.branchRouting.get.address shouldBe "branch-1"
  }

  it should "update an existing row in place on a second upsert" in {
    repo.upsert(sampleBranch())
    repo.upsert(sampleBranch().copy(name = "Renamed Branch"))

    repo.findByBank(BankId("bank-1")) should have size 1
    repo.findOne(BankId("bank-1"), BranchId("branch-1")).get.name shouldBe "Renamed Branch"
  }

  it should "scope findByBank to the requested bank" in {
    repo.upsert(sampleBranch(branchId = "branch-1", bankId = "bank-1"))
    repo.upsert(sampleBranch(branchId = "branch-2", bankId = "bank-2"))

    repo.findByBank(BankId("bank-1")).map(_.branchId.value) shouldBe List("branch-1")
  }
}
