package code.api.v3_0_0

import _root_.net.liftweb.json.Serialization.write
import code.model.{CreateViewJson, UpdateViewJSON}
import code.setup.{APIResponse, DefaultUsers, ServerSetupWithTestData, User1AllPrivileges}
import code.api.util.APIUtil.OAuth.{Consumer, Token, _}
import code.api.v1_2_0.V120ServerSetup

import scala.util.Random.nextInt

/**
 * Created by Hongwei Zhang on 05/05/17.
 */
trait V300ServerSetup extends V120ServerSetup {

  override  def v1_2Request = baseRequest / "obp" / "v1.2"
  def v1_4Request = baseRequest / "obp" / "v1.4.0"
  def v2_0Request = baseRequest / "obp" / "v2.0.0"
  def v2_1Request = baseRequest / "obp" / "v2.1.0"
  def v2_2Request = baseRequest / "obp" / "v2.2.0"
  def v3_0Request = baseRequest / "obp" / "v3.0.0"
  
  
  override def randomPrivateAccount(bankId : String) : code.api.v1_2.AccountJSON = {
    val accountsJson = getPrivateAccounts(bankId, user1).body.extract[code.api.v1_2.AccountsJSON].accounts
    val randomPosition = nextInt(accountsJson.size)
    accountsJson(randomPosition)
  }
  
  override def getAPIInfo : APIResponse = {
    val request = v3_0Request
    makeGetRequest(request)
  }
  
  override def getBanksInfo : APIResponse  = {
    val request = v2_2Request / "banks"
    makeGetRequest(request)
  }
  
  override def getBankInfo(bankId : String) : APIResponse  = {
    val request = v2_2Request / "banks" / bankId
    makeGetRequest(request)
  }
  
  override def getPrivateAccounts(bankId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / "private" <@(consumerAndToken)
    makeGetRequest(request)
  }
  
  //All the V300 new endpoints
  override def getAccountViews(bankId : String, accountId : String, consumerAndToken: Option[(Consumer, Token)]): APIResponse = {
    val request = v3_0Request / "banks" / bankId / "accounts" / accountId / "views" <@(consumerAndToken)
    makeGetRequest(request)
  }
  
  override def postView(bankId: String, accountId: String, view: CreateViewJson, consumerAndToken: Option[(Consumer, Token)]): APIResponse = {
    val request = (v3_0Request / "banks" / bankId / "accounts" / accountId / "views").POST <@(consumerAndToken)
    makePostRequest(request, write(view))
  }
  
  override def putView(bankId: String, accountId: String, viewId : String, view: UpdateViewJSON, consumerAndToken: Option[(Consumer, Token)]): APIResponse = {
    val request = (v3_0Request / "banks" / bankId / "accounts" / accountId / "views" / viewId).PUT <@(consumerAndToken)
    makePutRequest(request, write(view))
  }

}