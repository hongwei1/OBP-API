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

import code.setup.{DefaultUsers, ServerSetupWithTestData}
import org.scalatest.Tag

/**
 * Tag for the security-vulnerability evidence tests.
 *
 * Each scenario ASSERTS the theoretically secure / correct outcome, so while the underlying
 * vulnerability remains unpatched the test is EXPECTED TO FAIL — the red bar with its
 * "expected vs actual" clue is the evidence the vulnerability is real. When a path is fixed,
 * the corresponding scenario flips from red to green automatically.
 *
 * Run only these tests:
 *   mvn -pl obp-commons,obp-api scalatest:test -DtagsToInclude=code.security.SecurityVuln -DfailIfNoTests=false
 *
 * Exclude from CI main flow:
 *   mvn -pl obp-commons,obp-api scalatest:test -DtagsToExclude=code.security.SecurityVuln
 */
object SecurityVuln extends Tag("code.security.SecurityVuln")

/**
 * Shared base trait for the security-vulnerability evidence suites.
 *
 * Mirrors ConcurrentRaceSetup in structure: provides DefaultUsers (user1/user2/resourceUser1/
 * resourceUser2 OAuth credentials) and ServerSetupWithTestData (live embedded server, H2 DB,
 * helper factories). Tests call providers directly for unit-style checks and the embedded
 * HTTP server for end-to-end path verification.
 */
trait SecurityVulnSetup extends ServerSetupWithTestData with DefaultUsers
