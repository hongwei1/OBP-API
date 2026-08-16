package code.counterpartylimit

import code.api.util.{APIUtil, DoobieUtil}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.CounterpartyLimitTrait
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo
import org.json4s.{Formats, JValue}
import org.json4s.JsonDSL._

import java.sql.Timestamp
import scala.concurrent.Future

/**
 * Doobie implementation of the [[CounterpartyLimitProviderTrait]] vend (Phase 8 of the Lift Mapper ->
 * Doobie migration). The `counterpartylimit` table is accessed via Doobie SQL through
 * `DoobieUtil.runQuery`, wrapped in `Future` to satisfy the async trait. A row is keyed by the
 * (bankId, accountId, viewId, counterpartyId) tuple; `createOrUpdateCounterpartyLimit` is an upsert.
 *
 * Amounts are DECIMAL columns mapped directly to BigDecimal (no smallest-unit conversion).
 * Defaults mirror the Lift entity: amounts default to 0, transaction counts to -1.
 *
 * Coexistence phase: only data access is migrated. The `CounterpartyLimit` entity stays in
 * `Boot.scala`'s `ToSchemify.models` for schema and the test-isolation reset.
 */
object DoobieCounterpartyLimitProvider extends CounterpartyLimitProviderTrait {

  private case class CounterpartyLimitRow(
    counterpartyLimitId: Option[String],
    bankId: Option[String],
    accountId: Option[String],
    viewId: Option[String],
    counterpartyId: Option[String],
    currency: Option[String],
    maxSingleAmount: Option[BigDecimal],
    maxMonthlyAmount: Option[BigDecimal],
    maxNumberOfMonthlyTransactions: Option[Int],
    maxYearlyAmount: Option[BigDecimal],
    maxNumberOfYearlyTransactions: Option[Int],
    maxTotalAmount: Option[BigDecimal],
    maxNumberOfTransactions: Option[Int]
  )

  private case class DoobieCounterpartyLimit(row: CounterpartyLimitRow) extends CounterpartyLimitTrait {
    override def counterpartyLimitId: String = row.counterpartyLimitId.getOrElse("")
    override def bankId: String = row.bankId.getOrElse("")
    override def accountId: String = row.accountId.getOrElse("")
    override def viewId: String = row.viewId.getOrElse("")
    override def counterpartyId: String = row.counterpartyId.getOrElse("")
    override def currency: String = row.currency.getOrElse("")
    override def maxSingleAmount: BigDecimal = row.maxSingleAmount.getOrElse(BigDecimal(0))
    override def maxMonthlyAmount: BigDecimal = row.maxMonthlyAmount.getOrElse(BigDecimal(0))
    override def maxNumberOfMonthlyTransactions: Int = row.maxNumberOfMonthlyTransactions.getOrElse(-1)
    override def maxYearlyAmount: BigDecimal = row.maxYearlyAmount.getOrElse(BigDecimal(0))
    override def maxNumberOfYearlyTransactions: Int = row.maxNumberOfYearlyTransactions.getOrElse(-1)
    override def maxTotalAmount: BigDecimal = row.maxTotalAmount.getOrElse(BigDecimal(0))
    override def maxNumberOfTransactions: Int = row.maxNumberOfTransactions.getOrElse(-1)
    override def toJValue(implicit format: Formats): JValue =
      ("counterparty_limit_id", counterpartyLimitId) ~
        ("bank_id", bankId) ~
        ("account_id", accountId) ~
        ("view_id", viewId) ~
        ("counterparty_id", counterpartyId) ~
        ("currency", currency) ~
        ("max_single_amount", maxSingleAmount) ~
        ("max_monthly_amount", maxMonthlyAmount) ~
        ("max_number_of_monthly_transactions", maxNumberOfMonthlyTransactions) ~
        ("max_yearly_amount", maxYearlyAmount) ~
        ("max_number_of_yearly_transactions", maxNumberOfYearlyTransactions) ~
        ("max_total_amount", maxTotalAmount) ~
        ("max_number_of_transactions", maxNumberOfTransactions)
  }

  private def nn(s: String): String = if (s == null) "" else s
  private def now: Timestamp = new Timestamp(System.currentTimeMillis())

