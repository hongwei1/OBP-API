package code.entitlementrequest

import code.api.util.{APIUtil, DoobieUtil, ErrorMessages, OBPAscending, OBPDescending, OBPFromDate, OBPLimit, OBPOffset, OBPOrdering, OBPQueryParam, OBPToDate}
import code.users.Users
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.User
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}

import java.sql.Timestamp
import java.util.Date
import scala.collection.immutable.List
import scala.concurrent.Future

object DoobieEntitlementRequestsProvider extends EntitlementRequestProvider {

  // Table "mappedentitlementrequest": id, mentitlementrequestid, mbankid, muserid, mrolename, createdat, updatedat.
  private case class ReqRow(
    mentitlementrequestid: Option[String],
    mbankid: Option[String],
    muserid: Option[String],
    mrolename: Option[String],
    createdat: Option[Timestamp]
  )

  private case class DoobieEntitlementRequest(row: ReqRow) extends EntitlementRequest {
    override def entitlementRequestId: String = row.mentitlementrequestid.getOrElse("")
    override def bankId: String = row.mbankid.getOrElse("")
    override def user: Box[User] = Users.users.vend.getUserByUserId(row.muserid.getOrElse(""))
    override def roleName: String = row.mrolename.getOrElse("")
    override def created: Date = row.createdat.getOrElse(new Timestamp(0L))
  }

  private def nn(s: String): String = if (s == null) "" else s

  private val selectCols: Fragment =
    fr"SELECT mentitlementrequestid, mbankid, muserid, mrolename, createdat FROM mappedentitlementrequest"

  private def findOne(where: Fragment): Box[DoobieEntitlementRequest] =
    DoobieUtil.runQuery((selectCols ++ where ++ fr"LIMIT 1").query[ReqRow].option) match {
      case Some(r) => Full(DoobieEntitlementRequest(r))
      case None    => Empty
    }

  override def addEntitlementRequest(bankId: String, userId: String, roleName: String): Box[EntitlementRequest] = {
    val newId = APIUtil.generateUUID()
    val now = new Timestamp(System.currentTimeMillis())
    DoobieUtil.runQuery(
      sql"""INSERT INTO mappedentitlementrequest
              (mentitlementrequestid, mbankid, muserid, mrolename, createdat, updatedat)
            VALUES ($newId, ${nn(bankId)}, ${nn(userId)}, ${nn(roleName)}, $now, $now)""".update.run)
    Some(DoobieEntitlementRequest(ReqRow(Some(newId), Some(nn(bankId)), Some(nn(userId)), Some(nn(roleName)), Some(now))))
  }

  override def addEntitlementRequestFuture(bankId: String, userId: String, roleName: String): Future[Box[EntitlementRequest]] =
    Future { addEntitlementRequest(bankId, userId, roleName) }

  override def getEntitlementRequest(bankId: String, userId: String, roleName: String): Box[EntitlementRequest] =
    findOne(fr"WHERE mbankid = ${nn(bankId)} AND muserid = ${nn(userId)} AND mrolename = ${nn(roleName)}")

  override def getEntitlementRequestFuture(entitlementRequestId: String): Future[Box[EntitlementRequest]] =
    Future { findOne(fr"WHERE mentitlementrequestid = ${nn(entitlementRequestId)}") }

  override def getEntitlementRequestFuture(bankId: String, userId: String, roleName: String): Future[Box[EntitlementRequest]] =
    Future { getEntitlementRequest(bankId, userId, roleName) }

  private def runList(where: Fragment): List[EntitlementRequest] =
    DoobieUtil.runQuery((selectCols ++ where).query[ReqRow].to[List]).map(DoobieEntitlementRequest(_))

  override def getEntitlementRequestsFuture(): Future[Box[List[EntitlementRequest]]] =
    Future { Full(runList(Fragment.empty)) }

  override def getEntitlementRequestsFuture(userId: String): Future[Box[List[EntitlementRequest]]] =
    Future { Full(runList(fr"WHERE muserid = ${nn(userId)}")) }

  // Builds the WHERE (date filters) / ORDER BY / LIMIT / OFFSET clauses from OBP query params,
  // optionally AND-ed with a userId filter — mirrors getOptionalParams in the Mapper provider.
  private def buildQuery(userId: Option[String], queryParams: List[OBPQueryParam]): Fragment = {
    val whereFrags: List[Fragment] =
      userId.map(u => fr"muserid = ${nn(u)}").toList :::
      queryParams.collect { case OBPFromDate(date) => fr"createdat >= ${new Timestamp(date.getTime)}" } :::
      queryParams.collect { case OBPToDate(date) => fr"createdat <= ${new Timestamp(date.getTime)}" }
    val whereClause =
      if (whereFrags.isEmpty) Fragment.empty
      else fr"WHERE" ++ whereFrags.reduceLeft((a, b) => a ++ fr"AND" ++ b)
    val orderClause = queryParams.collectFirst {
      case OBPOrdering(_, OBPAscending)  => fr"ORDER BY createdat ASC"
      case OBPOrdering(_, OBPDescending) => fr"ORDER BY createdat DESC"
    }.getOrElse(Fragment.empty)
    val limitClause = queryParams.collectFirst { case OBPLimit(value) => fr"LIMIT $value" }.getOrElse(Fragment.empty)
    val offsetClause = queryParams.collectFirst { case OBPOffset(value) => fr"OFFSET $value" }.getOrElse(Fragment.empty)
    whereClause ++ orderClause ++ limitClause ++ offsetClause
  }

  override def getEntitlementRequestsFuture(queryParams: List[OBPQueryParam]): Future[Box[List[EntitlementRequest]]] =
    Future { Full(runList(buildQuery(None, queryParams))) }

  override def getEntitlementRequestsFuture(userId: String, queryParams: List[OBPQueryParam]): Future[Box[List[EntitlementRequest]]] =
    Future { Full(runList(buildQuery(Some(userId), queryParams))) }

  override def deleteEntitlementRequestFuture(entitlementRequestId: String): Future[Box[Boolean]] =
    Future {
      findOne(fr"WHERE mentitlementrequestid = ${nn(entitlementRequestId)}") match {
        case Full(_) =>
          Full(DoobieUtil.runQuery(
            sql"DELETE FROM mappedentitlementrequest WHERE mentitlementrequestid = ${nn(entitlementRequestId)}".update.run) > 0)
        case Empty => Empty ?~! ErrorMessages.EntitlementRequestNotFound
        case _     => Full(false)
      }
    }
}
