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

    scenario("granting both directions, or neither, restricts nothing") {
      // Neither is the plain ReadTransactionsBasic/Detail case; both is a TPP asking for everything.
      for (amount <- List(BigDecimal("-25"), BigDecimal("25"), BigDecimal(0))) {
        UKAmounts.admitsDirection(Some(amount), grantsCredits = false, grantsDebits = false) should be(true)
        UKAmounts.admitsDirection(Some(amount), grantsCredits = true, grantsDebits = true) should be(true)
      }
    }

    scenario("granting only Credits admits credits and excludes debits") {
      UKAmounts.admitsDirection(Some(BigDecimal("25")), grantsCredits = true, grantsDebits = false) should be(true)
      UKAmounts.admitsDirection(Some(BigDecimal("-25")), grantsCredits = true, grantsDebits = false) should be(false)
      // Zero is a credit, so a Credits-only consent sees it.
      UKAmounts.admitsDirection(Some(BigDecimal(0)), grantsCredits = true, grantsDebits = false) should be(true)
    }

    scenario("granting only Debits admits debits and excludes credits") {
      UKAmounts.admitsDirection(Some(BigDecimal("-25")), grantsCredits = false, grantsDebits = true) should be(true)
      UKAmounts.admitsDirection(Some(BigDecimal("25")), grantsCredits = false, grantsDebits = true) should be(false)
      UKAmounts.admitsDirection(Some(BigDecimal(0)), grantsCredits = false, grantsDebits = true) should be(false)
    }

    scenario("what a response labels Debit is what a Debits-only consent admits") {
      // The two must agree, or a row could be labelled one direction and filtered as the other.
      for (amount <- List(BigDecimal("-0.01"), BigDecimal("0"), BigDecimal("0.01"), BigDecimal("-1000"))) {
        val labelledDebit = UKAmounts.creditDebitIndicator(amount) == "Debit"
        UKAmounts.admitsDirection(Some(amount), grantsCredits = false, grantsDebits = true) should be(labelledDebit)
        UKAmounts.admitsDirection(Some(amount), grantsCredits = true, grantsDebits = false) should be(!labelledDebit)
      }
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
