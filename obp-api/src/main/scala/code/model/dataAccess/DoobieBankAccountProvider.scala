package code.model.dataAccess

import code.api.util.{APIUtil, DoobieUtil}
import code.util.Helper
import com.openbankproject.commons.model._
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.mapper.By

import java.sql.Timestamp
import java.util.Date
import scala.collection.immutable.List

/**
 * Doobie-backed data access for the `mappedbankaccount` table, replacing the Lift
 * Mapper queries that previously lived inline in
 * [[code.bankconnectors.LocalMappedConnector]].
 *
 * The MappedBankAccount entity is intentionally retained (Boot.ToSchemify, settlement
 * accounts, MappedPhisicalCard foreign key, sandbox import, migrations) during the
 * coexistence phase — both paths read and write the same table.
 *
 * accountRoutings / accountRules are computed lazily on access, exactly mirroring the
 * MappedBankAccount accessors (accountRoutings still queries the BankAccountRouting
 * Mapper entity, which is unchanged).
 */
object DoobieBankAccountProvider {

  private case class AccountRow(
    bank: Option[String],
    theaccountid: Option[String],
    accountcurrency: Option[String],
    accountnumber: Option[String],
    accountbalance: Option[Long],
    accountname: Option[String],
    kind: Option[String],
    accountlabel: Option[String],
    holder: Option[String],
    accountlastupdate: Option[Timestamp],
    mbranchid: Option[String],
    accountrulescheme1: Option[String],
    accountrulevalue1: Option[Long],
    accountrulescheme2: Option[String],
    accountrulevalue2: Option[Long]
  )

  private case class DoobieBankAccount(row: AccountRow) extends BankAccount {
    override def accountId: AccountId   = AccountId(row.theaccountid.getOrElse(""))
    override def bankId: BankId         = BankId(row.bank.getOrElse(""))
    override def currency: String       = row.accountcurrency.getOrElse("").toUpperCase
    override def number: String         = row.accountnumber.getOrElse("")
    override def balance: BigDecimal     = Helper.smallestCurrencyUnitToBigDecimal(row.accountbalance.getOrElse(0L), currency)
    override def name: String           = row.accountname.getOrElse("")
    override def accountType: String    = row.kind.getOrElse("")
    override def label: String          = row.accountlabel.getOrElse("")
    override def accountHolder: String  = row.holder.getOrElse("")
    override def lastUpdate: Date       = row.accountlastupdate.map(ts => new Date(ts.getTime)).getOrElse(null)
    override def branchId: String       = row.mbranchid.getOrElse("")

    private def createAccountRule(scheme: String, value: Long): List[AccountRule] = scheme match {
      case s: String if s.equalsIgnoreCase("") == false =>
        val v = Helper.smallestCurrencyUnitToBigDecimal(value, currency)
        List(AccountRule(scheme, v.toString()))
      case _ => Nil
    }

    override def accountRoutings: List[AccountRouting] =
      BankAccountRouting.findAll(
        By(BankAccountRouting.BankId, this.bankId.value),
        By(BankAccountRouting.AccountId, this.accountId.value)
      ).map(_.accountRouting)

    override def accountRules: List[AccountRule] =
      createAccountRule(row.accountrulescheme1.getOrElse(""), row.accountrulevalue1.getOrElse(0L)) :::
        createAccountRule(row.accountrulescheme2.getOrElse(""), row.accountrulevalue2.getOrElse(0L))
  }

  private def nn(s: String): String = if (s == null) "" else s

  private val selectCols: Fragment =
    fr"""SELECT bank, theaccountid, accountcurrency, accountnumber, accountbalance, accountname,
                kind, accountlabel, holder, accountlastupdate, mbranchid,
                accountrulescheme1, accountrulevalue1, accountrulescheme2, accountrulevalue2
         FROM mappedbankaccount"""

