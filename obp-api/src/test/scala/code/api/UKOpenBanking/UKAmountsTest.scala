package code.api.UKOpenBanking

import org.scalatest.{FeatureSpec, GivenWhenThen, Matchers}

/**
 * UK Open Banking splits a signed amount in two: `Amount` is unsigned (its pattern,
 * `^\d{1,13}$|^\d{1,13}\.\d{1,5}$`, admits no sign) and the direction sits beside it in
 * `CreditDebitIndicator`. OBP holds one signed BigDecimal, so every UK response has to split it.
 *
 * The factories used to hardcode `"Credit"` and pass the signed number straight through, which
 * reported a debit of 25 as a credit of -25 — both halves wrong at once. These scenarios pin the
 * split so neither half can drift back.
 */
class UKAmountsTest extends FeatureSpec with Matchers with GivenWhenThen {

  feature("UK Open Banking - splitting a signed amount into magnitude and direction") {

    scenario("a negative amount is a debit, reported as its magnitude") {
      UKAmounts.creditDebitIndicator(BigDecimal("-25.00")) should be("Debit")
      UKAmounts.unsignedAmount(BigDecimal("-25.00")) should be("25.00")
    }

    scenario("a positive amount is a credit, unchanged") {
      UKAmounts.creditDebitIndicator(BigDecimal("1209.06")) should be("Credit")
      UKAmounts.unsignedAmount(BigDecimal("1209.06")) should be("1209.06")
    }

    scenario("zero is a credit, as the standard states explicitly") {
      UKAmounts.creditDebitIndicator(BigDecimal(0)) should be("Credit")
      UKAmounts.unsignedAmount(BigDecimal(0)) should be("0")
    }

    scenario("a missing amount is treated as zero, not as an error") {
      UKAmounts.creditDebitIndicator(None: Option[BigDecimal]) should be("Credit")
      UKAmounts.unsignedAmount(None: Option[BigDecimal]) should be("0")
      UKAmounts.creditDebitIndicator(Some(BigDecimal("-1"))) should be("Debit")
      UKAmounts.unsignedAmount(Some(BigDecimal("-1"))) should be("1")
    }

    scenario("an amount OBP already holds as a string splits the same way") {
      UKAmounts.creditDebitIndicatorOfString("-25.00") should be("Debit")
      UKAmounts.unsignedAmountString("-25.00") should be("25.00")
      UKAmounts.creditDebitIndicatorOfString("1209.06") should be("Credit")
      UKAmounts.unsignedAmountString("1209.06") should be("1209.06")
    }

    scenario("a value that is not a number is passed through rather than turned into a fabricated zero") {
      UKAmounts.unsignedAmountString("") should be("")
      UKAmounts.unsignedAmountString("not-a-number") should be("not-a-number")
      UKAmounts.creditDebitIndicatorOfString("") should be("Credit")
    }

    scenario("every produced Amount matches the standard's unsigned pattern") {
      val pattern = "^\\d{1,13}$|^\\d{1,13}\\.\\d{1,5}$".r
      List("-25.00", "25.00", "0", "-0.5", "1209.06", "-1234567890123").foreach { input =>
        val produced = UKAmounts.unsignedAmountString(input)
        withClue(s"input $input produced $produced: ") {
          pattern.findFirstIn(produced).isDefined should be(true)
        }
      }
    }
  }
}
