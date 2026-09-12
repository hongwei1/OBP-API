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
import org.scalatest.{FlatSpec, Matchers}
import scala.collection.JavaConverters._

/**
 * The core utility layer must not depend on an API standard.
 *
 * Everything in `code.api.util` is imported by hundreds of files, the standards packages included.
 * When the core also reaches back into a standard, the two are mutually dependent: neither can be
 * compiled, reasoned about or eventually extracted without the other. `code.api.util` therefore
 * owns the little Berlin Group vocabulary it genuinely needs (see BerlinGroupVocabulary) and the
 * standards alias it, rather than the other way round.
 *
 * Everything in this repository is one Maven module, so nothing stops the next edit from adding
 * the import straight back. Until a module boundary can enforce it, this test is the gatekeeper.
 *
 * Three files are exempt, each listed below with its reason. If you are here because this test
 * failed, the fix is almost never to add a fourth.
 */
class CoreDependencyDirectionTest extends FlatSpec with Matchers {

  /**
   * Scala sources of the core utility layer. Maven runs the suite with the MODULE as the working
   * directory, the IDE and a root-level invocation with the repository root, so both are tried.
   * Not finding them is a hard failure rather than a cancel: a guard that silently opts out when
   * it cannot see the code is worse than no guard, because the build still goes green.
   */
  private val coreRoot: Path =
    List("src/main/scala/code/api/util", "obp-api/src/main/scala/code/api/util")
      .map(Paths.get(_))
      .find(Files.isDirectory(_))
      .getOrElse(throw new IllegalStateException(
        s"cannot locate code/api/util sources from working directory ${Paths.get("").toAbsolutePath}"))

  private val standardPackages = List("code.api.berlin", "code.api.UKOpenBanking")

  /**
   * Exempt, with reasons:
   *
   *  - `http4s/Http4sApp.scala` is the composition root. It builds the request chain, so naming
   *    every standard's routes is its entire job — that is assembly, not a core utility reaching
   *    sideways. Inverting it would mean a runtime plugin registry, which buys nothing here and
   *    costs the compile-time guarantee that every wired standard exists.
   *
   *  - `ConsentUtil.scala` is a genuine violation, not a principled exemption: it is 2,600+ lines
   *    coupling versions, persistence and schedulers, and untangling its Berlin Group consent JSON
   *    is its own piece of work. Listed so this guard can protect everything else meanwhile.
   *
   *  - `BerlinGroupCheck.scala` is Berlin Group logic that happens to sit in this package. The fix
   *    is to move the file into the standard's package, not to invert anything; listed until then.
   */
  private val exempt = Set(
    "http4s/Http4sApp.scala",
    "ConsentUtil.scala",
    "BerlinGroupCheck.scala"
  )

  private def sourceLines(p: Path): List[(Int, String)] =
    Files.readAllLines(p).asScala.toList.zipWithIndex
      .map { case (l, i) => (i + 1, l) }
      .filterNot { case (_, l) =>
        val t = l.trim
        t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")
      }

  "the core utility layer" should "not depend on any API standard" in {
    val offenders =
      Files.walk(coreRoot).iterator.asScala
        .filter(p => p.toString.endsWith(".scala"))
        .filterNot(p => exempt.contains(coreRoot.relativize(p).toString))
        .flatMap { p =>
          sourceLines(p).collect {
            case (n, l) if standardPackages.exists(l.contains) =>
              s"${coreRoot.relativize(p)}:$n: ${l.trim.take(100)}"
          }
        }
        .toList

    withClue(
      s"these files in code.api.util reach into an API standard, which makes the core and that " +
      s"standard mutually dependent:\n${offenders.mkString("\n")}\n" +
      s"Put the vocabulary the core needs in code.api.util (see BerlinGroupVocabulary) and alias " +
      s"it from the standard, rather than importing the standard here — "
    ) {
      offenders shouldBe empty
    }
  }

  it should "keep the exemption list honest" in {
    // An exemption that no longer names a real file is a stale excuse, and one whose file has
    // since been cleaned up should be removed so the guard tightens instead of drifting.
    exempt.foreach { rel =>
      val p = coreRoot.resolve(rel)
      withClue(s"exempt file $rel does not exist — remove it from the list: ") {
        Files.exists(p) should equal(true)
      }
      withClue(s"exempt file $rel no longer references a standard — remove it from the list so " +
               s"the guard covers it: ") {
        sourceLines(p).exists { case (_, l) => standardPackages.exists(l.contains) } should equal(true)
      }
    }
  }
}
