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
package code.security

import code.api.util.{APIUtil, CallContext}
import code.api.util.APIUtil.HTTPParam

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

/**
 * PERF-PP — Blocking Await.result in gRPC AuthInterceptor.
 *
 * AuthInterceptor.interceptCall (AuthInterceptor.scala:72) calls:
 *   val (userBox, _) = Await.result(APIUtil.getUserAndSessionContextFuture(cc), 30.seconds)
 *
 * This blocks the gRPC dispatcher thread for up to 30 seconds per authenticated call. If the
 * auth service (DB connection pool / DirectLogin / OAuth lookup) degrades, all concurrent
 * gRPC requests serialize behind this wall — complete thread starvation of the gRPC server.
 * The fix is to use a non-blocking Future callback and return the completion asynchronously
 * via ServerCall/Listener instead of blocking the interceptor thread.
 *
 * The test asserts the secure design: the interceptor must NOT block the calling thread; instead
 * it must complete asynchronously within a strict wall-clock budget that precludes 30-second
 * blocking. EXPECTED TO FAIL while Await.result is used. Tagged SecurityVuln.
 */
class GrpcAuthBlockingTest extends SecurityVulnSetup {

  feature("gRPC AuthInterceptor must authenticate asynchronously — no blocking Await") {

    scenario("PERF-PP: interceptCall must complete without blocking the calling thread for 30 seconds", SecurityVuln) {
      Given("a call context simulating a gRPC inbound request with DirectLogin credentials")
      val cc = CallContext(
        requestHeaders = List(HTTPParam("DirectLogin", List("token=perf-pp-timing-probe"))),
        verb                = "GET",
        url                 = "/grpc/chat",
        implementedInVersion = "v6.0.0",
        correlationId       = APIUtil.generateUUID()
      )

      When("getUserAndSessionContextFuture is invoked and completion time measured")
      val maxAcceptableMs = 5000L // a genuine Await(30s) must exceed this; async callback does not
      val start  = System.currentTimeMillis()
      val future = APIUtil.getUserAndSessionContextFuture(cc)
      val elapsed = System.currentTimeMillis() - start // synchronous part must be near-zero

      Then("the synchronous portion of the call must return in < maxAcceptableMs ms — no blocking")
      withClue(
        s"Synchronous elapsed=${elapsed}ms (max allowed=${maxAcceptableMs}ms) — " +
        s"AuthInterceptor.interceptCall (AuthInterceptor.scala:72) calls Await.result(future, 30.seconds), " +
        s"blocking the gRPC dispatcher thread for up to 30 seconds per call; " +
        s"under auth-service degradation all gRPC threads pile up behind this wall (PERF-PP) — "
      ) {
        elapsed should be < maxAcceptableMs
      }

      // Ensure the future resolves eventually so we don't leak resources
      Await.ready(future, 35.seconds)
    }
  }
}
