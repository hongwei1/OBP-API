package code.group

import code.setup.ServerSetup

import scala.concurrent.Await
import scala.concurrent.duration._

class GroupProviderTest extends ServerSetup {

  val bankId1 = "obp-bank-group-test1"
  val groupName1 = "Test Group One"
  val groupDescription1 = "First test group"
  val roles1 = List("CanCreateAccount", "CanGetAnyUser")

  private def provider = GroupTrait.group.vend

  private def deleteAll(): Unit = {
    val all = Await.result(provider.getAllGroups(), 10.seconds).openOr(List())
    all.foreach(g => provider.deleteGroup(g.groupId))
  }

  override def beforeAll() = {
    super.beforeAll()
    deleteAll()
  }

  override def afterEach() = {
    super.afterEach()
    deleteAll()
  }

  feature("Group provider CRUD") {
    scenario("create then get a group") {
      Given("a created group")
      val created = provider.createGroup(Some(bankId1), groupName1, groupDescription1, roles1, isEnabled = true)
      created.isDefined should equal(true)
      val groupId = created.map(_.groupId).openOr("")
      groupId.replace("-", "").size should equal(32)

      When("we get it by id")
      val found = provider.getGroup(groupId)

      Then("the fields round-trip")
      found.isDefined should equal(true)
      found.map(_.groupName).openOr("") should equal(groupName1)
      found.map(_.groupDescription).openOr("") should equal(groupDescription1)
      found.map(_.bankId).openOr(None) should equal(Some(bankId1))
      found.map(_.isEnabled).openOr(false) should equal(true)
      found.map(_.listOfRoles).openOr(List()) should equal(roles1)
    }

    scenario("list groups by bank id and all groups") {
      val created = provider.createGroup(Some(bankId1), groupName1, groupDescription1, roles1, isEnabled = true)
      val groupId = created.map(_.groupId).openOr("")

      When("we list by bank id")
      val byBank = Await.result(provider.getGroupsByBankId(Some(bankId1)), 10.seconds).openOr(List())
      Then("it contains the group")
      byBank.map(_.groupId) should contain(groupId)

      When("we list all groups")
      val all = Await.result(provider.getAllGroups(), 10.seconds).openOr(List())
      Then("it contains the group")
      all.map(_.groupId) should contain(groupId)
    }

    scenario("update a group partially leaves other fields intact") {
      val created = provider.createGroup(Some(bankId1), groupName1, groupDescription1, roles1, isEnabled = true)
      val groupId = created.map(_.groupId).openOr("")

      When("we update only the name and isEnabled")
      val updated = provider.updateGroup(groupId, Some("Renamed Group"), None, None, Some(false))

      Then("changed fields are updated and others preserved")
      updated.isDefined should equal(true)
      val reread = provider.getGroup(groupId)
      reread.map(_.groupName).openOr("") should equal("Renamed Group")
      reread.map(_.isEnabled).openOr(true) should equal(false)
      reread.map(_.groupDescription).openOr("") should equal(groupDescription1)
      reread.map(_.listOfRoles).openOr(List()) should equal(roles1)
    }

    scenario("delete a group") {
      val created = provider.createGroup(Some(bankId1), groupName1, groupDescription1, roles1, isEnabled = true)
      val groupId = created.map(_.groupId).openOr("")

      When("we delete it")
      provider.deleteGroup(groupId) should equal(net.liftweb.common.Full(true))

      Then("it can no longer be found")
      provider.getGroup(groupId).isDefined should equal(false)
    }
  }
}
