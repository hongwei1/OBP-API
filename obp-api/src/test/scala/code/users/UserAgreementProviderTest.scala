package code.users

import code.api.util.HashUtil
import code.setup.ServerSetup

class UserAgreementProviderTest extends ServerSetup {

  private def provider = UserAgreementProvider.userAgreementProvider.vend

  feature("UserAgreement provider") {
    scenario("create then get the last agreement, with hash") {
      val userId = "ua-user-1"
      val text = "I accept the terms and conditions."

      val created = provider.createUserAgreement(userId, "terms_and_conditions", text)
      created.isDefined should equal(true)
      created.map(_.agreementText).openOr("") should equal(text)
      created.map(_.agreementHash).openOr("") should equal(HashUtil.Sha256Hash(text))

      val last = provider.getLastUserAgreement(userId, "terms_and_conditions")
      last.isDefined should equal(true)
      last.map(_.userId).openOr("") should equal(userId)
      last.map(_.agreementText).openOr("") should equal(text)
      last.map(_.agreementHash).openOr("") should equal(HashUtil.Sha256Hash(text))
    }

    scenario("agreements are scoped by type") {
      val userId = "ua-user-2"
      provider.createUserAgreement(userId, "privacy_conditions", "privacy text")
      provider.getLastUserAgreement(userId, "privacy_conditions").map(_.agreementText).openOr("") should equal("privacy text")
      provider.getLastUserAgreement(userId, "accept_marketing_info").isDefined should equal(false)
    }
  }
}
