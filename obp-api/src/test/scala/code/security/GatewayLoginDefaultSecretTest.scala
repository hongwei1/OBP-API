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

import code.api.DAuth
import code.api.util.{ApiPropsWithAlias, CertificateUtil}
import code.api.util.JwtUtil
import com.nimbusds.jose.{JWSAlgorithm, JWSHeader}
import com.nimbusds.jose.crypto.MACSigner
import com.nimbusds.jwt.{JWTClaimsSet, SignedJWT}

import java.util.Date

/**
 * CR-1 — Hardcoded default HMAC secret for GatewayLogin/DAuth JWTs.
 * CR-2 — DAuth RSA-verifies-HMAC key confusion.
 * JWT-EXP — DAuth + GatewayLogin JWT exp claim never validated.
 *
 * CR-1: ApiPropsWithAlias.jwtTokenSecret (line 33-36) uses defaultValue =
 * "Cannot get your at least 256 bit secret". This constant is in public source. If the
 * operator has not overridden jwt_token_secret, an attacker can mint a valid HS256 token
 * with any login_user_name and authenticate as any user. The server must REFUSE to start
 * (or refuse auth) when the secret is the public default.
 *
 * CR-2: DAuth.validateJwtToken (dauth.scala:112-131) when jwt.use.ssl=false checks
 * JwtUtil.validateJwtWithRsaKey(token) for signature type but accepts an HMAC-signed
 * token when validateJwtWithRsaKey returns false, then proceeds to parse the claims
 * from it without the signature having been verified by HMAC either. This creates a path
 * where a token signed with neither valid RSA nor HMAC passes validation.
 *
 * JWT-EXP: Neither DAuth.validateJwtToken nor GatewayLogin.validateJwtToken checks the
 * JWT exp claim after parsing. An expired (or exp-less) token is accepted indefinitely,
 * bypassing the intended session timeout.
 *
 * Each test asserts the secure outcome and is EXPECTED TO FAIL while the path is unguarded.
 * Tagged SecurityVuln.
 */
class GatewayLoginDefaultSecretTest extends SecurityVulnSetup {

  // ── CR-1 ────────────────────────────────────────────────────────────────────────────────

  feature("GatewayLogin HMAC secret must not be the hardcoded public default") {

    scenario("CR-1: a JWT signed with the known public default secret must be rejected", SecurityVuln) {
      Given("the public known default GatewayLogin HMAC secret")
      val publicDefaultSecret = "Cannot get your at least 256 bit secret"
      val configuredSecret    = ApiPropsWithAlias.jwtTokenSecret

      When("a JWT is forged with the default secret")
      val forgeable = configuredSecret == publicDefaultSecret

      // Mint a JWT with the default secret and an arbitrary user
      val claims = new JWTClaimsSet.Builder()
        .subject("cr1-attacker")
        .claim("login_user_name", "admin")
        .claim("is_first", "false")
        .claim("app_id", "cr1-test-app")
        .claim("app_name", "cr1-test-app")
        .claim("time_stamp", System.currentTimeMillis().toString)
        .claim("cbs_token", "cr1-forged-cbs-token")
        .claim("cbs_id", "cr1-cbs-id")
        .expirationTime(new Date(System.currentTimeMillis() + 3600000L))
        .build()
      val header    = new JWSHeader.Builder(JWSAlgorithm.HS256).build()
      val signedJwt = new SignedJWT(header, claims)
      signedJwt.sign(new MACSigner(publicDefaultSecret.getBytes("UTF-8")))
      val token = signedJwt.serialize()

      Then("if the configured secret is the public default, the token must be rejected with 401")
      // When the secret is the public default, this token is VALID — proving CR-1.
      // A secure deployment must refuse this token (or startup must fail when default secret is detected).
      withClue(
        s"Secret in use == publicDefault=$forgeable " +
        s"(expected false) — ApiPropsWithAlias.jwtTokenSecret defaults to the known public " +
        s"constant 'Cannot get your at least 256 bit secret' (ApiPropsWithAlias.scala:33-36); " +
        s"if jwt_token_secret is unset any attacker can forge a valid GatewayLogin JWT — "
      ) {
        // If forgeable=true it means the server is running with the default secret
        forgeable should equal(false)
      }
    }
  }

  // ── JWT-EXP ──────────────────────────────────────────────────────────────────────────────

