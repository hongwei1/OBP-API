package code.accountaccessrequest

import code.setup.ServerSetup
import com.openbankproject.commons.model.enums.AccountAccessRequestStatus

class AccountAccessRequestProviderTest extends ServerSetup {

  private def provider = AccountAccessRequestTrait.accountAccessRequest.vend

  feature("AccountAccessRequest provider") {
    scenario("create, query by id/account/status/requestor/user-account-view, then update status") {
      val bankId = "aar-bank-1"
      val accountId = "aar-acc-1"
      val viewId = "owner"
      val requestor = "aar-requestor-1"
      val target = "aar-target-1"

      val created = provider.createAccountAccessRequest(bankId, accountId, viewId, isSystemView = true, requestor, target, "need access")
      created.isDefined should equal(true)
      val reqId = created.map(_.accountAccessRequestId).openOr("")
      reqId.replace("-", "").size should equal(32)
      created.map(_.status).openOr("") should equal(AccountAccessRequestStatus.INITIATED.toString)

      provider.getById(reqId).map(_.businessJustification).openOr("") should equal("need access")
      provider.getByAccount(bankId, accountId).openOr(Nil).size should equal(1)
      provider.getByAccountAndStatus(bankId, accountId, AccountAccessRequestStatus.INITIATED.toString).openOr(Nil).size should equal(1)
      provider.getByRequestorUserId(requestor).openOr(Nil).size should equal(1)
      provider.getByUserAccountView(target, bankId, accountId, viewId).isDefined should equal(true)

      When("we update the status to a checked state")
      val updated = provider.updateStatus(reqId, "GRANTED", "checker-1", "looks good")
      updated.map(_.status).openOr("") should equal("GRANTED")
      updated.map(_.checkerUserId).openOr("") should equal("checker-1")

      Then("it is no longer INITIATED for the user-account-view lookup")
      provider.getByUserAccountView(target, bankId, accountId, viewId).isDefined should equal(false)
      provider.getById(reqId).map(_.checkerComment).openOr("") should equal("looks good")
    }
  }
}
