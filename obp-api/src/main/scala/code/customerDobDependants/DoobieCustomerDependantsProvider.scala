package code.CustomerDependants

import code.api.util.DoobieUtil
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.CustomerDependant
import doobie._
import doobie.implicits._

import java.sql.Timestamp
import java.util.Date
import scala.collection.immutable.List

/**
 * Doobie implementation of the [[CustomerDependants]] vend (Lift Mapper -> Doobie migration).
 * The `mappedcustomerdependant` table is accessed via Doobie SQL through `DoobieUtil.runQuery`.
 *
 * The trait returns the concrete `MappedCustomerDependant` (used in the customer `one-to-many` model),
 * so the Lift class is reused purely as an in-memory return holder: rows are constructed with
 * `MappedCustomerDependant.create.<field>(...)` and NEVER persisted via `.saveMe()` — all DB I/O is
 * done through Doobie. Callers only read `mDateOfBirth.get` (see `MappedCustomerProvider`), which the
 * holder exposes correctly.
 *
 * Coexistence phase: only data access is migrated. `MappedCustomerDependant` stays in
 * `Boot.scala`'s `ToSchemify.models` for schema and the test-isolation reset.
 */
object DoobieCustomerDependantsProvider extends CustomerDependants with MdcLoggable {

  // Columns: id, mcustomer (BIGINT FK -> mappedcustomer.id), mdateofbirth (TIMESTAMP)
  private case class DependantRow(
    mCustomer: Option[Long],
    mDateOfBirth: Option[Timestamp]
  )

  private def toEntity(row: DependantRow): MappedCustomerDependant = {
    val holder = MappedCustomerDependant.create
    row.mCustomer.foreach(pk => holder.mCustomer(pk))
    row.mDateOfBirth.foreach(ts => holder.mDateOfBirth(new Date(ts.getTime)))
    holder
  }

  private val selectCols: Fragment =
    fr"SELECT mcustomer, mdateofbirth FROM mappedcustomerdependant"

  override def createCustomerDependants(mapperCustomerPrimaryKey: Long,
                                        customerDependants: List[CustomerDependant]): List[MappedCustomerDependant] =
    customerDependants.map { customerDependant =>
      val dob = new Timestamp(customerDependant.dateOfBirth.getTime)
      DoobieUtil.runQuery(
        sql"""INSERT INTO mappedcustomerdependant (mcustomer, mdateofbirth)
              VALUES ($mapperCustomerPrimaryKey, $dob)""".update.run)
      toEntity(DependantRow(Some(mapperCustomerPrimaryKey), Some(dob)))
    }

  override def getCustomerDependantsByCustomerPrimaryKey(mapperCustomerPrimaryKey: Long): List[MappedCustomerDependant] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE mcustomer = $mapperCustomerPrimaryKey").query[DependantRow].to[List])
      .map(toEntity)

}
