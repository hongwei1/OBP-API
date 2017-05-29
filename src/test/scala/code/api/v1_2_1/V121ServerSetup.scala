package code.api.v1_2_1

import _root_.net.liftweb.json.Serialization.write
import code.api.util.APIUtil.OAuth.{Consumer, Token, _}
import code.api.v1_2._
import code.model.{CreateViewJson, UpdateViewJSON, Consumer => OBPConsumer, Token => OBPToken}
import code.setup.{APIResponse, DefaultUsers, User1AllPrivileges}
import net.liftweb.json.{Extraction, compact, render}
import net.liftweb.util.Helpers.randomString
import org.scalatest.Tag

import _root_.net.liftweb.json.Serialization.write
import code.api.util.APIUtil
import code.api.util.APIUtil.OAuth._
import code.bankconnectors.Connector
import code.model.{Consumer => OBPConsumer, Token => OBPToken, _}
import code.views.Views
import net.liftweb.json.JsonAST.JString
import net.liftweb.json.JsonDSL._
import net.liftweb.json._
import net.liftweb.util.Helpers._
import _root_.net.liftweb.util.{Props, _}
import code.setup.{APIResponse, DefaultUsers, PrivateUser2Accounts, User1AllPrivileges}
import org.scalatest.Tag

import scala.util.Random._

import scala.util.Random.{nextBoolean, nextInt, _}

trait V121ServerSetup extends User1AllPrivileges with DefaultUsers with PrivateUser2Accounts{
  
  def v1_2Request = baseRequest / "obp" / "v1.2.1"
  
  val viewFields = List(
    "can_see_transaction_this_bank_account","can_see_transaction_other_bank_account",
    "can_see_transaction_metadata","can_see_transaction_label","can_see_transaction_amount",
    "can_see_transaction_type","can_see_transaction_currency","can_see_transaction_start_date",
    "can_see_transaction_finish_date","can_see_transaction_balance","can_see_comments",
    "can_see_narrative","can_see_tags","can_see_images","can_see_bank_account_owners",
    "can_see_bank_account_type","can_see_bank_account_balance","can_see_bank_account_currency",
    "can_see_bank_account_label","can_see_bank_account_national_identifier",
    "can_see_bank_account_swift_bic","can_see_bank_account_iban","can_see_bank_account_number",
    "can_see_bank_account_bank_name","can_see_other_account_national_identifier",
    "can_see_other_account_swift_bic","can_see_other_account_iban",
    "can_see_other_account_bank_name","can_see_other_account_number",
    "can_see_other_account_metadata","can_see_other_account_kind","can_see_more_info",
    "can_see_url","can_see_image_url","can_see_open_corporates_url","can_see_corporate_location",
    "can_see_physical_location","can_see_public_alias","can_see_private_alias","can_add_more_info",
    "can_add_url","can_add_image_url","can_add_open_corporates_url","can_add_corporate_location",
    "can_add_physical_location","can_add_public_alias","can_add_private_alias",
    "can_delete_corporate_location","can_delete_physical_location","can_edit_narrative",
    "can_add_comment","can_delete_comment","can_add_tag","can_delete_tag","can_add_image",
    "can_delete_image","can_add_where_tag","can_see_where_tag","can_delete_where_tag"
  )
  
  /************************* test tags ************************/
  
  /**
    * Example: To run tests with tag "getPermissions":
    * 	mvn test -D tagsToInclude=getPermissions
    *
    *  This is made possible by the scalatest maven plugin
    */
  
