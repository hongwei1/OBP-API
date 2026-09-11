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

import code.api.Constant.InstanceRole
import org.scalatest.{FlatSpec, Matchers}

/**
 * `instance.role` decides which slice of `Boot` a JVM performs, so getting it wrong is not a
 * cosmetic failure: a role that wrongly reports `runsMigrations = false` starts against a schema
 * nobody created, and one that wrongly reports `runsSchedulers = false` silently stops archiving
 * metrics and publishing the Open Corridor outbox.
 *
 * `InstanceRole.value` is resolved once at class-initialisation time from props, so this JVM can
 * only ever observe a single role — the rules are therefore checked as functions, and the
 * resolved values are checked only for the default this test suite actually boots under.
 */
class InstanceRoleTest extends FlatSpec with Matchers {

  "InstanceRole" should "give each role exactly the slice of boot it owns" in {
    val table = Seq(
      //  role                      migrations  schedulers  exits
      (InstanceRole.All,       true,  true,  false),
      (InstanceRole.Migrator,  true,  false, true),
      (InstanceRole.Scheduler, false, true,  false),
      (InstanceRole.Web,       false, false, false)
    )
    table.foreach { case (role, migrations, schedulers, exits) =>
      withClue(s"role '$role': ") {
        InstanceRole.runsMigrationsFor(role) should equal(migrations)
        InstanceRole.runsSchedulersFor(role) should equal(schedulers)
        InstanceRole.exitsAfterBootFor(role) should equal(exits)
      }
    }
  }

  it should "have every unit of boot work owned by exactly one dedicated role" in {
    // The split only buys anything if migrator + scheduler + web together cover all of boot with
    // no overlap. Overlap means two replicas doing the same job; a gap means nobody does it.
    val dedicated = Seq(InstanceRole.Migrator, InstanceRole.Scheduler, InstanceRole.Web)
    dedicated.count(InstanceRole.runsMigrationsFor) should equal(1)
    dedicated.count(InstanceRole.runsSchedulersFor) should equal(1)
    dedicated.count(InstanceRole.exitsAfterBootFor) should equal(1)
  }

  it should "accept a role regardless of surrounding whitespace or case" in {
    InstanceRole.normalise("  Migrator ") should equal(InstanceRole.Migrator)
    InstanceRole.normalise("WEB") should equal(InstanceRole.Web)
  }

  it should "refuse an unrecognised role instead of quietly doing no work" in {
    // A typo that parsed as "not migrator, not scheduler, not web" would produce an instance that
    // creates no schema, runs no jobs, and reports nothing wrong.
    val thrown = intercept[IllegalArgumentException] {
      InstanceRole.normalise("webb")
    }
    thrown.getMessage should include("webb")
    thrown.getMessage should include(InstanceRole.Web)
  }

  it should "default to the historical all-in-one behaviour when the prop is unset" in {
    // This suite boots without instance.role set, which is what an existing deployment looks
    // like. It must behave exactly as it did before the role split existed.
    InstanceRole.value should equal(InstanceRole.All)
    InstanceRole.runsMigrations should equal(true)
    InstanceRole.runsSchedulers should equal(true)
    InstanceRole.exitsAfterBoot should equal(false)
  }
}
