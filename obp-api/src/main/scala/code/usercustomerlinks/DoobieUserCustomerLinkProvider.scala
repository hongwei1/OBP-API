package code.usercustomerlinks

import code.api.util.{APIUtil, DoobieUtil, ErrorMessages}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}

import java.sql.Timestamp
import java.util.Date
import scala.collection.immutable.List
import scala.concurrent.Future

object DoobieUserCustomerLinkProvider extends UserCustomerLinkProvider {

  // Table "mappedusercustomerlink": id, musercustomerlinkid, mdateinserted, muserid, misactive, createdat, updatedat, mcustomerid.
  private case class UclRow(
    musercustomerlinkid: Option[String],
    muserid: Option[String],
    mcustomerid: Option[String],
    mdateinserted: Option[Timestamp],
    misactive: Option[Boolean]
  )

  private case class DoobieUserCustomerLink(row: UclRow) extends UserCustomerLink {
    override def userCustomerLinkId: String = row.musercustomerlinkid.getOrElse("")
    override def userId: String = row.muserid.getOrElse("")
    override def customerId: String = row.mcustomerid.getOrElse("")
    override def dateInserted: Date = row.mdateinserted.orNull
    override def isActive: Boolean = row.misactive.getOrElse(false)
  }

  private def nn(s: String): String = if (s == null) "" else s

  private val selectCols: Fragment =
    fr"SELECT musercustomerlinkid, muserid, mcustomerid, mdateinserted, misactive FROM mappedusercustomerlink"

  private def findOne(where: Fragment): Box[DoobieUserCustomerLink] =
    DoobieUtil.runQuery((selectCols ++ where ++ fr"LIMIT 1").query[UclRow].option) match {
      case Some(r) => Full(DoobieUserCustomerLink(r))
      case None    => Empty
    }

  private def findList(where: Fragment): List[UserCustomerLink] =
    DoobieUtil.runQuery((selectCols ++ where).query[UclRow].to[List]).map(DoobieUserCustomerLink(_))

  // The Mapper ignores the passed dateInserted and stores `new Date()` — preserve that.
  private def insertLink(userId: String, customerId: String, isActive: Boolean): Box[UserCustomerLink] = {
    val newId = APIUtil.generateUUID()
    val now = new Timestamp(System.currentTimeMillis())
    DoobieUtil.runQuery(
      sql"""INSERT INTO mappedusercustomerlink
              (musercustomerlinkid, muserid, mcustomerid, mdateinserted, misactive, createdat, updatedat)
            VALUES ($newId, ${nn(userId)}, ${nn(customerId)}, $now, $isActive, $now, $now)""".update.run)
    // Re-read so the returned object is field-identical to a subsequent find (stable equality).
    findOne(fr"WHERE musercustomerlinkid = $newId")
  }

  override def createUserCustomerLink(userId: String, customerId: String, dateInserted: Date, isActive: Boolean): Box[UserCustomerLink] =
    insertLink(userId, customerId, isActive)

  override def getOCreateUserCustomerLink(userId: String, customerId: String, dateInserted: Date, isActive: Boolean): Box[UserCustomerLink] =
    getUserCustomerLink(userId, customerId) match {
      case Empty          => insertLink(userId, customerId, isActive)
      case everythingElse => everythingElse
    }

  override def getUserCustomerLinkByCustomerId(customerId: String): Box[UserCustomerLink] =
    findOne(fr"WHERE mcustomerid = ${nn(customerId)}")

  override def getUserCustomerLinksByCustomerId(customerId: String): List[UserCustomerLink] =
    findList(fr"WHERE mcustomerid = ${nn(customerId)}")

  override def getUserCustomerLinksByUserId(userId: String): List[UserCustomerLink] =
    findList(fr"WHERE muserid = ${nn(userId)} ORDER BY id ASC")

  override def getUserCustomerLink(userId: String, customerId: String): Box[UserCustomerLink] =
    findOne(fr"WHERE muserid = ${nn(userId)} AND mcustomerid = ${nn(customerId)}")

  override def getUserCustomerLinks: Box[List[UserCustomerLink]] =
    Full(findList(Fragment.empty))

  override def bulkDeleteUserCustomerLinks(): Boolean = {
    DoobieUtil.runQuery(sql"DELETE FROM mappedusercustomerlink".update.run)
    true
  }

  override def deleteUserCustomerLink(userCustomerLinkId: String): Future[Box[Boolean]] =
    Future {
      findOne(fr"WHERE musercustomerlinkid = ${nn(userCustomerLinkId)}") match {
        case Full(_) =>
          Full(DoobieUtil.runQuery(
            sql"DELETE FROM mappedusercustomerlink WHERE musercustomerlinkid = ${nn(userCustomerLinkId)}".update.run) > 0)
        case _ => Empty ?~! ErrorMessages.UserCustomerLinkNotFound
      }
    }
}