  object CurrentTest extends Tag("currentScenario")
  object API1_2 extends Tag("api1.2.1")
  object APIInfo extends Tag("apiInfo")
  object GetHostedBanks extends Tag("hostedBanks")
  object GetHostedBank extends Tag("getHostedBank")
  object GetBankAccounts extends Tag("getBankAccounts")
  object GetPublicBankAccounts extends Tag("getPublicBankAccounts")
  object GetPrivateBankAccounts extends Tag("getPrivateBankAccounts")
  //I would have prefered to change, e.g. GetBankAccounts to be GetBankAccountsForOneBank instead of
  //making a new tag GetBankAccountsForAllBanks, but I didn't as to preserve tag compatibility between api versions
  object GetBankAccountsForAllBanks extends Tag("getBankAccountsForAllBanks")
  object GetPublicBankAccountsForAllBanks extends Tag("getPublicBankAccountsForAllBanks")
  object GetPrivateBankAccountsForAllBanks extends Tag("getPrivateBankAccountsForAllBanks")
  object GetBankAccount extends Tag("getBankAccount")
  object GetViews extends Tag("getViews")
  object PostView extends Tag("postView")
  object PutView extends Tag("putView")
  object DeleteView extends Tag("deleteView")
  object GetPermissions extends Tag("getPermissions")
  object GetPermission extends Tag("getPermission")
  object PostPermission extends Tag("postPermission")
  object PostPermissions extends Tag("postPermissions")
  object DeletePermission extends Tag("deletePermission")
  object DeletePermissions extends Tag("deletePermissions")
  object GetCounterparties extends Tag("getCounterparties")
  object GetCounterparty extends Tag("getCounterparty")
  object GetCounterpartyMetadata extends Tag("getCounterpartyMetadata")
  object GetPublicAlias extends Tag("getPublicAlias")
  object PostPublicAlias extends Tag("postPublicAlias")
  object PutPublicAlias extends Tag("putPublicAlias")
  object DeletePublicAlias extends Tag("deletePublicAlias")
  object GetPrivateAlias extends Tag("getPrivateAlias")
  object PostPrivateAlias extends Tag("postPrivateAlias")
  object PutPrivateAlias extends Tag("putPrivateAlias")
  object DeletePrivateAlias extends Tag("deletePrivateAlias")
  object PostMoreInfo extends Tag("postMoreInfo")
  object PutMoreInfo extends Tag("putMoreInfo")
  object DeleteMoreInfo extends Tag("deleteMoreInfo")
  object PostURL extends Tag("postURL")
  object PutURL extends Tag("putURL")
  object DeleteURL extends Tag("deleteURL")
  object PostImageURL extends Tag("postImageURL")
  object PutImageURL extends Tag("putImageURL")
  object DeleteImageURL extends Tag("DeleteImageURL")
  object PostOpenCorporatesURL extends Tag("postOpenCorporatesURL")
  object PutOpenCorporatesURL extends Tag("putOpenCorporatesURL")
  object DeleteOpenCorporatesURL extends Tag("deleteOpenCorporatesURL")
  object PostCorporateLocation extends Tag("postCorporateLocation")
  object PutCorporateLocation extends Tag("putCorporateLocation")
  object DeleteCorporateLocation extends Tag("deleteCorporateLocation")
  object PostPhysicalLocation extends Tag("postPhysicalLocation")
  object PutPhysicalLocation extends Tag("putPhysicalLocation")
  object DeletePhysicalLocation extends Tag("deletePhysicalLocation")
  object GetTransactions extends Tag("getTransactions")
  object GetTransactionsWithParams extends Tag("getTransactionsWithParams")
  object GetTransaction extends Tag("getTransaction")
  object GetNarrative extends Tag("getNarrative")
  object PostNarrative extends Tag("postNarrative")
  object PutNarrative extends Tag("putNarrative")
  object DeleteNarrative extends Tag("deleteNarrative")
  object GetComments extends Tag("getComments")
  object PostComment extends Tag("postComment")
  object DeleteComment extends Tag("deleteComment")
  object GetTags extends Tag("getTags")
  object PostTag extends Tag("postTag")
  object DeleteTag extends Tag("deleteTag")
  object GetImages extends Tag("getImages")
  object PostImage extends Tag("postImage")
  object DeleteImage extends Tag("deleteImage")
  object GetWhere extends Tag("getWhere")
  object PostWhere extends Tag("postWhere")
  object PutWhere extends Tag("putWhere")
  object DeleteWhere extends Tag("deleteWhere")
  object GetTransactionAccount extends Tag("getTransactionAccount")
  object Payments extends Tag("payments")
  
  /********************* API test methods ********************/
  def randomViewPermalink(bankId: String, account: AccountJSON) : String = {
    val request = v1_2Request / "banks" / bankId / "accounts" / account.id / "views" <@(consumer, token1)
    val reply = makeGetRequest(request)
    val possibleViewsPermalinks = reply.body.extract[ViewsJSONV121].views.filterNot(_.is_public==true)
    val randomPosition = nextInt(possibleViewsPermalinks.size)
    possibleViewsPermalinks(randomPosition).id
  }
  
