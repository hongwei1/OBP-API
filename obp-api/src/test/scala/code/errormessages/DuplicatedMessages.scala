package code.errormessages

import code.setup.ServerSetup

/**
 * Every OBP error message carries a number (OBP-nnnnn) and two messages must never share one:
 * clients match on the number, so a duplicate makes two different failures indistinguishable.
 *
 * The check parses ErrorMessages.scala rather than inspecting the object, because the numbers are
 * embedded in the string literals and only the source shows all of them in declaration order.
 *
 * The parser used to live on ErrorMessages itself. It moved here when that object moved to
 * obp-kernel: it needs scalameta, which the kernel deliberately does not depend on — the kernel is
 * meant to hold API vocabulary, not a Scala parser — and it is only ever used by this test.
 */
class DuplicatedMessages extends ServerSetup {

  /** ErrorMessages.scala, wherever the suite is run from (module directory or repository root). */
  private def errorMessagesSource: java.io.File =
    List(
      "../obp-kernel/src/main/scala/code/api/util/ErrorMessages.scala",
      "obp-kernel/src/main/scala/code/api/util/ErrorMessages.scala"
    ).map(new java.io.File(_)).find(_.exists()).getOrElse(
      fail(s"cannot find ErrorMessages.scala from working directory ${new java.io.File(".").getAbsolutePath}")
    )

  private def duplicatedMessageNumbers: List[(String, Int)] = {
    import scala.meta._
    val source: Source = errorMessagesSource.parse[Source].get
    val numbers = source.collect {
      case obj: Defn.Object if obj.name.value == "ErrorMessages" =>
        obj.collect {
          case v: Defn.Val if v.rhs.syntax.startsWith(""""OBP-""") => v.rhs.syntax.split(":")(0)
        }
    }.flatten
    numbers.groupBy(identity).map { case (n, occurrences) => n -> occurrences.length }
      .toList.filter(_._2 > 1)
  }

  feature("Try to find duplicated message numbers") {
    scenario("Parse the file ErrorMessages.scala") {
      Then("Size of list of duplicated message numbers has to be 0")
      val duplicates = duplicatedMessageNumbers
      withClue(s"duplicated OBP error numbers: ${duplicates.mkString(", ")} — ") {
        duplicates.size should equal(0)
      }
    }
  }
}
