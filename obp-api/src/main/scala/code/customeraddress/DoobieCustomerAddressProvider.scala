package code.customeraddress

import code.api.util.{DoobieUtil, ErrorMessages}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.CustomerAddress
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import java.sql.Timestamp
import java.util.{Date, UUID}
import scala.concurrent.Future

/**
 * Doobie implementation of the [[CustomerAddressProvider]] vend (Phase 5 of the Lift Mapper -> Doobie
 * migration). The `mappedcustomeraddress` table is accessed via Doobie SQL through
 * `DoobieUtil.runQuery`, wrapped in `Future` to satisfy the async trait. The `mcustomerid` BIGINT
 * foreign key is resolved against `mappedcustomer` exactly as in [[code.taxresidence]]: writes look up
 * the customer PK by the string customerId; reads JOIN `mappedcustomer` to project customerId back out.
 *
 * Coexistence phase: only data access is migrated. `MappedCustomerAddress` stays in
 * `Boot.scala`'s `ToSchemify.models` for schema and the test-isolation reset.
 */
object DoobieCustomerAddressProvider extends CustomerAddressProvider {

  private case class AddressRow(
    customerAddressId: Option[String],
    line1: Option[String],
    line2: Option[String],
    line3: Option[String],
    city: Option[String],
    county: Option[String],
    state: Option[String],
    postcode: Option[String],
    countryCode: Option[String],
    status: Option[String],
    tags: Option[String],
    createdAt: Option[Timestamp],
    customerId: Option[String]
  )

  private case class DoobieCustomerAddress(row: AddressRow) extends CustomerAddress {
    override def customerId: String = row.customerId.getOrElse("")
    override def customerAddressId: String = row.customerAddressId.getOrElse("")
    override def line1: String = row.line1.getOrElse("")
    override def line2: String = row.line2.getOrElse("")
    override def line3: String = row.line3.getOrElse("")
    override def city: String = row.city.getOrElse("")
    override def county: String = row.county.getOrElse("")
    override def state: String = row.state.getOrElse("")
    override def postcode: String = row.postcode.getOrElse("")
    override def countryCode: String = row.countryCode.getOrElse("")
    // Lift's MappedCustomerAddress.status reads mState (NOT mStatus) — a long-standing copy-paste
    // bug. Preserve it verbatim so behaviour (and any test asserting on it) matches Lift.
    override def status: String = row.state.getOrElse("")
    override def tags: String = row.tags.getOrElse("")
    override def insertDate: Date = row.createdAt.map(t => new Date(t.getTime)).getOrElse(new Date(0L))
  }

  private def nn(s: String): String = if (s == null) "" else s
  private def now: Timestamp = new Timestamp(System.currentTimeMillis())

  private def findCustomerPk(customerId: String): Option[Long] =
    DoobieUtil.runQuery(
      sql"SELECT id FROM mappedcustomer WHERE mcustomerid = ${nn(customerId)} LIMIT 1".query[Long].option)

  private val selectJoin: Fragment =
    fr"""SELECT a.mcustomeraddressid, a.mline1, a.mline2, a.mline3, a.mcity, a.mcounty, a.mstate,
         a.mpostcode, a.mcountrycode, a.mstatus, a.mtags, a.createdat, c.mcustomerid
         FROM mappedcustomeraddress a JOIN mappedcustomer c ON a.mcustomerid = c.id"""

  private def findByAddressId(addressId: String): Option[AddressRow] =
    DoobieUtil.runQuery(
      (selectJoin ++ fr"WHERE a.mcustomeraddressid = ${nn(addressId)} LIMIT 1").query[AddressRow].option)

  override def getAddress(customerId: String): Future[Box[List[CustomerAddress]]] = Future {
    findCustomerPk(customerId) match {
      case Some(_) =>
        Full(DoobieUtil.runQuery(
          (selectJoin ++ fr"WHERE c.mcustomerid = ${nn(customerId)}").query[AddressRow].to[List])
          .map(DoobieCustomerAddress(_)))
      case None => Empty
    }
  }

  override def createAddress(customerId: String, line1: String, line2: String, line3: String,
                             city: String, county: String, state: String, postcode: String,
                             countryCode: String, tags: String, status: String): Future[Box[CustomerAddress]] = Future {
    findCustomerPk(customerId) match {
      case Some(pk) =>
        tryo {
          val addressId = UUID.randomUUID().toString
          DoobieUtil.runQuery(
            sql"""INSERT INTO mappedcustomeraddress
                  (mcustomerid, mcustomeraddressid, mline1, mline2, mline3, mcity, mcounty, mstate,
                   mpostcode, mcountrycode, mtags, mstatus, createdat, updatedat)
                  VALUES ($pk, $addressId, ${nn(line1)}, ${nn(line2)}, ${nn(line3)}, ${nn(city)},
                   ${nn(county)}, ${nn(state)}, ${nn(postcode)}, ${nn(countryCode)}, ${nn(tags)},
                   ${nn(status)}, $now, $now)""".update.run)
          findByAddressId(addressId).map(DoobieCustomerAddress(_))
            .getOrElse(throw new RuntimeException("created customer address not found"))
        }
      case None => Empty ?~! ErrorMessages.CustomerNotFoundByCustomerId
    }
  }

  override def updateAddress(customerAddressId: String, line1: String, line2: String, line3: String,
                             city: String, county: String, state: String, postcode: String,
                             countryCode: String, tags: String, status: String): Future[Box[CustomerAddress]] = Future {
    findByAddressId(customerAddressId) match {
      case Some(_) =>
        tryo {
          DoobieUtil.runQuery(
            sql"""UPDATE mappedcustomeraddress
                  SET mline1 = ${nn(line1)}, mline2 = ${nn(line2)}, mline3 = ${nn(line3)}, mcity = ${nn(city)},
                      mcounty = ${nn(county)}, mstate = ${nn(state)}, mpostcode = ${nn(postcode)},
                      mcountrycode = ${nn(countryCode)}, mtags = ${nn(tags)}, mstatus = ${nn(status)}, updatedat = $now
                  WHERE mcustomeraddressid = ${nn(customerAddressId)}""".update.run)
          findByAddressId(customerAddressId).map(DoobieCustomerAddress(_))
            .getOrElse(throw new RuntimeException("updated customer address not found"))
        }
      case None => Empty ?~! ErrorMessages.CustomerAddressNotFound
    }
  }

  override def deleteAddress(customerAddressId: String): Future[Box[Boolean]] = Future {
    findByAddressId(customerAddressId) match {
      case Some(_) =>
        Full(DoobieUtil.runQuery(
          sql"DELETE FROM mappedcustomeraddress WHERE mcustomeraddressid = ${nn(customerAddressId)}".update.run) > 0)
      case None => Empty ?~! ErrorMessages.CustomerAddressNotFound
    }
  }
}
