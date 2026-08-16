package code.taxresidence

import code.api.util.{DoobieUtil, ErrorMessages}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.TaxResidence
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import java.sql.Timestamp
import java.util.UUID
import scala.concurrent.Future

/**
 * Doobie implementation of the [[TaxResidenceProvider]] vend (Phase 4 of the Lift Mapper -> Doobie
 * migration). The `mappedtaxresidence` table is accessed via Doobie SQL through `DoobieUtil.runQuery`,
 * wrapped in a `Future` to satisfy the async trait (mirroring the Lift `Future { Mapper... }` shape).
 *
 * The `mcustomerid` column is a BIGINT foreign key to `mappedcustomer.id`, so callers pass the
 * customer's string `customerId`: writes resolve it to the customer PK first, and reads JOIN
 * `mappedcustomer` to project the string `customerId` back out (matching Lift's `mCustomerId.foreign`).
 *
 * Coexistence phase: only data access is migrated. `MappedTaxResidence` stays in
 * `Boot.scala`'s `ToSchemify.models` for schema and the test-isolation reset.
 */
object DoobieTaxResidenceProvider extends TaxResidenceProvider {

  private case class TaxResidenceRow(
    taxResidenceId: Option[String],
    domain: Option[String],
    taxNumber: Option[String],
    customerId: Option[String]
  )

  private case class DoobieTaxResidence(row: TaxResidenceRow) extends TaxResidence {
    override def customerId: String = row.customerId.getOrElse("")
    override def taxResidenceId: String = row.taxResidenceId.getOrElse("")
    override def domain: String = row.domain.getOrElse("")
    override def taxNumber: String = row.taxNumber.getOrElse("")
  }

  private def nn(s: String): String = if (s == null) "" else s
  private def now: Timestamp = new Timestamp(System.currentTimeMillis())

  private def findCustomerPk(customerId: String): Option[Long] =
    DoobieUtil.runQuery(
      sql"SELECT id FROM mappedcustomer WHERE mcustomerid = ${nn(customerId)} LIMIT 1".query[Long].option)

  override def getTaxResidence(customerId: String): Future[Box[List[TaxResidence]]] = Future {
    findCustomerPk(customerId) match {
      case Some(_) =>
        Full(DoobieUtil.runQuery(
          fr"""SELECT tr.mtaxresidenceid, tr.mdomain, tr.mtaxnumber, c.mcustomerid
               FROM mappedtaxresidence tr JOIN mappedcustomer c ON tr.mcustomerid = c.id
               WHERE c.mcustomerid = ${nn(customerId)}""".query[TaxResidenceRow].to[List])
          .map(DoobieTaxResidence(_)))
      case None => Empty
    }
  }

  override def createTaxResidence(customerId: String, domain: String, taxNumber: String): Future[Box[TaxResidence]] = Future {
    findCustomerPk(customerId) match {
      case Some(pk) =>
        tryo {
          val trId = UUID.randomUUID().toString
          DoobieUtil.runQuery(
            sql"""INSERT INTO mappedtaxresidence (mcustomerid, mtaxresidenceid, mdomain, mtaxnumber, createdat, updatedat)
                  VALUES ($pk, $trId, ${nn(domain)}, ${nn(taxNumber)}, $now, $now)""".update.run)
          DoobieTaxResidence(TaxResidenceRow(Some(trId), Some(nn(domain)), Some(nn(taxNumber)), Some(customerId)))
        }
      case None => Empty ?~! ErrorMessages.CustomerNotFoundByCustomerId
    }
  }

  override def deleteTaxResidence(taxResidenceId: String): Future[Box[Boolean]] = Future {
    DoobieUtil.runQuery(
      sql"SELECT id FROM mappedtaxresidence WHERE mtaxresidenceid = ${nn(taxResidenceId)} LIMIT 1".query[Long].option) match {
      case Some(_) =>
        Full(DoobieUtil.runQuery(
          sql"DELETE FROM mappedtaxresidence WHERE mtaxresidenceid = ${nn(taxResidenceId)}".update.run) > 0)
      case None => Empty ?~! ErrorMessages.TaxResidenceNotFound
    }
  }
}
