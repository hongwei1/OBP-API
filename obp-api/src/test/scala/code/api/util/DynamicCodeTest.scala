/**
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */
package code.api.util

import java.nio.file.{Files, Path, Paths}
import org.json4s.JValue
import org.scalatest.{BeforeAndAfterEach, FlatSpec, Matchers}
import scala.collection.JavaConverters._

/**
 * `DynamicCode` is the seam between the management endpoints and the runtime Scala compiler.
 *
 * Two things are pinned here. First, what an instance with no compiler installed answers: on
 * JDK 24+ the SecurityManager sandbox cannot even be installed, so a deployment that does not use
 * dynamic code should be able to ship without the toolbox — and when it does, these endpoints must
 * behave exactly as they already do with `allow_user_generated_scala_code=false`, not fail open
 * and not pretend the code compiled.
 *
 * Second, that the version files no longer reach past the seam. They are 13k / 18k / 9k lines and
 * the engine references were scattered through them; a single re-added import would quietly put
 * the compiler back on the required path and undo the point of the module split.
 */
class DynamicCodeTest extends FlatSpec with Matchers with BeforeAndAfterEach {

  override def beforeEach(): Unit = { super.beforeEach(); DynamicCode.uninstall() }
  override def afterEach(): Unit = { DynamicCode.uninstall(); super.afterEach() }

  private val noJson: Option[JValue] = None

  "DynamicCode" should "report itself unavailable and disabled when no compiler is installed" in {
    DynamicCode.isAvailable should equal(false)
    DynamicCode.isEnabled should equal(false)
  }

  it should "refuse every compile with the same message the kill switch produces" in {
    // The endpoints already answer DynamicCodeExecutionDisabled when the prop is off. An absent
    // module must be indistinguishable from that, so an operator reading the response cannot be
    // misled about why the request was refused.
    val expected = Left(ErrorMessages.DynamicCodeExecutionDisabled)
    DynamicCode.checkConnectorMethod("m", "body", "Scala") should equal(expected)
    DynamicCode.checkDynamicMessageDoc("Scala", "body") should equal(expected)
    DynamicCode.checkDynamicResourceDoc(noJson, noJson, "body") should equal(expected)
  }

  it should "report a problem rather than an empty list when it cannot compile at all" in {
    // An empty problem list is the compiler's way of saying "this compiles". Returning it with no
    // compiler installed would tell the caller their code is fine when nothing examined it.
    val problems = DynamicCode.compileProblems(noJson, noJson, "anything")
    withClue(s"compileProblems returned $problems with no compiler installed: an empty list means " +
             "'it compiles', which nothing here established — ") {
      problems should not be empty
    }
    problems.head.message should equal(ErrorMessages.DynamicCodeExecutionDisabled)
  }

  it should "pass an installed compiler's answers through" in {
    DynamicCode.install(new DynamicCodeCompiler {
      def isEnabled = true
      def checkConnectorMethod(n: String, b: String, l: String) = Right(())
      def checkDynamicMessageDoc(l: String, b: String) = Left("nope")
      def checkDynamicResourceDoc(a: Option[JValue], r: Option[JValue], b: String) = Right(())
      def compileProblems(a: Option[JValue], r: Option[JValue], b: String) =
        List(DynamicCodeProblem(3, 7, "ERROR", "not found: value x"))
    })
    DynamicCode.isAvailable should equal(true)
    DynamicCode.isEnabled should equal(true)
    DynamicCode.checkConnectorMethod("m", "b", "Scala") should equal(Right(()))
    DynamicCode.checkDynamicMessageDoc("Scala", "b") should equal(Left("nope"))
    DynamicCode.compileProblems(noJson, noJson, "b").head.line should equal(3)
  }

  it should "stay disabled when an installed compiler says the prop is off" in {
    DynamicCode.install(new DynamicCodeCompiler {
      def isEnabled = false
      def checkConnectorMethod(n: String, b: String, l: String) = Right(())
      def checkDynamicMessageDoc(l: String, b: String) = Right(())
      def checkDynamicResourceDoc(a: Option[JValue], r: Option[JValue], b: String) = Right(())
      def compileProblems(a: Option[JValue], r: Option[JValue], b: String) = Nil
    })
    DynamicCode.isAvailable should equal(true)
    DynamicCode.isEnabled should equal(false)
  }

  // ── the version files must not reach past the seam ────────────────────────────────────────

