package com.tesobe.obp.jun2017

import com.tesobe.obp.BasicBankAccount
import com.tesobe.obp.GetBankAccounts.getBasicBankAccountsForUser
import com.tesobe.obp.Nt1cMf.getBalance
import com.tesobe.obp.GetBankAccounts.base64EncodedSha256

import scala.collection.mutable.{ListBuffer, Map}


/**
  * Responsible for processing requests based on local example_import_jun2017.json file.
  *
  */
object LeumiDecoder extends Decoder {
  
  var mapAccountIdToAccountNumber = Map[String, String]()
  var mapAccountNumberToAccountId= Map[String, String]()

  def getOrCreateAccountId(accountNr: String): String = {
    if (mapAccountNumberToAccountId.contains(accountNr)) { mapAccountNumberToAccountId(accountNr) }
    else {
      val accountId = base64EncodedSha256(accountNr + "fjdsaFDSAefwfsalfid")
      mapAccountIdToAccountNumber += (accountId -> accountNr)
      mapAccountNumberToAccountId += (accountNr -> accountId)
      accountId
    }
  }

    def mapAdapterAccountToInboundAccountJune2017(userid: String, x: BasicBankAccount): InboundAccountJune2017 = {

    //TODO: This is by choice and needs verification
    //Create OwnerRights and accountViewer for result InboundAccount2017 creation
    val hasOwnerRights: Boolean = x.accountPermissions.canMakeExternalPayments || x.accountPermissions.canMakeInternalPayments
    val hasViewerRights: Boolean = x.accountPermissions.canSee
    val  viewsToGenerate  = {
      if (hasOwnerRights) {List("Owner")}
      else if (hasViewerRights) {List("Accountant")}
      else {List("")}
    }
    //Create Owner for result InboundAccount2017 creation
    val accountOwner = if (hasOwnerRights) {List(userid)} else {List("")}
    InboundAccountJune2017(
      errorCode = "errorcode",
      bankId = "10",
      branchId = x.branchNr,
      accountId = getOrCreateAccountId(x.accountNr),
      accountNumber = x.accountNr,
      accountType = x.accountType,
      balanceAmount = getBalance("./src/test/resources/nt1c_result.json"),
      balanceCurrency = "ILS",
      owners = accountOwner,
      viewsToGenerate = viewsToGenerate,
      bankRoutingScheme = "",
      bankRoutingAddress = "",
      branchRoutingScheme = "",
      branchRoutingAddress = "",
      accountRoutingScheme = "",
      accountRoutingAddress = ""
    )
  }
  
  def getBankAccountbyAccountId(getAccount: GetAccountbyAccountID): BankAccount = {
    val username = "./src/test/resources/joni_result.json"
    val accountNr = mapAccountIdToAccountNumber(getAccount.accountId)
    val mfAccounts = getBasicBankAccountsForUser(username)
    BankAccount(getAccount.authInfo,  mapAdapterAccountToInboundAccountJune2017(username,mfAccounts.filter(x => x.accountNr == accountNr).head)) 
  }
  
  def getBankAccountByAccountNumber(getAccount: GetAccountbyAccountNumber): BankAccount = {
    val username = "./src/test/resources/joni_result.json"
    val mfAccounts = getBasicBankAccountsForUser(username)
    BankAccount(getAccount.authInfo,  mapAdapterAccountToInboundAccountJune2017(username,mfAccounts.filter(x => 
      x.accountNr == getAccount.accountNumber).head))
  }

  override def getBankAccounts(getAccounts: GetAccounts): BankAccounts = {
    // userid is path to test json file
    val userid = "./src/test/resources/joni_result.json"
    val mfAccounts = getBasicBankAccountsForUser(userid)
    var result = new ListBuffer[InboundAccountJune2017]()
    for (i <- mfAccounts) {
      
      result += mapAdapterAccountToInboundAccountJune2017(userid, i)
      }
    BankAccounts(getAccounts.authInfo, result.toList)
  }
  

}



