package com.tesobe.obp.jun2017

import com.tesobe.obp.{BasicBankAccount, Tn2TnuaBodedet}
import com.tesobe.obp.GetBankAccounts.getBasicBankAccountsForUser
import com.tesobe.obp.Nt1cMf.getBalance
import com.tesobe.obp.Nt1cTMf.getCompletedTransactions
import com.tesobe.obp.GetBankAccounts.base64EncodedSha256
import com.typesafe.scalalogging.StrictLogging

import scala.collection.mutable.{ListBuffer, Map}


/**
  * Responsible for processing requests based on local example json files.
  *
  */
object LeumiDecoder extends Decoder with StrictLogging {
  
  val defaultCurrency = "ILS"
  
  var mapAccountIdToAccountNumber = Map[String, String]()
  var mapAccountNumberToAccountId= Map[String, String]()
  
  //Helper functions start here:---------------------------------------------------------------------------------------

  def getOrCreateAccountId(accountNr: String): String = {
    logger.debug(s"getOrCreateAccountId-accountNr($accountNr)")
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
      else if (hasViewerRights) {List("Auditor")}
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
      balanceCurrency = defaultCurrency,
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
  def mapAdapterTransactionToInternalTransaction(userId: String, 
                                                 bankId: String,
                                                 accountId: String,
                                                 adapterTransaction: Tn2TnuaBodedet): InternalTransaction = {
    InternalTransaction(
      //Base : "TN2_TSHUVA_TAVLAIT":"TN2_SHETACH_LE_SEND_NOSAF":"TN2_TNUOT":"TN2_PIRTEY_TNUA":["TN2_TNUA_BODEDET"                              
      errorCode = "errorcode",
      transactionId = "transactionId", // Find some
      accountId = accountId, //accountId
      amount = adapterTransaction.TN2_TNUA_BODEDET.TN2_SCHUM, //:"TN2_SCHUM"
      bankId = "10", // 10 for now (Joni)
      completedDate = adapterTransaction.TN2_TNUA_BODEDET.TN2_TA_ERECH, //"TN2_TA_ERECH": // Date of value for
      counterpartyId = "counterpartyId",
      counterpartyName = "counterpartyName",
      currency = defaultCurrency, //ILS 
      description = adapterTransaction.TN2_TNUA_BODEDET.TN2_TEUR_PEULA, //"TN2_TEUR_PEULA":
      newBalanceAmount = adapterTransaction.TN2_TNUA_BODEDET.TN2_ITRA,  //"TN2_ITRA":
      newBalanceCurrency = defaultCurrency, //ILS
      postedDate = adapterTransaction.TN2_TNUA_BODEDET.TN2_TA_IBUD, //"TN2_TA_IBUD": // Date of transaction
      `type` = adapterTransaction.TN2_TNUA_BODEDET.TN2_SUG_PEULA, //"TN2_SUG_PEULA"
      userId = userId //userId
    )
  }
  //Helperfunctions end here--------------------------------------------------------------------------------------------
  
  //Processorfunctions start here---------------------------------------------------------------------------------------
  
  
  def getBankAccountbyAccountId(getAccount: GetAccountbyAccountID): InboundBankAccount = {
    val username = "./src/test/resources/joni_result.json"
    //TODO 1, if there is no account, it will throw the exception 
    val accountNr = mapAccountIdToAccountNumber(getAccount.accountId)
    val mfAccounts = getBasicBankAccountsForUser(username)
    InboundBankAccount(getAccount.authInfo,  mapAdapterAccountToInboundAccountJune2017(username,mfAccounts.filter(x => x.accountNr == accountNr).head)) 
  }
  
  def getBankAccountByAccountNumber(getAccount: GetAccountbyAccountNumber): InboundBankAccount = {
    val username = "./src/test/resources/joni_result.json"
    val mfAccounts = getBasicBankAccountsForUser(username)
    InboundBankAccount(getAccount.authInfo,  mapAdapterAccountToInboundAccountJune2017(username,mfAccounts.filter(x => 
      x.accountNr == getAccount.accountNumber).head))
  }

   def getBankAccounts(getAccountsInput: GetAccounts): InboundBankAccounts = {
    // userid is path to test json file
    val userid = "./src/test/resources/joni_result.json"
    val mfAccounts = getBasicBankAccountsForUser(userid)
    var result = new ListBuffer[InboundAccountJune2017]()
    for (i <- mfAccounts) {
      
      result += mapAdapterAccountToInboundAccountJune2017(userid, i)
      }
    InboundBankAccounts(getAccountsInput.authInfo, result.toList)
  }
  
  def getTransactions(transactions: GetTransactions): InboundTransactions = {
    val userid = "./src/test/resources/nt1c_T_result.json"
    val mfTransactions = getCompletedTransactions(userid)
    var result = new ListBuffer[InternalTransaction]
    for (i <- mfTransactions.TN2_TSHUVA_TAVLAIT.N2TshuvaTavlait.TN2_TNUOT.TN2_PIRTEY_TNUA) {
      result += mapAdapterTransactionToInternalTransaction(
        transactions.authInfo.userId,
        "10",
        transactions.accountId,
        i
      )
    }
      InboundTransactions(transactions.authInfo, result.toList)
  }

}