  def randomViewPermalinkButNotOwner(bankId: String, account: AccountJSON) : String = {
    val request = v1_2Request / "banks" / bankId / "accounts" / account.id / "views" <@(consumer, token1)
    val reply = makeGetRequest(request)
    val possibleViewsPermalinksWithoutOwner = reply.body.extract[ViewsJSONV121].views.filterNot(_.is_public==true).filterNot(_.id == "owner")
    val randomPosition = nextInt(possibleViewsPermalinksWithoutOwner.size)
    possibleViewsPermalinksWithoutOwner(randomPosition).id
  }
  
  def randomBank : String = {
    val banksJson = getBanksInfo.body.extract[BanksJSON]
    val randomPosition = nextInt(banksJson.banks.size)
    val bank = banksJson.banks(randomPosition)
    bank.id
  }
  
  def randomPublicAccount(bankId : String) : AccountJSON = {
    val accountsJson = getPublicAccounts(bankId).body.extract[AccountsJSON].accounts
    val randomPosition = nextInt(accountsJson.size)
    accountsJson(randomPosition)
  }
  
  def randomPrivateAccount(bankId : String) : AccountJSON = {
    val accountsJson = getPrivateAccounts(bankId, user1).body.extract[AccountsJSON].accounts
    val randomPosition = nextInt(accountsJson.size)
    accountsJson(randomPosition)
  }
  
  def privateAccountThatsNot(bankId: String, accId : String) : AccountJSON = {
    val accountsJson = getPrivateAccounts(bankId, user1).body.extract[AccountsJSON].accounts
    accountsJson.find(acc => acc.id != accId).getOrElse(fail(s"no private account that's not $accId"))
  }
  
  def randomAccountPermission(bankId : String, accountId : String) : PermissionJSON = {
    val persmissionsInfo = getAccountPermissions(bankId, accountId, user1).body.extract[PermissionsJSON]
    val randomPermission = nextInt(persmissionsInfo.permissions.size)
    persmissionsInfo.permissions(randomPermission)
  }
  
  def randomCounterparty(bankId : String, accountId : String, viewId : String): OtherAccountJSON = {
    val otherAccounts = getTheCounterparties(bankId, accountId, viewId, user1).body.extract[OtherAccountsJSON].other_accounts
    otherAccounts(nextInt(otherAccounts.size))
  }
  
  def randomLocation : LocationPlainJSON = {
    def sign = {
      val b = nextBoolean
      if(b) 1
      else -1
    }
    val longitude : Double = nextInt(180)*sign*nextDouble
    val latitude : Double = nextInt(90)*sign*nextDouble
    JSONFactory.createLocationPlainJSON(latitude, longitude)
  }
  
  def randomTransaction(bankId : String, accountId : String, viewId: String) : TransactionJSON = {
    val transactionsJson = getTransactions(bankId, accountId, viewId, user1).body.extract[TransactionsJSON].transactions
    val randomPosition = nextInt(transactionsJson.size)
    transactionsJson(randomPosition)
  }
  
  def randomViewsIdsToGrant(bankId : String, accountId : String) : List[String]= {
    //get the view ids of the available views on the bank accounts
    val viewsIds = getAccountViews(bankId, accountId, user1).body.extract[ViewsJSONV121].views.filterNot(_.is_public).map(_.id)
    //choose randomly some view ids to grant
    val (viewsIdsToGrant, _) = viewsIds.splitAt(nextInt(viewsIds.size) + 1)
    viewsIdsToGrant
  }
  
  def randomView(isPublic: Boolean, alias: String) : CreateViewJson = {
    CreateViewJson(
      name = randomString(3),
      description = randomString(3),
      is_public = isPublic,
      which_alias_to_use=alias,
      hide_metadata_if_alias_used = false,
      allowed_actions = viewFields
    )
  }
  
  def getAPIInfo : APIResponse = {
    val request = v1_2Request / "root"
    makeGetRequest(request)
  }
  
  def getBanksInfo : APIResponse  = {
    val request = v1_2Request / "banks"
    makeGetRequest(request)
  }
  
  def getBankInfo(bankId : String) : APIResponse  = {
    val request = v1_2Request / "banks" / bankId
    makeGetRequest(request)
  }
  
