package code.bankaccountbalance

import code.api.util.{APIUtil, DoobieUtil}
import code.model.dataAccess.MappedBankAccount
import code.util.Helper
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.{AccountId, BalanceId, BankId}
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.mapper.By
import net.liftweb.util.Helpers.tryo

import java.sql.{Date => SqlDate}
import scala.concurrent.Future

/**
 * Doobie-backed provider for the `bankaccountbalance` table.
 *
 * Mirrors [[MappedBankAccountBalanceProvider]] exactly: same trait, same
 * null/empty handling, same currency conversion behaviour. The trait returns
 * the concrete Lift [[BankAccountBalance]] entity, so reads construct an
 * in-memory `BankAccountBalance` via the Mapper builder (never saved). The
 * `balanceAmount` accessor on that entity re-reads `MappedBankAccount` for the
 * currency exactly as a persisted entity would, so accessor behaviour is
 * identical.
 *
 * During coexistence the Mapped entity + provider stay in place for table
 * creation and test reset; both paths read/write the same table.
 */
object DoobieBankAccountBalanceProvider extends BankAccountBalanceProviderTrait {

  private case class BalanceRow(
    bankid_ : Option[String],
    accountid_ : Option[String],
    balanceid_ : Option[String],
    balancetype: Option[String],
    balanceamount: Option[Long],
    referencedate: Option[SqlDate]
  )

  private def nn(s: String): String = if (s == null) "" else s

  /**
   * Build an in-memory [[BankAccountBalance]] from a row read via Doobie.
   * The object is NEVER saved/deleted — it is only used to satisfy the trait's
   * concrete return type. Setting the raw field values reproduces every
   * accessor on the Mapped entity verbatim (including the currency lookup in
   * `balanceAmount`).
   */
  private def toEntity(row: BalanceRow): BankAccountBalance = {
    val entity = BankAccountBalance.create
      .BankId_(row.bankid_.getOrElse(""))
      .AccountId_(row.accountid_.getOrElse(""))
      .BalanceId_(row.balanceid_.getOrElse(""))
      .BalanceType(row.balancetype.getOrElse(""))
      .BalanceAmount(row.balanceamount.getOrElse(0L))
    row.referencedate.foreach(d => entity.ReferenceDate(d))
    entity
  }

  private val selectCols: Fragment =
    fr"SELECT bankid_, accountid_, balanceid_, balancetype, balanceamount, referencedate FROM bankaccountbalance"

  override def getBankAccountBalances(accountId: AccountId): Future[Box[List[BankAccountBalance]]] = Future {
    tryo {
      DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE accountid_ = ${nn(accountId.value)}")
          .query[BalanceRow].to[List]).map(toEntity)
    }
  }

  override def getBankAccountsBalances(accountIds: List[AccountId]): Future[Box[List[BankAccountBalance]]] = Future {
    tryo {
      if (accountIds.isEmpty) {
        List.empty[BankAccountBalance]
      } else {
        val inList = accountIds.map(a => fr"${nn(a.value)}").reduceLeft((x, y) => x ++ fr"," ++ y)
        DoobieUtil.runQuery(
          (selectCols ++ fr"WHERE accountid_ IN (" ++ inList ++ fr")")
            .query[BalanceRow].to[List]).map(toEntity)
      }
    }
  }

  override def getBankAccountBalanceById(balanceId: BalanceId): Future[Box[BankAccountBalance]] = Future {
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE balanceid_ = ${nn(balanceId.value)} LIMIT 1")
        .query[BalanceRow].option) match {
      case Some(row) => Full(toEntity(row))
      case None      => Empty
    }
  }

  override def createOrUpdateBankAccountBalance(
    bankId: BankId,
    accountId: AccountId,
    balanceId: Option[BalanceId],
    balanceType: String,
    balanceAmount: BigDecimal
  ): Future[Box[BankAccountBalance]] = Future {
    // Get the MappedBankAccount for the given account ID (currency source) — mirror the Mapped provider exactly.
    val mappedBankAccount = code.model.dataAccess.MappedBankAccount
      .find(
        By(MappedBankAccount.theAccountId, accountId.value)
      )

    mappedBankAccount match {
      case Full(account) =>
        val amountUnits = Helper.convertToSmallestCurrencyUnits(balanceAmount, account.currency)
        balanceId match {
          case Some(id) =>
            DoobieUtil.runQuery(
              (selectCols ++ fr"WHERE balanceid_ = ${nn(id.value)} LIMIT 1")
                .query[BalanceRow].option) match {
              case Some(existing) =>
                tryo {
                  DoobieUtil.runQuery(
                    sql"""UPDATE bankaccountbalance
                          SET bankid_ = ${nn(bankId.value)}, accountid_ = ${nn(accountId.value)},
                              balancetype = ${nn(balanceType)}, balanceamount = $amountUnits,
                              updatedat = ${new java.sql.Timestamp(System.currentTimeMillis())}
                          WHERE balanceid_ = ${nn(id.value)}""".update.run)
                  toEntity(existing.copy(
                    bankid_ = Some(nn(bankId.value)),
                    accountid_ = Some(nn(accountId.value)),
                    balancetype = Some(nn(balanceType)),
                    balanceamount = Some(amountUnits)
                  ))
                }
              case None => Empty
            }
          case _ =>
            tryo {
              val newId = APIUtil.generateUUID()
              val now = new java.sql.Timestamp(System.currentTimeMillis())
              DoobieUtil.runQuery(
                sql"""INSERT INTO bankaccountbalance
                        (bankid_, accountid_, balanceid_, balancetype, balanceamount, createdat, updatedat)
                      VALUES (${nn(bankId.value)}, ${nn(accountId.value)}, $newId,
                              ${nn(balanceType)}, $amountUnits, $now, $now)""".update.run)
              toEntity(BalanceRow(
                Some(nn(bankId.value)), Some(nn(accountId.value)), Some(newId),
                Some(nn(balanceType)), Some(amountUnits), None
              ))
            }
        }
      case _ => Empty
    }
  }

  override def deleteBankAccountBalance(balanceId: BalanceId): Future[Box[Boolean]] = Future {
    // Mirror the Mapped provider: only "succeed" (with true) when a row exists; otherwise Empty.
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE balanceid_ = ${nn(balanceId.value)} LIMIT 1")
        .query[BalanceRow].option) match {
      case Some(_) =>
        Full(DoobieUtil.runQuery(
          sql"DELETE FROM bankaccountbalance WHERE balanceid_ = ${nn(balanceId.value)}".update.run) >= 0)
      case None => Empty
    }
  }

}
