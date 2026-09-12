package code.api.util

import java.nio.file.{Files, Path, Paths}
import org.scalatest.{FlatSpec, Matchers}
import scala.collection.JavaConverters._

/**
 * Endpoints must reach reference data through Connector, never through a provider directly.
 *
 * MethodRouting decides per Connector method which connector answers, and three of them are
 * out-of-process. An endpoint that calls Atms.atmsProvider directly bypasses that decision: a
 * deployment that routes getAtms to a remote connector would still get local data from that one
 * endpoint, and two API versions would disagree about the same bank. That is also what makes a
 * domain — atms, branches, and the ones after them — extractable into its own process at all:
 * every read has to pass the routing point.
 *
 * The provider packages themselves and the connectors that implement them are the only legitimate
 * callers. Whole identifiers only, for the reason DynamicCodeTest gives: a substring guard drowns
 * in false positives and gets deleted.
 */
class ConnectorRoutingBypassTest extends FlatSpec with Matchers {

  private val mainRoot: Path =
    List("src/main/scala/code", "obp-api/src/main/scala/code")
      .map(Paths.get(_)).find(Files.isDirectory(_))
      .getOrElse(throw new IllegalStateException(
        s"cannot locate sources from ${Paths.get("").toAbsolutePath}"))

  private val legitimateCallers = List("atms/", "branches/", "products/", "bankconnectors/")

  /**
   * The sandbox importer is exempt, and deliberately so. Its `productsProvider.getProduct` call is
   * the pre-flight "does this code already exist for this bank" check of a bulk import whose
   * *writes* go straight to the mapped store as `Saveable[ProductType]` — not through Connector at
   * all. Routing only the read would make it ask the remote store a question it then answers by
   * writing locally, which is worse than the bypass it fixes. Whoever routes the sandbox importer
   * has to move its writes first; until then this line is consistent with itself.
   */
  private val exemptFiles = List("sandbox/OBPDataImport.scala")

  private val providerPattern = "\\b(atmsProvider|branchesProvider|productsProvider)\\b".r

  "endpoints" should "reach atms, branches and products through Connector, not a provider" in {
    val offenders = Files.walk(mainRoot).iterator.asScala
      .filter(p => p.toString.endsWith(".scala"))
      .filterNot(p => legitimateCallers.exists(seg => mainRoot.relativize(p).toString.startsWith(seg)))
      .filterNot(p => exemptFiles.exists(f => mainRoot.relativize(p).toString.endsWith(f)))
      .flatMap { p =>
        Files.readAllLines(p).asScala.toList.zipWithIndex
          .map { case (l, i) => (i + 1, l) }
          .filterNot { case (_, l) => val t = l.trim; t.startsWith("//") || t.startsWith("*") || t.startsWith("/*") }
          .collect { case (n, l) if providerPattern.findFirstIn(l).isDefined =>
            s"${mainRoot.relativize(p)}:$n: ${l.trim.take(100)}" }
      }.toList

    withClue(
      s"these lines bypass MethodRouting by calling a provider directly:\n${offenders.mkString("\n")}\n" +
      "Go through Connector.connector.vend (or NewStyle) instead, so the routing table decides who answers — ") {
      offenders shouldBe empty
    }
  }

  // An exemption nobody re-checks is how a guard rots: if the sandbox importer stops calling the
  // provider (because its writes moved to Connector too), the exemption must go with it, or the
  // next bypass added to that file passes unnoticed.
  it should "keep every exemption earning its place" in {
    exemptFiles.foreach { f =>
      val path = mainRoot.resolve(f)
      withClue(s"$f is exempted from the provider guard but no longer exists — drop the exemption: ") {
        Files.exists(path) shouldBe true
      }
      val stillCalls = Files.readAllLines(path).asScala.exists(l => providerPattern.findFirstIn(l).isDefined)
      withClue(s"$f is exempted from the provider guard but no longer calls a provider — drop the exemption: ") {
        stillCalls shouldBe true
      }
    }
  }
}
