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

import code.api.util.JwtUtil
import com.nimbusds.jose.crypto.RSASSASigner
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator
import com.nimbusds.jose.jwk.{JWKSet, RSAKey}
import com.nimbusds.jose.{JWSAlgorithm, JWSHeader}
import com.nimbusds.jwt.{JWTClaimsSet, SignedJWT}
import net.liftweb.common.Failure

import java.io.{File, FileWriter}
import java.util.Date

/**
 * SEC-2 — Circular `aud` validation in JwtUtil.validateIdToken.
 *
 * JwtUtil.validateIdToken (JwtUtil.scala:269-271) derives BOTH the expected issuer AND
 * the expected clientID directly from the token being validated:
 *   val aud      = getAudience(idToken).headOption.getOrElse("")
 *   val clientID = new ClientID(aud)
 *
 * This means any token whose signature verifies passes the audience check unconditionally
 * — the validator is configured to expect whatever the token itself claims. An attacker
 * who can generate a valid signature for ANY audience value (e.g. after stealing the
 * signing key, or in a key-confusion attack) can present a token with aud="attacker-app"
 * and it will be accepted even when the configured openid_connect_1.client_id is a
 * completely different value.
 *
 * A secure implementation reads the expected clientId from props:
 *   val expectedClientId = APIUtil.getPropsValue("openid_connect_1.client_id", "")
 *   val clientID = new ClientID(expectedClientId)
 * and rejects tokens whose aud does not match that configured value.
 *
 * The test asserts the secure outcome (Failure for a token with a mismatched aud), so it
 * is EXPECTED TO FAIL while the path is unguarded. Tagged SecurityVuln.
 */
class OAuthIdTokenAudienceTest extends SecurityVulnSetup {

  feature("validateIdToken must reject tokens whose aud does not match the configured client_id") {

    scenario("SEC-2: a token with aud=attacker-app must be rejected when client_id=obp-configured-client", SecurityVuln) {
      Given("an RSA key pair and a signed ID token whose aud is controlled by the attacker")
      val rsaKey: RSAKey = new RSAKeyGenerator(2048).keyID("sec2-test-key").generate()
      val signer         = new RSASSASigner(rsaKey)

      val now    = new Date()
      val expiry = new Date(now.getTime + 3600 * 1000L)

      val attackerAud    = "attacker-app"
      val configuredAud  = "obp-configured-client"

      val claims = new JWTClaimsSet.Builder()
        .issuer("https://test-issuer.sec2.example")
        .audience(attackerAud)
        .subject("sec2-test-subject")
        .issueTime(now)
        .expirationTime(expiry)
        .build()

      val header    = new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("sec2-test-key").build()
      val signedJWT = new SignedJWT(header, claims)
      signedJWT.sign(signer)
      val token = signedJWT.serialize()

      And("a JWK set served from a local temp file (no network required)")
      val publicKeys = new JWKSet(rsaKey.toPublicJWK)
      val jwkFile    = File.createTempFile("sec2-jwks-", ".json")
      jwkFile.deleteOnExit()
      val fw = new FileWriter(jwkFile)
      try { fw.write(publicKeys.toString) } finally { fw.close() }
      val jwkFileUrl = jwkFile.toURI.toURL.toString

      And(s"props set so the configured client_id is $configuredAud (different from token aud $attackerAud)")
      setPropsValues(
        "openid_connect_1.client_id" -> configuredAud
      )

      When("validateIdToken is called with the attacker's token")
      val result = JwtUtil.validateIdToken(token, jwkFileUrl)

      Then("the result must be Failure — the token's aud does not match the configured client_id")
      withClue(
        s"result=$result (expected Failure) — validateIdToken derives clientID = new ClientID(getAudience(token)) " +
        s"so a token with aud='$attackerAud' always satisfies its own audience check regardless of the " +
        s"configured '$configuredAud'; any signed token passes (JwtUtil.scala:269-271) — "
      ) {
        result should be(a[Failure])
      }
    }
  }
}
