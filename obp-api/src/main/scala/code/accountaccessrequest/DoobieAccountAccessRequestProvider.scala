package code.accountaccessrequest

import code.api.util.{APIUtil, DoobieUtil}
import com.openbankproject.commons.model.enums.AccountAccessRequestStatus
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import java.sql.Timestamp
import java.util.Date
import scala.collection.immutable.List

object DoobieAccountAccessRequestProvider extends AccountAccessRequestProvider {

  // Table "accountaccessrequest" (custom dbTableName).
  private case class AarRow(
    accountaccessrequestid: Option[String],
    bankid: Option[String],
    accountid: Option[String],
    viewid: Option[String],
    issystemview: Option[Boolean],
    requestoruserid: Option[String],
    targetuserid: Option[String],
    businessjustification: Option[String],
    status: Option[String],
    checkeruserid: Option[String],
    checkercomment: Option[String],
    createdat: Option[Timestamp],
    updatedat: Option[Timestamp]
  )

  private case class DoobieAccountAccessRequest(row: AarRow) extends AccountAccessRequestTrait {
    override def accountAccessRequestId: String = row.accountaccessrequestid.getOrElse("")
    override def bankId: String = row.bankid.getOrElse("")
    override def accountId: String = row.accountid.getOrElse("")
    override def viewId: String = row.viewid.getOrElse("")
    override def isSystemView: Boolean = row.issystemview.getOrElse(false)
    override def requestorUserId: String = row.requestoruserid.getOrElse("")
    override def targetUserId: String = row.targetuserid.getOrElse("")
    override def businessJustification: String = row.businessjustification.getOrElse("")
    override def status: String = row.status.getOrElse("")
    override def checkerUserId: String = row.checkeruserid.getOrElse("")
    override def checkerComment: String = row.checkercomment.getOrElse("")
    override def created: Date = row.createdat.orNull
    override def updated: Date = row.updatedat.orNull
  }

  private def nn(s: String): String = if (s == null) "" else s

  private val selectCols: Fragment =
    fr"""SELECT accountaccessrequestid, bankid, accountid, viewid, issystemview, requestoruserid, targetuserid,
                businessjustification, status, checkeruserid, checkercomment, createdat, updatedat
         FROM accountaccessrequest"""

  private def findOne(where: Fragment): Box[DoobieAccountAccessRequest] =
    DoobieUtil.runQuery((selectCols ++ where ++ fr"LIMIT 1").query[AarRow].option) match {
      case Some(r) => Full(DoobieAccountAccessRequest(r))
      case None    => Empty
    }

  private def findList(where: Fragment): List[AccountAccessRequestTrait] =
    DoobieUtil.runQuery((selectCols ++ where).query[AarRow].to[List]).map(DoobieAccountAccessRequest(_))

  override def createAccountAccessRequest(bankId: String, accountId: String, viewId: String, isSystemView: Boolean,
                                          requestorUserId: String, targetUserId: String,
                                          businessJustification: String): Box[AccountAccessRequestTrait] =
    tryo {
      val newId = APIUtil.generateUUID()
      val now = new Timestamp(System.currentTimeMillis())
      val status = AccountAccessRequestStatus.INITIATED.toString
      DoobieUtil.runQuery(
        sql"""INSERT INTO accountaccessrequest
                (accountaccessrequestid, bankid, accountid, viewid, issystemview, requestoruserid, targetuserid,
                 businessjustification, status, checkeruserid, checkercomment, createdat, updatedat)
              VALUES ($newId, ${nn(bankId)}, ${nn(accountId)}, ${nn(viewId)}, $isSystemView, ${nn(requestorUserId)},
                      ${nn(targetUserId)}, ${nn(businessJustification)}, $status, '', '', $now, $now)""".update.run)
      DoobieAccountAccessRequest(AarRow(Some(newId), Some(nn(bankId)), Some(nn(accountId)), Some(nn(viewId)),
        Some(isSystemView), Some(nn(requestorUserId)), Some(nn(targetUserId)), Some(nn(businessJustification)),
        Some(status), Some(""), Some(""), Some(now), Some(now)))
    }

  override def getById(accountAccessRequestId: String): Box[AccountAccessRequestTrait] =
    findOne(fr"WHERE accountaccessrequestid = ${nn(accountAccessRequestId)}")

  override def getByAccount(bankId: String, accountId: String): Box[List[AccountAccessRequestTrait]] =
    tryo { findList(fr"WHERE bankid = ${nn(bankId)} AND accountid = ${nn(accountId)} ORDER BY id DESC") }

  override def getByAccountAndStatus(bankId: String, accountId: String, status: String): Box[List[AccountAccessRequestTrait]] =
    tryo { findList(fr"WHERE bankid = ${nn(bankId)} AND accountid = ${nn(accountId)} AND status = ${nn(status)} ORDER BY id DESC") }

  override def getByRequestorUserId(requestorUserId: String): Box[List[AccountAccessRequestTrait]] =
    tryo { findList(fr"WHERE requestoruserid = ${nn(requestorUserId)} ORDER BY id DESC") }

  override def getByUserAccountView(targetUserId: String, bankId: String, accountId: String, viewId: String): Box[AccountAccessRequestTrait] =
    findOne(fr"""WHERE targetuserid = ${nn(targetUserId)} AND bankid = ${nn(bankId)} AND accountid = ${nn(accountId)}
                   AND viewid = ${nn(viewId)} AND status = ${AccountAccessRequestStatus.INITIATED.toString}""")

  override def updateStatus(accountAccessRequestId: String, status: String, checkerUserId: String,
                            checkerComment: String): Box[AccountAccessRequestTrait] =
    findOne(fr"WHERE accountaccessrequestid = ${nn(accountAccessRequestId)}").flatMap { request =>
      tryo {
        val now = new Timestamp(System.currentTimeMillis())
        DoobieUtil.runQuery(
          sql"""UPDATE accountaccessrequest
                SET status = ${nn(status)}, checkeruserid = ${nn(checkerUserId)}, checkercomment = ${nn(checkerComment)}, updatedat = $now
                WHERE accountaccessrequestid = ${nn(accountAccessRequestId)}""".update.run)
        DoobieAccountAccessRequest(request.row.copy(
          status = Some(nn(status)), checkeruserid = Some(nn(checkerUserId)), checkercomment = Some(nn(checkerComment)), updatedat = Some(now)))
      }
    }
}