  private val selectCols: Fragment =
    fr"""SELECT counterpartylimitid, bankid, accountid, viewid, counterpartyid, currency,
         maxsingleamount, maxmonthlyamount, maxnumberofmonthlytransactions, maxyearlyamount,
         maxnumberofyearlytransactions, maxtotalamount, maxnumberoftransactions FROM counterpartylimit"""

  private def findByKeys(bankId: String, accountId: String, viewId: String, counterpartyId: String): Option[CounterpartyLimitRow] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"""WHERE bankid = ${nn(bankId)} AND accountid = ${nn(accountId)}
              AND viewid = ${nn(viewId)} AND counterpartyid = ${nn(counterpartyId)} LIMIT 1""")
        .query[CounterpartyLimitRow].option)

  override def getCounterpartyLimit(bankId: String, accountId: String, viewId: String, counterpartyId: String): Future[Box[CounterpartyLimitTrait]] = Future {
    findByKeys(bankId, accountId, viewId, counterpartyId) match {
      case Some(row) => Full(DoobieCounterpartyLimit(row))
      case None => Empty
    }
  }

  override def deleteCounterpartyLimit(bankId: String, accountId: String, viewId: String, counterpartyId: String): Future[Box[Boolean]] = Future {
    findByKeys(bankId, accountId, viewId, counterpartyId) match {
      case Some(_) =>
        Full(DoobieUtil.runQuery(
          sql"""DELETE FROM counterpartylimit WHERE bankid = ${nn(bankId)} AND accountid = ${nn(accountId)}
                AND viewid = ${nn(viewId)} AND counterpartyid = ${nn(counterpartyId)}""".update.run) > 0)
      case None => Empty
    }
  }

  override def createOrUpdateCounterpartyLimit(bankId: String, accountId: String, viewId: String,
                                               counterpartyId: String, currency: String,
                                               maxSingleAmount: BigDecimal, maxMonthlyAmount: BigDecimal,
                                               maxNumberOfMonthlyTransactions: Int, maxYearlyAmount: BigDecimal,
                                               maxNumberOfYearlyTransactions: Int, maxTotalAmount: BigDecimal,
                                               maxNumberOfTransactions: Int): Future[Box[CounterpartyLimitTrait]] = Future {
    tryo {
      findByKeys(bankId, accountId, viewId, counterpartyId) match {
        case Some(_) =>
          DoobieUtil.runQuery(
            sql"""UPDATE counterpartylimit
                  SET currency = ${nn(currency)}, maxsingleamount = $maxSingleAmount,
                      maxmonthlyamount = $maxMonthlyAmount, maxnumberofmonthlytransactions = $maxNumberOfMonthlyTransactions,
                      maxyearlyamount = $maxYearlyAmount, maxnumberofyearlytransactions = $maxNumberOfYearlyTransactions,
                      maxtotalamount = $maxTotalAmount, maxnumberoftransactions = $maxNumberOfTransactions, updatedat = $now
                  WHERE bankid = ${nn(bankId)} AND accountid = ${nn(accountId)}
                    AND viewid = ${nn(viewId)} AND counterpartyid = ${nn(counterpartyId)}""".update.run)
        case None =>
          val id = APIUtil.generateUUID()
          DoobieUtil.runQuery(
            sql"""INSERT INTO counterpartylimit
                  (counterpartylimitid, bankid, accountid, viewid, counterpartyid, currency,
                   maxsingleamount, maxmonthlyamount, maxnumberofmonthlytransactions, maxyearlyamount,
                   maxnumberofyearlytransactions, maxtotalamount, maxnumberoftransactions, createdat, updatedat)
                  VALUES ($id, ${nn(bankId)}, ${nn(accountId)}, ${nn(viewId)}, ${nn(counterpartyId)}, ${nn(currency)},
                   $maxSingleAmount, $maxMonthlyAmount, $maxNumberOfMonthlyTransactions, $maxYearlyAmount,
                   $maxNumberOfYearlyTransactions, $maxTotalAmount, $maxNumberOfTransactions, $now, $now)""".update.run)
      }
      findByKeys(bankId, accountId, viewId, counterpartyId).map(DoobieCounterpartyLimit(_))
        .getOrElse(throw new RuntimeException("counterparty limit not found after upsert"))
    }
  }
}
