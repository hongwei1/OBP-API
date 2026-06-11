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

import code.api.util.{ApiPropsWithAlias, JwtUtil}
import code.api.util.APIUtil.OAuth._
import code.api.util.ApiRole.canCreateEntitlementAtOneBank
import code.entitlement.Entitlement
import com.nimbusds.jose.{JWSAlgorithm, JWSHeader}
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jwt.{JWTClaimsSet, SignedJWT}

import java.util.Date

/**
 * PRIVESC-1 — Entitlement privilege escalation via body bank_id.
 * JWT-EXP (GatewayLogin) — GatewayLogin JWT exp claim never validated.
 *
 * PRIVESC-1: Http4s700.addEntitlement (line 307-327) reads bank_id from the request body and
 * passes it to BOTH the auth check and the grant creation:
 *   hasAtLeastOneEntitlement(body.bank_id, user.userId, roles)   ← checks body bank
 *   addEntitlement(body.bank_id, userId, body.role_name)          ← grants at body bank
 *
 * A user with canCreateEntitlementAtOneBank for bank A who supplies body.bank_id=B would
 * fail the check (they lack the role at B). However, a user with canCreateEntitlementAtAnyBank
 * at bank="" (system-level) can set body.bank_id to any bank and grant any bank-level role
 * there — this is the intended behaviour for system admins. The test verifies the boundary:
 * a user with only canCreateEntitlementAtOneBank at bank A must NOT be able to grant at bank B.
 *
 * JWT-EXP (GW): GatewayLogin.validateJwtToken verifies the HMAC signature but never checks
 * the exp claim. A token deliberately issued without exp is accepted indefinitely.
 *
 * Both tests assert the secure outcome. EXPECTED TO FAIL while the path is unguarded.
 * Tagged SecurityVuln.
 */
class EntitlementPrivescTest extends SecurityVulnSetup {

  private val v7Request = baseRequest / "obp" / "v7.0.0"

  // ── PRIVESC-1 ────────────────────────────────────────────────────────────────────────────

  feature("addEntitlement must be scoped to the bank where the caller has the role") {

    scenario("PRIVESC-1: user1 with canCreateEntitlementAtOneBank at bank-A must not grant roles at bank-B", SecurityVuln) {
      Given("user1 has canCreateEntitlementAtOneBank only at bank-A")
      val bankA   = createBank("__privesc1_bank_a")
      val bankB   = createBank("__privesc1_bank_b")
      val bankAId = bankA.bankId.value
      val bankBId = bankB.bankId.value

      Entitlement.entitlement.vend.addEntitlement(bankAId, resourceUser1.userId, canCreateEntitlementAtOneBank.toString)

      When("user1 submits addEntitlement for user2 at bank-B (cross-bank body override)")
      val body =
        s"""{
           |  "bank_id":   "$bankBId",
           |  "role_name": "CanGetAnyUser"
           |}""".stripMargin

      val req  = (v7Request / "users" / resourceUser2.userId / "entitlements").POST <@ user1
      val resp = makePostRequest(req, body)

      Then("the request must be rejected with 4xx — user1 has no role at bank-B")
      withClue(
        s"POST returned ${resp.code} body=${resp.body} (expected 4xx) — " +
        s"addEntitlement (Http4s700.scala:318-320) checks hasAtLeastOneEntitlement(body.bank_id, ...) " +
        s"which means a user with the role at bank-A and body.bank_id=bank-B must fail the check " +
        s"(role not held at B) — PRIVESC-1 — "
      ) {
        resp.code should be >= 400
        resp.code should be < 500
      }
    }
  }

  // ── JWT-EXP (GatewayLogin) ───────────────────────────────────────────────────────────────

  feature("GatewayLogin validateJwtToken must reject tokens whose exp claim is in the past") {

    scenario("JWT-EXP: a GatewayLogin JWT with an already-expired exp must return Failure", SecurityVuln) {
      Given("an expired GatewayLogin-style JWT signed with the configured HMAC secret")
      val secret = ApiPropsWithAlias.jwtTokenSecret
      val expired = new JWTClaimsSet.Builder()
        .subject("jwtexp-gw-user")
        .claim("login_user_name", "jwtexp-gw-login")
        .claim("is_first", "false")
        .claim("app_id", "jwtexp-gw-app")
        .claim("app_name", "jwtexp-gw-app")
        .claim("time_stamp", "2000-01-01T00:00:00Z")
        .claim("cbs_token", "jwtexp-gw-cbs")
        .claim("cbs_id", "jwtexp-gw-id")
        .expirationTime(new Date(1000L)) // expired in 1970
        .build()

      val header    = new JWSHeader.Builder(JWSAlgorithm.HS256).build()
      val signedJwt = new SignedJWT(header, expired)
      signedJwt.sign(new MACSigner(secret.getBytes("UTF-8")))
      val expiredToken = signedJwt.serialize()

      When("GatewayLogin.validateJwtToken is called on the expired token")
      import code.api.GatewayLogin
      val result = GatewayLogin.validateJwtToken(expiredToken)

      Then("validateJwtToken must return Failure — the token has expired")
      withClue(
        s"validateJwtToken returned ${result.getClass.getSimpleName} (expected Failure) — " +
        s"GatewayLogin.validateJwtToken (GatewayLogin.scala:124-144) verifies the signature " +
        s"but never extracts or checks the exp claim; an expired token is accepted indefinitely — JWT-EXP — "
      ) {
        result.isInstanceOf[net.liftweb.common.Failure] should equal(true)
      }
    }
  }
}
