package code.customer.internalMapping

import code.api.util.DoobieUtil
import com.openbankproject.commons.model.CustomerId
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import java.sql.Timestamp

object DoobieCustomerIdMappingProvider extends CustomerIdMappingProvider {

  private case class CustIdMapRow(
    id: Option[Long],
    mcustomerplaintextreference: Option[String],
    mcustomerid: Option[String],
    createdat: Option[Timestamp],
    updatedat: Option[Timestamp]
  )

  private def nn(s: String): String = if (s == null) "" else s

  private val selectCols: Fragment =
    fr"SELECT id, mcustomerplaintextreference, mcustomerid, createdat, updatedat FROM mappedcustomeridmapping"

  override def getOrCreateCustomerId(customerPlainTextReference: String): Box[CustomerId] =
    tryo {
      val existing = DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE mcustomerplaintextreference = ${nn(customerPlainTextReference)} LIMIT 1")
          .query[CustIdMapRow].option)
      existing match {
        case Some(row) =>
          Full(row.mcustomerid.map(CustomerId(_)).getOrElse(CustomerId("")))
        case None =>
          val newCustomerId = java.util.UUID.randomUUID().toString
          val now = new Timestamp(System.currentTimeMillis())
          DoobieUtil.runQuery(
            sql"""INSERT INTO mappedcustomeridmapping (mcustomerplaintextreference, mcustomerid, createdat, updatedat)
                   VALUES (${nn(customerPlainTextReference)}, $newCustomerId, $now, $now)""".update.run)
          Full(CustomerId(newCustomerId))
      }
    }.getOrElse(Empty)

  override def getCustomerPlainTextReference(customerId: CustomerId): Box[String] =
    tryo {
      DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE mcustomerid = ${nn(customerId.value)} LIMIT 1")
          .query[CustIdMapRow].option) match {
        case Some(row) =>
          Full(row.mcustomerplaintextreference.getOrElse(""))
        case None =>
          Empty
      }
    }.getOrElse(Empty)
}
