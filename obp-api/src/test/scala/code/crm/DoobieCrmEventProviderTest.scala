package code.crm

import java.util.Date

import code.crm.CrmEvent.CrmEventId
import code.setup.{DefaultUsers, ServerSetup}

class DoobieCrmEventProviderTest extends ServerSetup with DefaultUsers {

  override def beforeAll(): Unit = {
    super.beforeAll()
    MappedCrmEvent.bulkDelete_!!()
  }

  override def afterEach(): Unit = {
    super.afterEach()
    MappedCrmEvent.bulkDelete_!!()
  }

  private def createEvent1() = MappedCrmEvent.create
    .mCrmEventId("DOOBIE_EVT_001")
    .mBankId(testBankId1.value)
    .mUserId(resourceUser1)
    .mScheduledDate(new Date(12340000L))
    .mActualDate(new Date(12340000L))
    .mChannel("PHONE")
    .mDetail("Doobie call detail 1")
    .mResult("Resolved")
    .mCategory("CategoryA")
    .saveMe()

  private def createEvent2() = MappedCrmEvent.create
    .mCrmEventId("DOOBIE_EVT_002")
    .mBankId(testBankId2.value)
    .mUserId(resourceUser2)
    .mScheduledDate(new Date(22340000L))
    .mActualDate(new Date(22340000L))
    .mChannel("EMAIL")
    .mDetail("Doobie call detail 2")
    .mResult("Pending")
    .mCategory("CategoryB")
    .saveMe()

  private def createEvent3() = MappedCrmEvent.create
    .mCrmEventId("DOOBIE_EVT_003")
    .mBankId(testBankId2.value)
    .mUserId(resourceUser2)
    .mScheduledDate(new Date(32340000L))
    .mActualDate(new Date(32340000L))
    .mChannel("BRANCH")
    .mDetail("Doobie call detail 3")
    .mResult("Completed")
    .mCategory("CategoryB")
    .saveMe()

  private val vend = CrmEvent.crmEventProvider.vend

  feature("DoobieCrmEventProvider — getEventsFromProvider(bankId)") {

    scenario("No events exist for bank — returns empty list") {
      createEvent2() // only bankId2
      val result = vend.getCrmEvents(testBankId1)
      result.isDefined should equal(true)
      result.get.size should equal(0)
    }

    scenario("Events exist for bank — returns them all") {
      createEvent2()
      createEvent3()
      val result = vend.getCrmEvents(testBankId2)
      result.isDefined should equal(true)
      result.get.size should equal(2)
    }
  }

  feature("DoobieCrmEventProvider — getEventsFromProvider(bankId, user)") {

    scenario("No events exist for that bank+user combo") {
      createEvent1() // bankId1 / user1
      val result = vend.getCrmEvents(testBankId1, resourceUser2)
      result.isDefined should equal(true)
      result.get.size should equal(0)
    }

    scenario("Events exist for bank+user — returns them") {
      createEvent2()
      createEvent3()
      val result = vend.getCrmEvents(testBankId2, resourceUser2)
      result.isDefined should equal(true)
      result.get.size should equal(2)
    }

    scenario("Field values are correct") {
      createEvent1()
      val result = vend.getCrmEvents(testBankId1, resourceUser1)
      result.isDefined should equal(true)
      val evt = result.get.head
      evt.crmEventId should equal(CrmEventId("DOOBIE_EVT_001"))
      evt.bankId should equal(testBankId1)
      evt.channel should equal("PHONE")
      evt.detail should equal("Doobie call detail 1")
      evt.result should equal("Resolved")
      evt.category should equal("CategoryA")
      evt.user.userPrimaryKey should equal(resourceUser1.userPrimaryKey)
    }
  }

  feature("DoobieCrmEventProvider — getEventFromProvider(crmEventId)") {

    scenario("Event does not exist — returns None") {
      val result = vend.getCrmEvent(CrmEventId("NO_SUCH_ID"))
      result.isDefined should equal(false)
    }

    scenario("Event exists — returns it with correct fields") {
      createEvent1()
      val result = vend.getCrmEvent(CrmEventId("DOOBIE_EVT_001"))
      result.isDefined should equal(true)
      val evt = result.get
      evt.bankId should equal(testBankId1)
      evt.channel should equal("PHONE")
    }
  }
}
