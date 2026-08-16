package code.customeraccountlinks

import code.api.util.{APIUtil, DoobieUtil, ErrorMessages}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.CustomerAccountLinkTrait
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import java.sql.Timestamp
import scala.collection.immutable.List
import scala.concurrent.Future

object DoobieCustomerAccountLinkProvider extends CustomerAccountLinkProvider {

  // Table "customeraccountlink": id, accountid, customerid, bankid, customeraccountlinkid, relationshiptype, createdat, updatedat.
  private case class CalRow(
    customeraccountlinkid: Option[String],
    customerid: Option[String],
    bankid: Option[String],
    accountid: Option[String],
    relationshiptype: Option[String]
  )

  private case class DoobieCustomerAccountLink(row: CalRow) extends CustomerAccountLinkTrait {
    override def customerAccountLinkId: String = row.customeraccountlinkid.getOrElse("")
    override def customerId: String = row.customerid.getOrElse("")
    override def bankId: String = row.bankid.getOrElse("")
    override def accountId: String = row.accountid.getOrElse("")
    override def relationshipType: String = row.relationshiptype.getOrElse("")
  }

  private def nn(s: String): String = if (s == null) "" else s

  private val selectCols: Fragment =
    fr"SELECT customeraccountlinkid, customerid, bankid, accountid, relationshiptype FROM customeraccountlink"

  private def findOne(where: Fragment): Box[DoobieCustomerAccountLink] =
    DoobieUtil.runQuery((selectCols ++ where ++ fr"LIMIT 1").query[CalRow].option) match {
      case Some(r) => Full(DoobieCustomerAccountLink(r))
      case None    => Empty
    }

  private def findList(where: Fragment): List[CustomerAccountLinkTrait] =
    DoobieUtil.runQuery((selectCols ++ where).query[CalRow].to[List]).map(DoobieCustomerAccountLink(_))

  private def insertLink(customerId: String, bankId: String, accountId: String, relationshipType: String): DoobieCustomerAccountLink = {
    val newId = APIUtil.generateUUID()
    val now = new Timestamp(System.currentTimeMillis())
    DoobieUtil.runQuery(
      sql"""INSERT INTO customeraccountlink
              (customeraccountlinkid, customerid, bankid, accountid, relationshiptype, createdat, updatedat)
            VALUES ($newId, ${nn(customerId)}, ${nn(bankId)}, ${nn(accountId)}, ${nn(relationshipType)}, $now, $now)""".update.run)
    DoobieCustomerAccountLink(CalRow(Some(newId), Some(nn(customerId)), Some(nn(bankId)), Some(nn(accountId)), Some(nn(relationshipType))))
  }

  override def createCustomerAccountLink(customerId: String, bankId: String, accountId: String, relationshipType: String): Box[CustomerAccountLinkTrait] =
    tryo { insertLink(customerId, bankId, accountId, relationshipType) }

  override def getOrCreateCustomerAccountLink(customerId: String, bankId: String, accountId: String, relationshipType: String): Box[CustomerAccountLinkTrait] =
    findOne(fr"WHERE customerid = ${nn(customerId)} AND bankid = ${nn(bankId)} AND accountid = ${nn(accountId)}") match {
      case Empty          => Some(insertLink(customerId, bankId, accountId, relationshipType))
      case everythingElse => everythingElse
    }

  override def getCustomerAccountLinkByCustomerId(customerId: String): Box[CustomerAccountLinkTrait] =
    findOne(fr"WHERE customerid = ${nn(customerId)}")

  override def getCustomerAccountLinksByBankIdAccountId(bankId: String, accountId: String): Box[List[CustomerAccountLinkTrait]] =
    tryo { findList(fr"WHERE bankid = ${nn(bankId)} AND accountid = ${nn(accountId)}") }

  override def getCustomerAccountLinksByCustomerId(customerId: String): Box[List[CustomerAccountLinkTrait]] =
    tryo { findList(fr"WHERE customerid = ${nn(customerId)}") }

  override def getCustomerAccountLinksByAccountId(bankId: String, accountId: String): Box[List[CustomerAccountLinkTrait]] =
    tryo { findList(fr"WHERE bankid = ${nn(bankId)} AND accountid = ${nn(accountId)}") }

  override def getCustomerAccountLinkById(customerAccountLinkId: String): Box[CustomerAccountLinkTrait] =
    findOne(fr"WHERE customeraccountlinkid = ${nn(customerAccountLinkId)}")

  override def updateCustomerAccountLinkById(customerAccountLinkId: String, relationshipType: String): Box[CustomerAccountLinkTrait] =
    findOne(fr"WHERE customeraccountlinkid = ${nn(customerAccountLinkId)}") match {
      case Full(link) =>
        DoobieUtil.runQuery(
          sql"UPDATE customeraccountlink SET relationshiptype = ${nn(relationshipType)} WHERE customeraccountlinkid = ${nn(customerAccountLinkId)}".update.run)
        Full(DoobieCustomerAccountLink(link.row.copy(relationshiptype = Some(nn(relationshipType)))))
      case _ => Empty ?~! ErrorMessages.CustomerAccountLinkNotFound
    }

  override def getCustomerAccountLinks: Box[List[CustomerAccountLinkTrait]] =
    tryo { findList(Fragment.empty) }

  override def bulkDeleteCustomerAccountLinks(): Boolean = {
    DoobieUtil.runQuery(sql"DELETE FROM customeraccountlink".update.run)
    true
  }

  override def deleteCustomerAccountLinkById(customerAccountLinkId: String): Future[Box[Boolean]] =
    Future {
      findOne(fr"WHERE customeraccountlinkid = ${nn(customerAccountLinkId)}") match {
        case Full(_) =>
          Full(DoobieUtil.runQuery(
            sql"DELETE FROM customeraccountlink WHERE customeraccountlinkid = ${nn(customerAccountLinkId)}".update.run) > 0)
        case _ => Empty ?~! ErrorMessages.CustomerAccountLinkNotFound
      }
    }
}
