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

import com.openbankproject.commons.model.{BankIdAccountId, User, View}
import net.liftweb.common.{Box, Failure, Full}
import org.scalatest.{BeforeAndAfterEach, FlatSpec, Matchers}

/**
 * `AbacAccountAccess` is the seam between the core's authorisation path and a rule engine that
 * compiles user-supplied Scala at runtime — an engine a deployment is meant to be able to omit.
 *
 * What matters is the answer when NO engine is installed. `APIUtil.hasAccountAccess` reaches ABAC
 * only in its final branch, after public-view, firehose and ordinary view checks have already
 * refused, so ABAC is a pure GRANT: it can hand out access the view model denied and can never
 * retract access the view model allowed. `Full(false)` is therefore the fail-closed answer —
 * nothing that was denied becomes allowed — and `Full(true)` would be a silent authorisation
 * bypass on every account in the instance.
 *
 * That is the property this suite exists to pin. A future edit that "helpfully" defaults the
 * missing provider to `Full(true)`, or that lets an engine failure read as a grant, turns a
 * missing optional module into an access-control hole; these scenarios fail if it does.
 */
class AbacAccountAccessTest extends FlatSpec with Matchers with BeforeAndAfterEach {

  override def beforeEach(): Unit = {
    super.beforeEach()
    AbacAccountAccess.uninstall()
  }

  override def afterEach(): Unit = {
    AbacAccountAccess.uninstall()
    super.afterEach()
  }

  /** The seam passes these straight through, so nulls are adequate and keep the test dependency-free. */
  private def ask(): Box[Boolean] =
    AbacAccountAccess.grantsAccountAccess(
      null.asInstanceOf[User], null.asInstanceOf[View], null.asInstanceOf[BankIdAccountId], None)

  private def providerReturning(answer: Box[Boolean]): AbacAccountAccessProvider =
    new AbacAccountAccessProvider {
      def grantsAccountAccess(u: User, v: View, a: BankIdAccountId, cc: Option[CallContext]): Box[Boolean] = answer
    }

  "AbacAccountAccess" should "grant nothing when no rule engine is installed" in {
    AbacAccountAccess.isAvailable should equal(false)
    withClue(
      "with no ABAC engine installed the seam granted access. ABAC is consulted only AFTER the " +
      "ordinary view checks have already refused, so anything other than Full(false) here hands " +
      "out access the view model denied — on every account in the instance — purely because an " +
      "optional module is absent — "
    ) {
      ask() should equal(Full(false))
    }
  }

  it should "report availability truthfully" in {
    AbacAccountAccess.isAvailable should equal(false)
    AbacAccountAccess.install(providerReturning(Full(false)))
    AbacAccountAccess.isAvailable should equal(true)
    AbacAccountAccess.uninstall()
    AbacAccountAccess.isAvailable should equal(false)
  }

  it should "pass an installed engine's verdict through unchanged" in {
    // Including Failure: in the fallback position a denial with a reason and a plain decline are
    // both refusals, and the core distinguishes them only to report why.
    AbacAccountAccess.install(providerReturning(Full(true)))
    ask() should equal(Full(true))

    AbacAccountAccess.install(providerReturning(Full(false)))
    ask() should equal(Full(false))

    val denied = Failure("ABAC rules denied access. Failing rule IDs: r1")
    AbacAccountAccess.install(providerReturning(denied))
    ask() should equal(denied)
  }

  it should "go back to granting nothing once the engine is removed" in {
    // A deployment that drops the optional module must return to the shipped default rather than
    // keeping whatever the last engine said.
    AbacAccountAccess.install(providerReturning(Full(true)))
    ask() should equal(Full(true))

    AbacAccountAccess.uninstall()
    ask() should equal(Full(false))
  }
}