  private def findByBankAndAccount(bankId: BankId, accountId: AccountId): Option[DoobieBankAccount] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE bank = ${bankId.value} AND theaccountid = ${accountId.value} LIMIT 1")
        .query[AccountRow].option).map(DoobieBankAccount(_))

  private def findAllByAccountId(accountId: AccountId): List[DoobieBankAccount] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE theaccountid = ${accountId.value}")
        .query[AccountRow].to[List]).map(DoobieBankAccount(_))

  /**
   * Mirror of the old getBankAccountCommon: when the accountId is a UUID, look up by
   * accountId alone first and return the single match; otherwise (or on 0/many) fall
   * back to the (bank, accountId) composite lookup.
   */
  def getBankAccount(bankId: BankId, accountId: AccountId): Box[BankAccount] = {
    val result: Option[DoobieBankAccount] =
      if (APIUtil.checkIfStringIsUUID(accountId.value)) {
        findAllByAccountId(accountId) match {
          case account :: Nil => Some(account)
          case _              => findByBankAndAccount(bankId, accountId)
        }
      } else {
        findByBankAndAccount(bankId, accountId)
      }
    result match {
      case Some(a) => Full(a)
      case None    => Empty
    }
  }

  /**
   * Upsert for createOrUpdateMappedBankAccount: sets bank, theAccountId and the
   * uppercased currency; matching the original which only touched those three fields.
   */
  def createOrUpdateAccountCurrency(bankId: BankId, accountId: AccountId, currency: String): BankAccount = {
    val now = new Timestamp(System.currentTimeMillis())
    val cur = nn(currency).toUpperCase
    val exists = findByBankAndAccount(bankId, accountId).isDefined
    if (exists) {
      DoobieUtil.runQuery(
        sql"""UPDATE mappedbankaccount
              SET bank = ${bankId.value}, theaccountid = ${accountId.value}, accountcurrency = $cur, updatedat = $now
              WHERE bank = ${bankId.value} AND theaccountid = ${accountId.value}""".update.run)
    } else {
      DoobieUtil.runQuery(
        sql"""INSERT INTO mappedbankaccount (bank, theaccountid, accountcurrency, accountbalance, createdat, updatedat)
              VALUES (${bankId.value}, ${accountId.value}, $cur, 0, $now, $now)""".update.run)
    }
    findByBankAndAccount(bankId, accountId).getOrElse(
      DoobieBankAccount(AccountRow(
        Some(bankId.value), Some(accountId.value), Some(cur), None, Some(0L),
        None, None, None, None, None, None, None, None, None, None))
    )
  }

  /** Update the smallest-currency-unit balance for an account. */
  def updateAccountBalance(bankId: BankId, accountId: AccountId, newBalance: Long): Boolean =
    DoobieUtil.runQuery(
      sql"""UPDATE mappedbankaccount SET accountbalance = $newBalance
            WHERE bank = ${bankId.value} AND theaccountid = ${accountId.value}""".update.run) > 0

  /** Update the account label. */
  def updateAccountLabel(bankId: BankId, accountId: AccountId, label: String): Boolean =
    DoobieUtil.runQuery(
      sql"""UPDATE mappedbankaccount SET accountlabel = ${nn(label)}
            WHERE bank = ${bankId.value} AND theaccountid = ${accountId.value}""".update.run) > 0

  /**
   * Update the account type (kind), label and branch id, returning the re-read account.
   * Mirrors updateBankAccount's `.kind(..).accountLabel(..).mBranchId(..).saveMe`.
   */
  def updateAccountTypeLabelBranch(bankId: BankId, accountId: AccountId,
                                   accountType: String, accountLabel: String, branchId: String): Box[BankAccount] = {
    val now = new Timestamp(System.currentTimeMillis())
    DoobieUtil.runQuery(
      sql"""UPDATE mappedbankaccount
            SET kind = ${nn(accountType)}, accountlabel = ${nn(accountLabel)}, mbranchid = ${nn(branchId)}, updatedat = $now
            WHERE bank = ${bankId.value} AND theaccountid = ${accountId.value}""".update.run)
    findByBankAndAccount(bankId, accountId) match {
      case Some(a) => Full(a)
      case None    => Empty
    }
  }
}
