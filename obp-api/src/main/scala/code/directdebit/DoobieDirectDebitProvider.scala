package code.directdebit

import code.api.util.{APIUtil, DoobieUtil}
import com.openbankproject.commons.model.DirectDebitTrait
import doobie._
import doobie.implicits._
import net.liftweb.common.Box
import net.liftweb.util.Helpers.tryo

import java.sql.Timestamp
import java.util.Date

/**
 * Doobie implementation of the [[DirectDebitProvider]] vend (Phase 6 of the Lift Mapper -> Doobie
 * migration). The `directdebit` table is accessed via Doobie SQL through `DoobieUtil.runQuery`
 * (joining the http4s request transaction when one is present). Synchronous, no foreign-key joins:
 * bank/account/customer/user/counterparty ids are all stored as strings.
 *
 * Coexistence phase: only data access is migrated. The `DirectDebit` entity stays in
 * `Boot.scala`'s `ToSchemify.models` for schema and the test-isolation reset.
 *
 * Nullable date columns are read as `Option[Timestamp]` and projected to `null` via `orNull`,
 * mirroring Lift MappedDateTime.get (a never-set DateCancelled/DateExpires returns null).
 */
object DoobieDirectDebitProvider extends DirectDebitProvider {

  private case class DirectDebitRow(
    directDebitId: Option[String],
    bankId: Option[String],
    accountId: Option[String],
    customerId: Option[String],
    userId: Option[String],
    counterpartyId: Option[String],
    dateSigned: Option[Timestamp],
    dateCancelled: Option[Timestamp],
    dateStarts: Option[Timestamp],
    dateExpires: Option[Timestamp],
    active: Option[Boolean]
  )

  private case class DoobieDirectDebit(row: DirectDebitRow) extends DirectDebitTrait {
    private def toDate(o: Option[Timestamp]): Date = o.map(t => new Date(t.getTime)).orNull
    override def directDebitId: String = row.directDebitId.getOrElse("")
    override def bankId: String = row.bankId.getOrElse("")
    override def accountId: String = row.accountId.getOrElse("")
    override def customerId: String = row.customerId.getOrElse("")
    override def userId: String = row.userId.getOrElse("")
    override def counterpartyId: String = row.counterpartyId.getOrElse("")
    override def dateSigned: Date = toDate(row.dateSigned)
    override def dateCancelled: Date = toDate(row.dateCancelled)
    override def dateStarts: Date = toDate(row.dateStarts)
    override def dateExpires: Date = toDate(row.dateExpires)
    override def active: Boolean = row.active.getOrElse(false)
  }

  private def nn(s: String): String = if (s == null) "" else s
  private def now: Timestamp = new Timestamp(System.currentTimeMillis())

  private val selectCols: Fragment =
    fr"""SELECT directdebitid, bankid, accountid, customerid, userid, counterpartyid,
         datesigned, datecancelled, datestarts, dateexpires, active FROM directdebit"""

  override def createDirectDebit(bankId: String, accountId: String, customerId: String, userId: String,
                                 counterpartyId: String, dateSigned: Date, dateStarts: Date,
                                 dateExpires: Option[Date]): Box[DirectDebitTrait] = tryo {
    val ddId = APIUtil.generateUUID()
    val signedTs = new Timestamp(dateSigned.getTime)
    val startsTs = new Timestamp(dateStarts.getTime)
    val expiresTs: Option[Timestamp] = dateExpires.map(d => new Timestamp(d.getTime))
    DoobieUtil.runQuery(
      sql"""INSERT INTO directdebit
            (directdebitid, bankid, accountid, customerid, userid, counterpartyid,
             datesigned, datestarts, dateexpires, active, createdat, updatedat)
            VALUES ($ddId, ${nn(bankId)}, ${nn(accountId)}, ${nn(customerId)}, ${nn(userId)},
             ${nn(counterpartyId)}, $signedTs, $startsTs, $expiresTs, ${true}, $now, $now)""".update.run)
    DoobieDirectDebit(DirectDebitRow(
      Some(ddId), Some(nn(bankId)), Some(nn(accountId)), Some(nn(customerId)), Some(nn(userId)),
      Some(nn(counterpartyId)), Some(signedTs), None, Some(startsTs), expiresTs, Some(true)))
  }

  override def getDirectDebitsByCustomer(customerId: String): List[DirectDebitTrait] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE customerid = ${nn(customerId)} ORDER BY updatedat DESC")
        .query[DirectDebitRow].to[List]).map(DoobieDirectDebit(_))

  override def getDirectDebitsByUser(userId: String): List[DirectDebitTrait] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE userid = ${nn(userId)} ORDER BY updatedat DESC")
        .query[DirectDebitRow].to[List]).map(DoobieDirectDebit(_))
}
