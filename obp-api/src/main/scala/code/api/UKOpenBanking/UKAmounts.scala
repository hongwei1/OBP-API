package code.api.UKOpenBanking

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
}