  private val versionFiles: List[Path] =
    List("v4_0_0/Http4s400.scala", "v6_0_0/Http4s600.scala", "v7_0_0/Http4s700.scala")
      .map(rel => List(s"src/main/scala/code/api/$rel", s"obp-api/src/main/scala/code/api/$rel")
        .map(Paths.get(_)).find(Files.exists(_))
        .getOrElse(throw new IllegalStateException(
          s"cannot locate $rel from ${Paths.get("").toAbsolutePath}")))

  /** Symbols that only exist because something compiles code at runtime, as whole identifiers —
    * see the core-wide guard below for why substring matching is not good enough. */
  private val engineSymbolPattern =
    ("\\b(" + List("CompiledObjects", "DynamicUtil", "InternalConnector", "DynamicConnector").mkString("|") + ")\\b").r

  it should "keep the runtime compiler out of the core" in {
    // Wider than the version files: nothing outside the engine's own packages may name a symbol
    // that only exists because something compiles code at runtime. This is what makes the module
    // extractable at all — one re-added import anywhere here puts the compiler back on the
    // required path for every deployment, including the ones that deliberately ship without it.
    val coreRoots = List("src/main/scala/code", "obp-api/src/main/scala/code")
      .map(Paths.get(_)).find(Files.isDirectory(_))
      .getOrElse(throw new IllegalStateException(s"cannot locate sources from ${Paths.get("").toAbsolutePath}"))

    // The engine itself, and the composition root that installs it.
    val enginePaths = List("api/dynamic/endpoint/helper/", "abacrule/", "dynamicchangerequest/",
                           "bankconnectors/InternalConnector.scala", "bankconnectors/DynamicConnector.scala",
                           "bankconnectors/generator/", "api/util/DynamicUtil.scala")
    // Whole identifiers only. A substring match is worse than no guard: it reports
    // grantEntitlementsToUseDynamicEndpointsInSpaces, newInternalConnector,
    // DynamicConnectorMethod.methodBody and even this seam's own AbacRuleEngineProvider — none of
    // which reference the engine — and a wall of false positives is how a guard gets deleted.
    val symbolPattern =
      ("\\b(" + List("DynamicUtil", "CompiledObjects", "DynamicEndpoints", "DynamicCompileEndpoint",
                     "InternalConnector", "DynamicConnector", "AbacRuleEngine").mkString("|") + ")\\b").r

    val offenders = Files.walk(coreRoots).iterator.asScala
      .filter(p => p.toString.endsWith(".scala"))
      .filterNot(p => enginePaths.exists(p.toString.contains))
      .flatMap { p =>
        Files.readAllLines(p).asScala.toList.zipWithIndex
          .map { case (l, i) => (i + 1, l) }
          .filterNot { case (_, l) => val t = l.trim; t.startsWith("//") || t.startsWith("*") || t.startsWith("/*") || t.startsWith("|") }
          .collect {
            // invokeDynamicConnector is a NewStyle service call that merely contains a name;
            // Glossary quotes the engine inside documentation prose, not code.
            case (n, l) if symbolPattern.findFirstIn(l).isDefined &&
                           !p.toString.endsWith("Glossary.scala") =>
              s"${coreRoots.relativize(p)}:$n: ${l.trim.take(100)}"
          }
      }.toList

    withClue(
      s"these lines put the runtime Scala compiler back on the core's required path:\n" +
      s"${offenders.mkString("\n")}\n" +
      s"Go through one of the seams (DynamicCode, CompiledEndpoints, AbacAccountAccess, AbacRules, " +
      s"OptionalConnectors) instead — " ) {
      offenders shouldBe empty
    }
  }

  it should "keep the runtime compiler out of the version files" in {
    val offenders = versionFiles.flatMap { p =>
      Files.readAllLines(p).asScala.toList.zipWithIndex
        .map { case (l, i) => (i + 1, l) }
        .filterNot { case (_, l) => val t = l.trim; t.startsWith("//") || t.startsWith("*") || t.startsWith("/*") }
        .collect {
          // NewStyle.function.invokeDynamicConnector merely contains one of the names; it is a
          // call into the service layer, not a reference to the connector object.
          case (n, l) if engineSymbolPattern.findFirstIn(l).isDefined =>
            s"${p.getFileName}:$n: ${l.trim.take(100)}"
        }
    }
    withClue(
      s"these lines put the runtime Scala compiler back on the version files' required path:\n" +
      s"${offenders.mkString("\n")}\n" +
      s"Go through code.api.util.DynamicCode instead, so an instance without the compiler answers " +
      s"DynamicCodeExecutionDisabled rather than failing to start — "
    ) {
      offenders shouldBe empty
    }
  }
}
