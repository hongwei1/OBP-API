package code.api.UKOpenBanking

import code.api.util.{APIUtil, CallContext}
import code.model.ModeratedTransaction
import com.openbankproject.commons.model.{AccountId, BankId, BankIdAccountId, User, ViewId}

/**
 * How UK Open Banking writes a signed amount.
 *
 * The standard splits sign from magnitude: `Amount` is unsigned — `OBActiveCurrencyAndAmount_SimpleType`
 * is `^\d{1,13}$|^\d{1,13}\.\d{1,5}$`, which no negative string matches — and the direction is carried
 * beside it in `CreditDebitIndicator` (`OBCreditDebitCode`, `Credit` | `Debit`). OBP holds the same fact
 * the other way round, as one signed BigDecimal, so every UK response has to split it.
 *
 * Shared by the v3.1 and v4.0.1 factories: the two used to hardcode `"Credit"` next to a signed amount,
 * which reported a debit of 25 as a credit of -25 — wrong in both fields at once. One copy so they
 * cannot drift back apart.
 */
object UKAmounts {

  /** `Credit` or `Debit` for a signed amount. Zero is a credit, as the standard says explicitly. */
  def creditDebitIndicator(amount: BigDecimal): String =
    if (amount < 0) "Debit" else "Credit"

  def creditDebitIndicator(amount: Option[BigDecimal]): String =
    creditDebitIndicator(amount.getOrElse(BigDecimal(0)))

  /** The magnitude, as the unsigned decimal string the `Amount` field's pattern allows. */
  def unsignedAmount(amount: BigDecimal): String = amount.abs.toString()

  def unsignedAmount(amount: Option[BigDecimal]): String =
    unsignedAmount(amount.getOrElse(BigDecimal(0)))

  /**
   * The same split for an amount OBP already holds as a string (balances come through that way).
   * An unparseable value is passed through untouched rather than turned into a fabricated zero.
   */
  def unsignedAmountString(amount: String): String =
    scala.util.Try(BigDecimal(amount)).map(unsignedAmount).getOrElse(amount)

  def creditDebitIndicatorOfString(amount: String): String =
    scala.util.Try(BigDecimal(amount)).map(creditDebitIndicator).getOrElse("Credit")

  /**
   * Whether a transaction of this amount is inside the directions a consent granted.
   *
   * `ReadTransactionsCredits` and `ReadTransactionsDebits` are independently selectable Permissions,
   * and they restrict which rows come back rather than which fields are visible -- a PSU who grants
   * only Credits sees the same fields, on credit rows alone. Granting both, or neither, places no
   * direction restriction: neither is the plain `ReadTransactionsBasic`/`Detail` case, both is the
   * TPP asking for everything.
   *
   * Reads the direction through creditDebitIndicator rather than testing the sign again here, so the
   * row a response labels `Debit` is exactly the row this admits under Debits.
   */
  def admitsDirection(amount: Option[BigDecimal], grantsCredits: Boolean, grantsDebits: Boolean): Boolean =
    if (grantsCredits == grantsDebits) true
    else creditDebitIndicator(amount) == (if (grantsCredits) "Credit" else "Debit")

  /**
   * Whether the caller holds a given direction view on this account.
   *
   * A soft check: the answer is a fact about the consent's scope, not a refusal. The endpoints
   * already resolved a Detail-or-Basic view to read with, so a caller holding neither direction view
   * is not an error -- it simply has no direction restriction.
   */
  def grantsView(
    viewId: String,
    bankId: BankId,
    accountId: AccountId,
    user: User,
    callContext: CallContext
  ): Boolean =
    APIUtil.checkViewAccessAndReturnView(
      ViewId(viewId), BankIdAccountId(bankId, accountId), Some(user), Some(callContext)).isDefined

  /** admitsDirection over a transaction list, shared by the v3.1 and v4.0.1 endpoints. */
  def filterByGrantedDirections(
    transactions: List[ModeratedTransaction],
    grantsCredits: Boolean,
    grantsDebits: Boolean
  ): List[ModeratedTransaction] =
    if (grantsCredits == grantsDebits) transactions
    else transactions.filter(t => admitsDirection(t.amount, grantsCredits, grantsDebits))
}