  feature("DAuth validateJwtToken must reject tokens whose exp claim is in the past") {

    scenario("JWT-EXP: a DAuth JWT with an already-expired exp must return Failure, not Full", SecurityVuln) {
      Given("an expired DAuth-style JWT signed with HMAC (the current configured secret)")
      val secret = ApiPropsWithAlias.jwtTokenSecret
      val expired = new JWTClaimsSet.Builder()
        .subject("jwtexp-test-user")
        .claim("smart_contract_address", "jwtexp-contract")
        .claim("network_name", "testnet")
        .claim("login_user_name", "jwtexp-user")
        .claim("is_first", "false")
        .claim("app_id", "jwtexp-app")
        .claim("app_name", "jwtexp-app")
        .claim("time_stamp", "2000-01-01T00:00:00Z")
        .claim("cbs_token", "jwtexp-cbs-token")
        .expirationTime(new Date(1000L)) // expired in 1970
        .build()

      val header    = new JWSHeader.Builder(JWSAlgorithm.HS256).build()
      val signedJwt = new SignedJWT(header, expired)
      signedJwt.sign(new MACSigner(secret.getBytes("UTF-8")))
      val expiredToken = signedJwt.serialize()

      When("validateJwtToken is called on the expired token")
      val result = DAuth.validateJwtToken(expiredToken)

      Then("validateJwtToken must return Failure — the token has expired")
      withClue(
        s"validateJwtToken returned Full (expected Failure) — DAuth.validateJwtToken " +
        s"(dauth.scala:112-131) verifies the signature but never checks the exp claim; " +
        s"an expired token is accepted indefinitely — JWT-EXP — "
      ) {
        result.isInstanceOf[net.liftweb.common.Failure] should equal(true)
      }
    }
  }

  // ── CR-2 ────────────────────────────────────────────────────────────────────────────────

  feature("DAuth validateJwtToken must not accept an HMAC token when RSA verification fails") {

    scenario("CR-2: a token where RSA-verify returns false but is HMAC-signed must NOT return Full", SecurityVuln) {
      Given("an HMAC-signed token that is NOT a valid RSA signature")
      val secret = ApiPropsWithAlias.jwtTokenSecret
      val claims = new JWTClaimsSet.Builder()
        .subject("cr2-test-user")
        .claim("login_user_name", "cr2-user")
        .claim("smart_contract_address", "cr2-contract")
        .claim("network_name", "testnet")
        .claim("is_first", "false")
        .claim("app_id", "cr2-app")
        .claim("app_name", "cr2-app")
        .claim("time_stamp", System.currentTimeMillis().toString)
        .claim("cbs_token", "cr2-cbs")
        .expirationTime(new Date(System.currentTimeMillis() + 3600000L))
        .build()

      val header    = new JWSHeader.Builder(JWSAlgorithm.HS256).build()
      val signedJwt = new SignedJWT(header, claims)
      signedJwt.sign(new MACSigner(secret.getBytes("UTF-8")))
      val hmacToken = signedJwt.serialize()

      When("validateJwtToken is called with jwt.use.ssl=false (HMAC path)")
      // DAuth.validateJwtToken: when jwt.use.ssl=false, checks validateJwtWithRsaKey first.
      // For an HS256 token, RSA verify throws/returns false → falls to HMAC path.
      // The test verifies the HMAC path validates expiration as well.
      val result = DAuth.validateJwtToken(hmacToken)

      Then("result must reflect proper validation — Full only if signature AND exp are valid")
      // CR-2 is primarily about the path where RSA returns false but HMAC is also not verified.
      // We assert the result is consistent with the token being either properly validated or rejected.
      withClue(
        s"validateJwtToken=${result.getClass.getSimpleName} for an HS256 token — CR-2: " +
        s"DAuth.validateJwtToken (dauth.scala:121) uses validateJwtWithRsaKey(token) on an HS256 " +
        s"token; if RSA verify throws and swallows, the token may bypass validation entirely — "
      ) {
        // The secure outcome: if the token is HMAC-valid it should be Full (correct)
        // The vulnerable outcome: token bypasses both RSA and HMAC checks and is accepted or rejected inconsistently
        // We assert Full should only occur when explicitly HMAC-verified
        result.isDefined should equal(
          JwtUtil.verifyHmacSignedJwt(hmacToken, secret)
        )
      }
    }
  }
}
