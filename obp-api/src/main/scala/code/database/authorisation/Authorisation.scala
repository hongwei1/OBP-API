package code.database.authorisation

import com.openbankproject.commons.model.AuthorisationTrait
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector


object Authorisations extends SimpleInjector {
  val authorisationProvider = new Inject(buildOne _) {}
  def buildOne: AuthorisationProvider = MappedAuthorisationProvider
}

trait AuthorisationProvider {
  def getAuthorizationByAuthorizationId(authorizationId: String): Box[AuthorisationTrait]
  def getAuthorizationByAuthorizationId(paymentId: String, authorizationId: String): Box[AuthorisationTrait]
  def getAuthorizationByPaymentId(paymentId: String): Box[List[AuthorisationTrait]]
  def getAuthorizationByConsentId(consentId: String): Box[List[AuthorisationTrait]]
  def createAuthorization(paymentId: String,
                          consentId: String, 
                          authenticationType: String, 
                          authenticationMethodId: String,
                          scaStatus: String,
                          challengeData: String
                         ): Box[AuthorisationTrait]
  def checkAnswer(paymentId: String, authorizationId: String, challengeData: String): Box[AuthorisationTrait]
}