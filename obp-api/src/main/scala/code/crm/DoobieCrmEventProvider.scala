package code.crm

import code.api.util.DoobieUtil
import code.api.util.ErrorMessages._
import code.crm.CrmEvent.{CrmEvent, CrmEventId}
import code.model.dataAccess.ResourceUser
import code.users.Users
import com.openbankproject.commons.model.BankId
import doobie._
import doobie.implicits._

import java.sql.Timestamp
import java.util.Date
import scala.collection.immutable.List

object DoobieCrmEventProvider extends CrmEventProvider {

  // Table "mappedcrmevent". muserid is a BIGINT FK to resourceuser.id; the `user` accessor resolves it via
  // Users.users.vend.getResourceUserByResourceUserId (same as the Mapper's mUserId.foreign navigation).
  private case class CrmRow(
    mcrmeventid: Option[String],
    mbankid: Option[String],
    muserid: Option[Long],
    mcategory: Option[String],
    mdetail: Option[String],
    mchannel: Option[String],
    mscheduleddate: Option[Timestamp],
    mactualdate: Option[Timestamp],
    mresult: Option[String],
    mcustomername: Option[String],
    mcustomernumber: Option[String]
  )

  private case class DoobieCrmEvent(row: CrmRow) extends CrmEvent {
    override def crmEventId: CrmEventId = CrmEventId(row.mcrmeventid.getOrElse(""))
    override def bankId: BankId = BankId(row.mbankid.getOrElse(""))
    override def category: String = row.mcategory.getOrElse("")
    override def detail: String = row.mdetail.getOrElse("")
    override def channel: String = row.mchannel.getOrElse("")
    override def scheduledDate: Date = row.mscheduleddate.orNull
    override def actualDate: Date = row.mactualdate.orNull
    override def result: String = row.mresult.getOrElse("")
    override def user: ResourceUser =
      Users.users.vend.getResourceUserByResourceUserId(row.muserid.getOrElse(0L)).openOrThrowException(attemptedToOpenAnEmptyBox)
    override def customerName: String = row.mcustomername.getOrElse("")
    override def customerNumber: String = row.mcustomernumber.getOrElse("")
  }

  private def nn(s: String): String = if (s == null) "" else s

  private val selectCols: Fragment =
    fr"""SELECT mcrmeventid, mbankid, muserid, mcategory, mdetail, mchannel, mscheduleddate, mactualdate,
                mresult, mcustomername, mcustomernumber
         FROM mappedcrmevent"""

  override protected def getEventsFromProvider(bankId: BankId): Option[List[CrmEvent]] =
    Some(
      DoobieUtil.runQuery((selectCols ++ fr"WHERE mbankid = ${nn(bankId.value)}").query[CrmRow].to[List])
        .map(DoobieCrmEvent(_)))

  override protected def getEventsFromProvider(bankId: BankId, user: ResourceUser): Option[List[CrmEvent]] =
    Some(
      DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE mbankid = ${nn(bankId.value)} AND muserid = ${user.userPrimaryKey.value}")
          .query[CrmRow].to[List]).map(DoobieCrmEvent(_)))

  override protected def getEventFromProvider(crmEventId: CrmEventId): Option[CrmEvent] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE mcrmeventid = ${nn(crmEventId.value)} LIMIT 1").query[CrmRow].option)
      .map(DoobieCrmEvent(_))
}
