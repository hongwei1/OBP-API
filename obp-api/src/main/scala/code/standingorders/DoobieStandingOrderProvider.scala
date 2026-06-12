package code.standingorders

import code.api.util.{APIUtil, DoobieUtil}
import code.util.Helper.{convertToSmallestCurrencyUnits, smallestCurrencyUnitToBigDecimal}
import com.openbankproject.commons.model.StandingOrderTrait
import doobie._
import doobie.implicits._
import net.liftweb.common.Box
import net.liftweb.util.Helpers.tryo

import java.sql.Timestamp
import java.util.Date

/**
 * Doobie implementation of the [[StandingOrderProvider]] vend (Phase 7 of the Lift Mapper -> Doobie
 * migration). The `standingorder` table is accessed via Doobie SQL through `DoobieUtil.runQuery`
 * (synchronous, no foreign-key joins — all ids stored as strings). The monetary amount is stored as a
 * Long in the smallest currency unit, converted in/out via code.util.Helper, exactly as the Lift entity.
 *
 * Two Lift quirks are preserved verbatim for behavioural parity:
 *  - the column/field is misspelt `couterpartyid` (missing an 'n');
 *  - `createStandingOrder` ignores its `whenDetail` argument (Lift's create never set WhenDetail),
 *    so the column is left NULL on insert.
 *
 * Coexistence phase: only data access is migrated. The `StandingOrder` entity stays in
 * `Boot.scala`'s `ToSchemify.models` for schema and the test-isolation reset.
 */
object DoobieStandingOrderProvider extends StandingOrderProvider {

  private case class StandingOrderRow(
    standingOrderId: Option[String],
    bankId: Option[String],
    accountId: Option[String],
    customerId: Option[String],
    userId: Option[String],
    counterpartyId: Option[String],
    amountValue: Option[Long],
    amountCurrency: Option[String],
    whenFrequency: Option[String],
    whenDetail: Option[String],
    dateSigned: Option[Timestamp],
    dateCancelled: Option[Timestamp],
    dateStarts: Option[Timestamp],
    dateExpires: Option[Timestamp],
    active: Option[Boolean]
  )

  private case class DoobieStandingOrder(row: StandingOrderRow) extends StandingOrderTrait {
    private def toDate(o: Option[Timestamp]): Date = o.map(t => new Date(t.getTime)).orNull
    override def standingOrderId: String = row.standingOrderId.getOrElse("")
    override def bankId: String = row.bankId.getOrElse("")
    override def accountId: String = row.accountId.getOrElse("")
    override def customerId: String = row.customerId.getOrElse("")
    override def userId: String = row.userId.getOrElse("")
    override def counterpartyId: String = row.counterpartyId.getOrElse("")
    override def amountValue: BigDecimal =
      smallestCurrencyUnitToBigDecimal(row.amountValue.getOrElse(0L), row.amountCurrency.getOrElse(""))
    override def amountCurrency: String = row.amountCurrency.getOrElse("")
    override def whenFrequency: String = row.whenFrequency.getOrElse("")
    override def whenDetail: String = row.whenDetail.getOrElse("")
    override def dateSigned: Date = toDate(row.dateSigned)
    override def dateCancelled: Date = toDate(row.dateCancelled)
    override def dateStarts: Date = toDate(row.dateStarts)
    override def dateExpires: Date = toDate(row.dateExpires)
    override def active: Boolean = row.active.getOrElse(false)
  }

  private def nn(s: String): String = if (s == null) "" else s
  private def now: Timestamp = new Timestamp(System.currentTimeMillis())

  private val selectCols: Fragment =
    fr"""SELECT standingorderid, bankid, accountid, customerid, userid, couterpartyid, amountvalue,
         amountcurrency, whenfrequency, whendetail, datesigned, datecancelled, datestarts, dateexpires,
         active FROM standingorder"""

  override def createStandingOrder(bankId: String, accountId: String, customerId: String, userId: String,
                                   counterpartyId: String, amountValue: BigDecimal, amountCurrency: String,
                                   whenFrequency: String, whenDetail: String, dateSigned: Date,
                                   dateStarts: Date, dateExpires: Option[Date]): Box[StandingOrderTrait] = tryo {
    val soId = APIUtil.generateUUID()
    val amountUnits = convertToSmallestCurrencyUnits(amountValue, amountCurrency)
    val signedTs = new Timestamp(dateSigned.getTime)
    val startsTs = new Timestamp(dateStarts.getTime)
    val expiresTs: Option[Timestamp] = dateExpires.map(d => new Timestamp(d.getTime))
    // Note: whenDetail is intentionally NOT inserted — Lift's createStandingOrder ignored it.
    DoobieUtil.runQuery(
      sql"""INSERT INTO standingorder
            (standingorderid, bankid, accountid, customerid, userid, couterpartyid, amountvalue,
             amountcurrency, whenfrequency, datesigned, datestarts, dateexpires, active, createdat, updatedat)
            VALUES ($soId, ${nn(bankId)}, ${nn(accountId)}, ${nn(customerId)}, ${nn(userId)},
             ${nn(counterpartyId)}, $amountUnits, ${nn(amountCurrency)}, ${nn(whenFrequency)},
             $signedTs, $startsTs, $expiresTs, ${true}, $now, $now)""".update.run)
    DoobieStandingOrder(StandingOrderRow(
      Some(soId), Some(nn(bankId)), Some(nn(accountId)), Some(nn(customerId)), Some(nn(userId)),
      Some(nn(counterpartyId)), Some(amountUnits), Some(nn(amountCurrency)), Some(nn(whenFrequency)),
      None, Some(signedTs), None, Some(startsTs), expiresTs, Some(true)))
  }

  override def getStandingOrdersByCustomer(customerId: String): List[StandingOrderTrait] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE customerid = ${nn(customerId)} ORDER BY updatedat DESC")
        .query[StandingOrderRow].to[List]).map(DoobieStandingOrder(_))

  override def getStandingOrdersByUser(userId: String): List[StandingOrderTrait] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE userid = ${nn(userId)} ORDER BY updatedat DESC")
        .query[StandingOrderRow].to[List]).map(DoobieStandingOrder(_))
}
