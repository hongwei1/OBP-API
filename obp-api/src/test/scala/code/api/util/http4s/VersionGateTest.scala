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
package code.api.util.http4s

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import org.http4s.{HttpRoutes, Method, Request, Status, Uri}
import org.scalatest.{FlatSpec, Matchers}

/**
 * `api_disabled_versions` / `api_enabled_versions` are enforced once, at object-init time, by
 * `VersionGate`. Two properties matter and neither is observable from a single JVM through the
 * props (the gate is resolved before any test could set them), so they are pinned on the decision
 * function itself.
 *
 *  1. An excluded version must serve nothing.
 *  2. An excluded version's routes must never be FORCED. The `routes` parameter used to be by
 *     value, so a disabled version still initialised its whole object — every ResourceDoc
 *     registered, every route built — purely to have the result discarded. Disabling a version
 *     cost exactly as much boot work as keeping it.
 */
class VersionGateTest extends FlatSpec with Matchers {

  private def alwaysOk: HttpRoutes[IO] =
    HttpRoutes.of[IO] { case _ => IO.pure(org.http4s.Response[IO](Status.Ok)) }

  private def runGate(routes: HttpRoutes[IO]): Option[Status] =
    routes.run(Request[IO](Method.GET, Uri.unsafeFromString("/anything"))).value.unsafeRunSync().map(_.status)

  "gateWhen" should "serve the routes when the version is allowed" in {
    runGate(VersionGate.when(allowed = true, alwaysOk)) should equal(Some(Status.Ok))
  }

  it should "serve nothing when the version is excluded" in {
    runGate(VersionGate.when(allowed = false, alwaysOk)) should equal(None)
  }

  it should "never build an excluded version's routes" in {
    var built = false
    def expensiveRoutes: HttpRoutes[IO] = { built = true; alwaysOk }

    VersionGate.when(allowed = false, expensiveRoutes)

    withClue(
      "the excluded version's routes were constructed anyway. `gate` takes them by name precisely " +
      "so a version turned off by api_disabled_versions is never initialised — with a by-value " +
      "parameter, disabling a version saves no boot work at all — "
    ) {
      built should equal(false)
    }
  }

  it should "build an allowed version's routes exactly once" in {
    var builds = 0
    def countedRoutes: HttpRoutes[IO] = { builds += 1; alwaysOk }

    val gated = VersionGate.when(allowed = true, countedRoutes)
    // Several requests through the same gated value must not rebuild it: a by-name parameter that
    // leaked into the returned Kleisli would re-evaluate per request.
    runGate(gated)
    runGate(gated)

    builds should equal(1)
  }
}
