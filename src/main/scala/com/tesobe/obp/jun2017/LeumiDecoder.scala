package com.tesobe.obp.jun2017

import com.tesobe.obp.{BasicBankAccount, Tn2TnuaBodedet}
import com.tesobe.obp.GetBankAccounts.getBasicBankAccountsForUser
import com.tesobe.obp.Nt1cMf.getBalance
import com.tesobe.obp.Nt1cTMf.getCompletedTransactions
import com.tesobe.obp.GetBankAccounts.base64EncodedSha256
import com.tesobe.obp.JoniMf.getMFToken
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
  //TODO: Look for better mapping function
  case class TransactionIdValues(amount: String, completedDate: String, newBalanceAmount: String)
  var mapTransactionIdToTransactionValues = Map[String, TransactionIdValues]()
  var mapTransactionValuesToTransactionId = Map[TransactionIdValues, String]()
  
  //Helper functions start here:---------------------------------------------------------------------------------------

  def getOrCreateAccountId(accountNr: String): String = {
    logger.debug(s"getOrCreateAccountId-accountNr($accountNr)")
    if (mapAccountNumberToAccountId.contains(accountNr)) { mapAccountNumberToAccountId(accountNr) }
    else {
      //TODO: Do random salting for production? Will lead to expired accountIds becoming invalid.
      val accountId = base64EncodedSha256(accountNr + "fjdsaFDSAefwfsalfid")
      mapAccountIdToAccountNumber += (accountId -> accountNr)
      mapAccountNumberToAccountId += (accountNr -> accountId)
      accountId
    }
  }
  
  def getOrCreateTransactionId(amount: String, completedDate: String,newBalanceAmount: String): String = {
    logger.debug(s"getOrCreateTransactionId for ($amount)($completedDate)($newBalanceAmount)")
    val transactionIdValues = TransactionIdValues(amount,completedDate, newBalanceAmount)
    if (mapTransactionValuesToTransactionId.contains(transactionIdValues)) {
      mapTransactionValuesToTransactionId(transactionIdValues)
    } else {
      val transactionId = base64EncodedSha256(amount + completedDate + newBalanceAmount)
      mapTransactionValuesToTransactionId += (transactionIdValues -> transactionId)
      mapTransactionIdToTransactionValues += (transactionId -> transactionIdValues)
      transactionId
    }
  } 

  def mapAdapterAccountToInboundAccountJun2017(userid: String, x: BasicBankAccount): InboundAccountJun2017 = {

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
    InboundAccountJun2017(errorCode = "",
      x.cbsToken,
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
      accountRoutingAddress = "")
  }
  def mapAdapterTransactionToInternalTransaction(userId: String, 
                                                 bankId: String,
                                                 accountId: String,
                                                 adapterTransaction: Tn2TnuaBodedet): InternalTransaction = {
    val amount = adapterTransaction.TN2_TNUA_BODEDET.TN2_SCHUM
    val completedDate = adapterTransaction.TN2_TNUA_BODEDET.TN2_TA_ERECH
    val newBalanceAmount = adapterTransaction.TN2_TNUA_BODEDET.TN2_ITRA
    InternalTransaction(
      //Base : "TN2_TSHUVA_TAVLAIT":"TN2_SHETACH_LE_SEND_NOSAF":"TN2_TNUOT":"TN2_PIRTEY_TNUA":["TN2_TNUA_BODEDET"                              
      errorCode = "",
      transactionId = getOrCreateTransactionId(amount,completedDate,newBalanceAmount), // Find some
      accountId = accountId, //accountId
      amount = amount, //:"TN2_SCHUM"
      bankId = "10", // 10 for now (Joni)
      completedDate = completedDate, //"TN2_TA_ERECH": // Date of value for
      counterpartyId = "counterpartyId",
      counterpartyName = "counterpartyName",
      currency = defaultCurrency, //ILS 
      description = adapterTransaction.TN2_TNUA_BODEDET.TN2_TEUR_PEULA, //"TN2_TEUR_PEULA":
      newBalanceAmount = newBalanceAmount,  //"TN2_ITRA":
      newBalanceCurrency = defaultCurrency, //ILS
      postedDate = adapterTransaction.TN2_TNUA_BODEDET.TN2_TA_IBUD, //"TN2_TA_IBUD": // Date of transaction
      `type` = adapterTransaction.TN2_TNUA_BODEDET.TN2_SUG_PEULA, //"TN2_SUG_PEULA"
      userId = userId //userId
    )
  }
  //Helperfunctions end here--------------------------------------------------------------------------------------------
  
  //Processorfunctions start here---------------------------------------------------------------------------------------

  override def getBanks(getBanks: GetBanks) = {
      Banks(getBanks.authInfo, List(InboundBank("", "10", "leumi","leumilogo","leumiurl")))
    }

  override def getBank(getBank: GetBank) = {
    BankWrapper(getBank.authInfo, Some(InboundBank("", "10", "leumi","leumilogo","leumiurl")))
  }

  
  def getBankAccountbyAccountId(getAccount: GetAccountbyAccountID): InboundBankAccount = {
    val username = "./src/test/resources/joni_result.json"
    //Not cached or invalid AccountId
    if (!mapAccountIdToAccountNumber.contains(getAccount.accountId)) {
      getBankAccounts(GetAccounts(getAccount.authInfo))
    }
    val accountNr = mapAccountIdToAccountNumber(getAccount.accountId)
    val mfAccounts = getBasicBankAccountsForUser(username)
    InboundBankAccount(AuthInfo(getAccount.authInfo.userId,
      getAccount.authInfo.username,
      mfAccounts.head.cbsToken),
      mapAdapterAccountToInboundAccountJun2017(username,mfAccounts.filter(x => x.accountNr == accountNr).head)
    )
  }
  
  def getBankAccountByAccountNumber(getAccount: GetAccountbyAccountNumber): InboundBankAccount = {
    val username = "./src/test/resources/joni_result.json"
    val mfAccounts = getBasicBankAccountsForUser(username)
    InboundBankAccount(AuthInfo(getAccount.authInfo.userId,
      getAccount.authInfo.username,
      mfAccounts.head.cbsToken),
      mapAdapterAccountToInboundAccountJun2017(username,mfAccounts.filter(x => 
      x.accountNr == getAccount.accountNumber).head))
  }

   def getBankAccounts(getAccountsInput: GetAccounts): InboundBankAccounts = {
    // userid is path to test json file
    val userid = "./src/test/resources/joni_result.json"
    val mfAccounts = getBasicBankAccountsForUser(userid)
    var result = new ListBuffer[InboundAccountJun2017]()
    for (i <- mfAccounts) {
      
      result += mapAdapterAccountToInboundAccountJun2017(userid, i)
      }
    InboundBankAccounts(AuthInfo(getAccountsInput.authInfo.userId,
      getAccountsInput.authInfo.username,
      mfAccounts.head.cbsToken), result.toList)
  }
  
  def getTransactions(getTransactionsRequest: GetTransactions): InboundTransactions = {
    val userid = "./src/test/resources/nt1c_T_result.json"
    val mfTransactions = getCompletedTransactions(userid)
    var result = new ListBuffer[InternalTransaction]
    for (i <- mfTransactions.TN2_TSHUVA_TAVLAIT.N2TshuvaTavlait.TN2_TNUOT.TN2_PIRTEY_TNUA) {
      result += mapAdapterTransactionToInternalTransaction(
        getTransactionsRequest.authInfo.userId,
        "10",
        getTransactionsRequest.accountId,
        i
      )
    }
      InboundTransactions(getTransactionsRequest.authInfo, result.toList)
  }
  
  def getTransaction(getTransactionRequest: GetTransaction): InboundTransaction = {
    val userid = "./src/test/resources/nt1c_T_result.json"
    val mfTransactions = getCompletedTransactions(userid)
    if (!mapTransactionIdToTransactionValues.contains(getTransactionRequest.transactionId)) {
      getTransactions(GetTransactions(
        getTransactionRequest.authInfo,
        getTransactionRequest.bankId,
        getTransactionRequest.accountId,
        ""
      ))
    }
    val transactionIdValues = mapTransactionIdToTransactionValues(getTransactionRequest.transactionId)
    InboundTransaction(getTransactionRequest.authInfo,  mapAdapterTransactionToInternalTransaction(
      getTransactionRequest.authInfo.userId,
      getTransactionRequest.bankId,
      getTransactionRequest.accountId,
      mfTransactions.TN2_TSHUVA_TAVLAIT.N2TshuvaTavlait.TN2_TNUOT.TN2_PIRTEY_TNUA.filter(x =>
        x.TN2_TNUA_BODEDET.TN2_SCHUM == transactionIdValues.amount &&
          x.TN2_TNUA_BODEDET.TN2_TA_ERECH == transactionIdValues.completedDate &&
          x.TN2_TNUA_BODEDET.TN2_ITRA == transactionIdValues.newBalanceAmount ).head))
    }
  
  def getToken(getTokenRequest: GetToken): InboundToken = {
    InboundToken(getTokenRequest.username, getMFToken("./src/test/resources/joni_result.json"))
  }


}



