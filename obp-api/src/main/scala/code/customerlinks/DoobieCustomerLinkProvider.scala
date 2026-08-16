package code.customerlinks

import code.api.util.{APIUtil, DoobieUtil, ErrorMessages}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import java.sql.Timestamp
import java.util.Date
import scala.collection.immutable.List
import scala.concurrent.Future

object DoobieCustomerLinkProvider extends CustomerLinkProvider {

  // Table "customerlink" (custom dbTableName). The trait exposes dateInserted/dateUpdated = createdat/updatedat.
  private case class ClRow(
    customerlinkid: Option[String],
    bankid: Option[String],
    customerid: Option[String],
    otherbankid: Option[String],
    othercustomerid: Option[String],
    relationshipto: Option[String],
    createdat: Option[Timestamp],
    updatedat: Option[Timestamp]
  )

  private case class DoobieCustomerLink(row: ClRow) extends CustomerLinkTrait {
    override def customerLinkId: String = row.customerlinkid.getOrElse("")
    override def bankId: String = row.bankid.getOrElse("")
    override def customerId: String = row.customerid.getOrElse("")
    override def otherBankId: String = row.otherbankid.getOrElse("")
    override def otherCustomerId: String = row.othercustomerid.getOrElse("")
    override def relationshipTo: String = row.relationshipto.getOrElse("")
    override def dateInserted: Date = row.createdat.orNull
    override def dateUpdated: Date = row.updatedat.orNull
  }

  private def nn(s: String): String = if (s == null) "" else s

  private val selectCols: Fragment =
    fr"""SELECT customerlinkid, bankid, customerid, otherbankid, othercustomerid, relationshipto, createdat, updatedat
         FROM customerlink"""

  private def findOne(where: Fragment): Box[DoobieCustomerLink] =
    DoobieUtil.runQuery((selectCols ++ where ++ fr"LIMIT 1").query[ClRow].option) match {
      case Some(r) => Full(DoobieCustomerLink(r))
      case None    => Empty
    }

  private def findList(where: Fragment): List[CustomerLinkTrait] =
    DoobieUtil.runQuery((selectCols ++ where).query[ClRow].to[List]).map(DoobieCustomerLink(_))

  override def createCustomerLink(bankId: String, customerId: String, otherBankId: String, otherCustomerId: String, relationshipTo: String): Box[CustomerLinkTrait] =
    tryo {
      val newId = APIUtil.generateUUID()
      val now = new Timestamp(System.currentTimeMillis())
      DoobieUtil.runQuery(
        sql"""INSERT INTO customerlink
                (customerlinkid, bankid, customerid, otherbankid, othercustomerid, relationshipto, createdat, updatedat)
              VALUES ($newId, ${nn(bankId)}, ${nn(customerId)}, ${nn(otherBankId)}, ${nn(otherCustomerId)}, ${nn(relationshipTo)}, $now, $now)""".update.run)
      DoobieCustomerLink(ClRow(Some(newId), Some(nn(bankId)), Some(nn(customerId)), Some(nn(otherBankId)),
        Some(nn(otherCustomerId)), Some(nn(relationshipTo)), Some(now), Some(now)))
    }

  override def getCustomerLinkById(customerLinkId: String): Box[CustomerLinkTrait] =
    findOne(fr"WHERE customerlinkid = ${nn(customerLinkId)}")

  override def getCustomerLinksByBankId(bankId: String): Box[List[CustomerLinkTrait]] =
    tryo { findList(fr"WHERE bankid = ${nn(bankId)}") }

  override def getCustomerLinksByCustomerId(customerId: String): Box[List[CustomerLinkTrait]] =
    tryo { findList(fr"WHERE customerid = ${nn(customerId)}") }

  override def updateCustomerLinkById(customerLinkId: String, relationshipTo: String): Box[CustomerLinkTrait] =
    findOne(fr"WHERE customerlinkid = ${nn(customerLinkId)}") match {
      case Full(link) =>
        val now = new Timestamp(System.currentTimeMillis())
        DoobieUtil.runQuery(
          sql"UPDATE customerlink SET relationshipto = ${nn(relationshipTo)}, updatedat = $now WHERE customerlinkid = ${nn(customerLinkId)}".update.run)
        Full(DoobieCustomerLink(link.row.copy(relationshipto = Some(nn(relationshipTo)), updatedat = Some(now))))
      case _ => Empty ?~! ErrorMessages.CustomerLinkNotFound
    }

  override def deleteCustomerLinkById(customerLinkId: String): Future[Box[Boolean]] =
    Future {
      findOne(fr"WHERE customerlinkid = ${nn(customerLinkId)}") match {
        case Full(_) =>
          Full(DoobieUtil.runQuery(
            sql"DELETE FROM customerlink WHERE customerlinkid = ${nn(customerLinkId)}".update.run) > 0)
        case _ => Empty ?~! ErrorMessages.CustomerLinkNotFound
      }
    }

  override def bulkDeleteCustomerLinks(): Boolean = {
    DoobieUtil.runQuery(sql"DELETE FROM customerlink".update.run)
    true
  }
}