  def getPublicAccounts(bankId : String) : APIResponse= {
    val request = v1_2Request / "banks" / bankId / "accounts" / "public"
    makeGetRequest(request)
  }
  
  def getPrivateAccounts(bankId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / "private" <@(consumerAndToken)
    makeGetRequest(request)
  }
  
  def getBankAccountsForAllBanks(consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = v1_2Request / "accounts" <@(consumerAndToken)
    makeGetRequest(request)
  }
  
  def getPublicAccountsForAllBanks() : APIResponse= {
    val request = v1_2Request / "accounts" / "public"
    makeGetRequest(request)
  }
  
  def getPrivateAccountsForAllBanks(consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = v1_2Request / "accounts" / "private" <@(consumerAndToken)
    makeGetRequest(request)
  }
  
  def getBankAccounts(bankId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" <@(consumerAndToken)
    makeGetRequest(request)
  }
  
  def getPublicBankAccountDetails(bankId : String, accountId : String, viewId : String) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "account"
    makeGetRequest(request)
  }
  
  //get one bank account
  def getPrivateBankAccountDetails(bankId : String, accountId : String, viewId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "account" <@(consumerAndToken)
    makeGetRequest(request)
  }
  
  def getAccountViews(bankId : String, accountId : String, consumerAndToken: Option[(Consumer, Token)]): APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / "views" <@(consumerAndToken)
    makeGetRequest(request)
  }
  
  def postView(bankId: String, accountId: String, view: CreateViewJson, consumerAndToken: Option[(Consumer, Token)]): APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / "views").POST <@(consumerAndToken)
    makePostRequest(request, write(view))
  }
  
  def putView(bankId: String, accountId: String, viewId : String, view: UpdateViewJSON, consumerAndToken: Option[(Consumer, Token)]): APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / "views" / viewId).PUT <@(consumerAndToken)
    makePutRequest(request, write(view))
  }
  
  def deleteView(bankId: String, accountId: String, viewId: String, consumerAndToken: Option[(Consumer, Token)]): APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / "views" / viewId).DELETE <@(consumerAndToken)
    makeDeleteRequest(request)
  }
  
  def getAccountPermissions(bankId : String, accountId : String, consumerAndToken: Option[(Consumer, Token)]): APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / "permissions" <@(consumerAndToken)
    makeGetRequest(request)
  }
  
  def getUserAccountPermission(bankId : String, accountId : String, userId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / "permissions" / defaultProvider / userId <@(consumerAndToken)
    makeGetRequest(request)
  }
  
  def grantUserAccessToView(bankId : String, accountId : String, userId : String, viewId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse= {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / "permissions"/ defaultProvider / userId / "views" / viewId).POST <@(consumerAndToken)
    makePostRequest(request)
  }
  
  def grantUserAccessToViews(bankId : String, accountId : String, userId : String, viewIds : List[String], consumerAndToken: Option[(Consumer, Token)]) : APIResponse= {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / "permissions"/ defaultProvider / userId / "views").POST <@(consumerAndToken)
    val viewsJson = ViewIdsJson(viewIds)
    makePostRequest(request, write(viewsJson))
  }
  
  def revokeUserAccessToView(bankId : String, accountId : String, userId : String, viewId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse= {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / "permissions"/ defaultProvider / userId / "views" / viewId).DELETE <@(consumerAndToken)
    makeDeleteRequest(request)
  }
  
  def revokeUserAccessToAllViews(bankId : String, accountId : String, userId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse= {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / "permissions"/ defaultProvider / userId / "views").DELETE <@(consumerAndToken)
    makeDeleteRequest(request)
  }
  
  def getTheCounterparties(bankId : String, accountId : String, viewId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" <@(consumerAndToken)
    makeGetRequest(request)
  }
  
  def getTheCounterparty(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId <@(consumerAndToken)
    makeGetRequest(request)
  }
  
  def getMetadataOfOneCounterparty(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "metadata" <@(consumerAndToken)
    makeGetRequest(request)
  }
  
  def getThePublicAliasForOneCounterparty(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "public_alias" <@(consumerAndToken)
    makeGetRequest(request)
  }
  
  def postAPublicAliasForOneCounterparty(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, alias : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "public_alias").POST <@(consumerAndToken)
    val aliasJson = AliasJSON(alias)
    makePostRequest(request, write(aliasJson))
  }
  
  def updateThePublicAliasForOneCounterparty(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, alias : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "public_alias").PUT <@(consumerAndToken)
    val aliasJson = AliasJSON(alias)
    makePutRequest(request, write(aliasJson))
  }
  
  def deleteThePublicAliasForOneCounterparty(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "public_alias").DELETE <@(consumerAndToken)
    makeDeleteRequest(request)
  }
  
  def getThePrivateAliasForOneCounterparty(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "private_alias" <@(consumerAndToken)
    makeGetRequest(request)
  }
  
  def postAPrivateAliasForOneCounterparty(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, alias : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "private_alias").POST <@(consumerAndToken)
    val aliasJson = AliasJSON(alias)
    makePostRequest(request, write(aliasJson))
  }
  
  def updateThePrivateAliasForOneCounterparty(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, alias : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "private_alias").PUT <@(consumerAndToken)
    val aliasJson = AliasJSON(alias)
    makePutRequest(request, write(aliasJson))
  }
  
  def deleteThePrivateAliasForOneCounterparty(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "private_alias").DELETE <@(consumerAndToken)
    makeDeleteRequest(request)
  }
  
  def getMoreInfoForOneCounterparty(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, consumerAndToken: Option[(Consumer, Token)]) : String = {
    getMetadataOfOneCounterparty(bankId,accountId,viewId,otherBankAccountId,consumerAndToken).body.extract[OtherAccountMetadataJSON].more_info
  }
  
  def postMoreInfoForOneCounterparty(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, moreInfo : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "metadata" / "more_info").POST <@(consumerAndToken)
    val moreInfoJson = MoreInfoJSON(moreInfo)
    makePostRequest(request, write(moreInfoJson))
  }
  
  def updateMoreInfoForOneCounterparty(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, moreInfo : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "metadata" /"more_info").PUT <@(consumerAndToken)
    val moreInfoJson = MoreInfoJSON(moreInfo)
    makePutRequest(request, write(moreInfoJson))
  }
  
  def deleteMoreInfoForOneCounterparty(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "metadata" /"more_info").DELETE <@(consumerAndToken)
    makeDeleteRequest(request)
  }
  
  def getUrlForOneCounterparty(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, consumerAndToken: Option[(Consumer, Token)]) : String = {
    getMetadataOfOneCounterparty(bankId,accountId, viewId,otherBankAccountId,consumerAndToken).body.extract[OtherAccountMetadataJSON].URL
  }
  
  def postUrlForOneCounterparty(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, url : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "metadata" /"url").POST <@(consumerAndToken)
    val urlJson = UrlJSON(url)
    makePostRequest(request, write(urlJson))
  }
  
  def updateUrlForOneCounterparty(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, url : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "metadata" /"url").PUT <@(consumerAndToken)
    val urlJson = UrlJSON(url)
    makePutRequest(request, write(urlJson))
  }
  
  def deleteUrlForOneCounterparty(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "metadata" / "url").DELETE <@(consumerAndToken)
    makeDeleteRequest(request)
  }
  
  def getImageUrlForOneCounterparty(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, consumerAndToken: Option[(Consumer, Token)]) : String = {
    getMetadataOfOneCounterparty(bankId,accountId, viewId,otherBankAccountId,consumerAndToken).body.extract[OtherAccountMetadataJSON].image_URL
  }
  
  def postImageUrlForOneCounterparty(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, imageUrl : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "metadata" / "image_url").POST <@(consumerAndToken)
    val imageUrlJson = ImageUrlJSON(imageUrl)
    makePostRequest(request, write(imageUrlJson))
  }
  
  def updateImageUrlForOneCounterparty(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, imageUrl : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "metadata" / "image_url").PUT <@(consumerAndToken)
    val imageUrlJson = ImageUrlJSON(imageUrl)
    makePutRequest(request, write(imageUrlJson))
  }
  
  def deleteImageUrlForOneCounterparty(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "metadata" / "image_url").DELETE <@(consumerAndToken)
    makeDeleteRequest(request)
  }
  
  def getOpenCorporatesUrlForOneCounterparty(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, consumerAndToken: Option[(Consumer, Token)]) : String = {
    getMetadataOfOneCounterparty(bankId,accountId, viewId,otherBankAccountId, consumerAndToken).body.extract[OtherAccountMetadataJSON].open_corporates_URL
  }
  
  def postOpenCorporatesUrlForOneCounterparty(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, openCorporateUrl : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "metadata" / "open_corporates_url").POST <@(consumerAndToken)
    val openCorporateUrlJson = OpenCorporateUrlJSON(openCorporateUrl)
    makePostRequest(request, write(openCorporateUrlJson))
  }
  
  def updateOpenCorporatesUrlForOneCounterparty(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, openCorporateUrl : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "metadata" / "open_corporates_url").PUT <@(consumerAndToken)
    val openCorporateUrlJson = OpenCorporateUrlJSON(openCorporateUrl)
    makePutRequest(request, write(openCorporateUrlJson))
  }
  
  def deleteOpenCorporatesUrlForOneCounterparty(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "metadata" / "open_corporates_url").DELETE <@(consumerAndToken)
    makeDeleteRequest(request)
  }
  
  def getCorporateLocationForOneCounterparty(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, consumerAndToken: Option[(Consumer, Token)]) : LocationJSONV121 = {
    getMetadataOfOneCounterparty(bankId,accountId, viewId,otherBankAccountId, consumerAndToken).body.extract[OtherAccountMetadataJSON].corporate_location
  }
  
  def postCorporateLocationForOneCounterparty(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, corporateLocation : LocationPlainJSON, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "metadata" / "corporate_location").POST <@(consumerAndToken)
    val corpLocationJson = CorporateLocationJSON(corporateLocation)
    makePostRequest(request, write(corpLocationJson))
  }
  
  def updateCorporateLocationForOneCounterparty(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, corporateLocation : LocationPlainJSON, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "metadata" / "corporate_location").PUT <@(consumerAndToken)
    val corpLocationJson = CorporateLocationJSON(corporateLocation)
    makePutRequest(request, write(corpLocationJson))
  }
  
  def deleteCorporateLocationForOneCounterparty(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "metadata" / "corporate_location").DELETE <@(consumerAndToken)
    makeDeleteRequest(request)
  }
  
  def getPhysicalLocationForOneCounterparty(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, consumerAndToken: Option[(Consumer, Token)]) : LocationJSONV121 = {
    getMetadataOfOneCounterparty(bankId,accountId, viewId,otherBankAccountId, consumerAndToken).body.extract[OtherAccountMetadataJSON].physical_location
  }
  
  def postPhysicalLocationForOneCounterparty(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, physicalLocation : LocationPlainJSON, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "metadata" / "physical_location").POST <@(consumerAndToken)
    val physLocationJson = PhysicalLocationJSON(physicalLocation)
    makePostRequest(request, write(physLocationJson))
  }
  
  def updatePhysicalLocationForOneCounterparty(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, physicalLocation : LocationPlainJSON, consumerAndToken: Option[(Consumer, Token)])  : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "metadata" / "physical_location").PUT <@(consumerAndToken)
    val physLocationJson = PhysicalLocationJSON(physicalLocation)
    makePutRequest(request, write(physLocationJson))
  }
  
  def deletePhysicalLocationForOneCounterparty(bankId : String, accountId : String, viewId : String, otherBankAccountId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "other_accounts" / otherBankAccountId / "metadata" / "physical_location").DELETE <@(consumerAndToken)
    makeDeleteRequest(request)
  }
  
  def getTransactions(bankId : String, accountId : String, viewId : String, consumerAndToken: Option[(Consumer, Token)], params: List[(String, String)] = Nil): APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" <@(consumerAndToken)
    makeGetRequest(request, params)
  }
  
  def getTransaction(bankId : String, accountId : String, viewId : String, transactionId : String, consumerAndToken: Option[(Consumer, Token)]): APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "transaction" <@(consumerAndToken)
    makeGetRequest(request)
  }
  
  def postTransaction(bankId: String, accountId: String, viewId: String, paymentJson: MakePaymentJson, consumerAndToken: Option[(Consumer, Token)]): APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions").POST <@(consumerAndToken)
    makePostRequest(request, compact(render(Extraction.decompose(paymentJson))))
  }
  
  def getNarrativeForOneTransaction(bankId : String, accountId : String, viewId : String, transactionId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "narrative" <@(consumerAndToken)
    makeGetRequest(request)
  }
  
  def postNarrativeForOneTransaction(bankId : String, accountId : String, viewId : String, transactionId : String, narrative: String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "narrative").POST <@(consumerAndToken)
    val narrativeJson = TransactionNarrativeJSON(narrative)
    makePostRequest(request, write(narrativeJson))
  }
  
  def updateNarrativeForOneTransaction(bankId : String, accountId : String, viewId : String, transactionId : String, narrative: String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "narrative").PUT <@(consumerAndToken)
    val narrativeJson = TransactionNarrativeJSON(narrative)
    makePutRequest(request, write(narrativeJson))
  }
  
  def deleteNarrativeForOneTransaction(bankId : String, accountId : String, viewId : String, transactionId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "narrative").DELETE <@(consumerAndToken)
    makeDeleteRequest(request)
  }
  
  def getCommentsForOneTransaction(bankId : String, accountId : String, viewId : String, transactionId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "comments" <@(consumerAndToken)
    makeGetRequest(request)
  }
  
  def postCommentForOneTransaction(bankId : String, accountId : String, viewId : String, transactionId : String, comment: PostTransactionCommentJSON, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "comments").POST <@(consumerAndToken)
    makePostRequest(request, write(comment))
  }
  
  def deleteCommentForOneTransaction(bankId : String, accountId : String, viewId : String, transactionId : String, commentId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "comments" / commentId).DELETE <@(consumerAndToken)
    makeDeleteRequest(request)
  }
  
  def getTagsForOneTransaction(bankId : String, accountId : String, viewId : String, transactionId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "tags" <@(consumerAndToken)
    makeGetRequest(request)
  }
  
  def postTagForOneTransaction(bankId : String, accountId : String, viewId : String, transactionId : String, tag: PostTransactionTagJSON, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "tags").POST <@(consumerAndToken)
    makePostRequest(request, write(tag))
  }
  
  def deleteTagForOneTransaction(bankId : String, accountId : String, viewId : String, transactionId : String, tagId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "tags" / tagId).DELETE <@(consumerAndToken)
    makeDeleteRequest(request)
  }
  
  def getImagesForOneTransaction(bankId : String, accountId : String, viewId : String, transactionId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "images" <@(consumerAndToken)
    makeGetRequest(request)
  }
  
  def postImageForOneTransaction(bankId : String, accountId : String, viewId : String, transactionId : String, image: PostTransactionImageJSON, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "images").POST <@(consumerAndToken)
    makePostRequest(request, write(image))
  }
  
  def deleteImageForOneTransaction(bankId : String, accountId : String, viewId : String, transactionId : String, imageId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "images" / imageId).DELETE <@(consumerAndToken)
    makeDeleteRequest(request)
  }
  
  def getWhereForOneTransaction(bankId : String, accountId : String, viewId : String, transactionId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "where" <@(consumerAndToken)
    makeGetRequest(request)
  }
  
  def postWhereForOneTransaction(bankId : String, accountId : String, viewId : String, transactionId : String, where : LocationPlainJSON, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "where").POST <@(consumerAndToken)
    val whereJson = PostTransactionWhereJSON(where)
    makePostRequest(request, write(whereJson))
  }
  
  def updateWhereForOneTransaction(bankId : String, accountId : String, viewId : String, transactionId : String, where : LocationPlainJSON, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "where").PUT <@(consumerAndToken)
    val whereJson = PostTransactionWhereJSON(where)
    makePutRequest(request, write(whereJson))
  }
  
  def deleteWhereForOneTransaction(bankId : String, accountId : String, viewId : String, transactionId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = (v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "metadata" / "where").DELETE <@(consumerAndToken)
    makeDeleteRequest(request)
  }
  
  def getTheCounterpartyOfOneTransaction(bankId : String, accountId : String, viewId : String, transactionId : String, consumerAndToken: Option[(Consumer, Token)]) : APIResponse = {
    val request = v1_2Request / "banks" / bankId / "accounts" / accountId / viewId / "transactions" / transactionId / "other_account" <@(consumerAndToken)
    makeGetRequest(request)
  }
  
  
}
