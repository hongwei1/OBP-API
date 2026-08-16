package code.transaction_types

import code.TransactionTypes.TransactionType.TransactionType
import code.TransactionTypes.TransactionTypeProvider
import code.api.util.{APIUtil, DoobieUtil, ErrorMessages}
import code.api.v2_0_0.TransactionTypeJsonV200
import com.openbankproject.commons.model.{AmountOfMoney, BankId, TransactionTypeId}
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Failure, Full}
import net.liftweb.util.Helpers.tryo

import java.sql.Timestamp

object DoobieTransactionTypeProvider extends TransactionTypeProvider {

  private case class Row(
    id: Long,
    transactionTypeId: String,
    bankId: String,
    shortCode: String,
    summary: String,
    description: String,
    currency: String,
    amount: Long
  )

  private val selectCols: Fragment =
    fr"""SELECT id, mtransactiontypeid, mbankid, mshortcode, msummary, mdescription,
                mcustomerfee_currency, mcustomerfee_amount
         FROM mappedtransactiontype"""

  private def toTT(row: Row): TransactionType =
    TransactionType(
      id          = TransactionTypeId(row.transactionTypeId),
      bankId      = BankId(row.bankId),
      shortCode   = row.shortCode,
      summary     = row.summary,
      description = row.description,
      charge      = AmountOfMoney(currency = row.currency, amount = row.amount.toString)
    )

  override protected def getTransactionTypeFromProvider(transactionTypeId: TransactionTypeId): Option[TransactionType] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE mtransactiontypeid = ${transactionTypeId.value} LIMIT 1").query[Row].option
    ).map(toTT)

  override protected def getTransactionTypesForBankFromProvider(bankId: BankId): Some[List[TransactionType]] =
    Some(
      DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE mbankid = ${bankId.value}").query[Row].to[List]
      ).map(toTT)
    )

  override def createOrUpdateTransactionTypeAtProvider(transactionType: TransactionTypeJsonV200): Box[TransactionType] = {
    val ttId       = transactionType.id.toString
    val bankId     = transactionType.bank_id
    val shortCode  = transactionType.short_code
    val summary    = transactionType.summary
    val desc       = transactionType.description
    val currency   = transactionType.charge.currency.toString
    val amount     = transactionType.charge.amount.toString.toLong
    val now        = new Timestamp(System.currentTimeMillis())

    getTransactionTypeFromProvider(transactionType.id) match {
      case Some(_) =>
        tryo {
          DoobieUtil.runQuery(
            sql"""UPDATE mappedtransactiontype
                  SET mshortcode = $shortCode, msummary = $summary, mdescription = $desc,
                      mcustomerfee_currency = $currency, mcustomerfee_amount = $amount,
                      updatedat = $now
                  WHERE mtransactiontypeid = $ttId""".update.run
          )
          toTT(Row(0L, ttId, bankId, shortCode, summary, desc, currency, amount))
        } ?~! ErrorMessages.CreateTransactionTypeUpdateError
      case None =>
        tryo {
          DoobieUtil.runQuery(
            sql"""INSERT INTO mappedtransactiontype
                    (mtransactiontypeid, mbankid, mshortcode, msummary, mdescription,
                     mcustomerfee_currency, mcustomerfee_amount, createdat, updatedat)
                  VALUES ($ttId, $bankId, $shortCode, $summary, $desc, $currency, $amount, $now, $now)
               """.update.run
          )
          toTT(Row(0L, ttId, bankId, shortCode, summary, desc, currency, amount))
        } ?~! ErrorMessages.CreateTransactionTypeInsertError
    }
  }
}
