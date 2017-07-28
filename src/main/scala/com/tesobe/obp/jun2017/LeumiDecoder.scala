package com.tesobe.obp.jun2017

import com.tesobe.obp.{BasicBankAccount, Tn2TnuaBodedet}
import com.tesobe.obp.GetBankAccounts.getBasicBankAccountsForUser
import com.tesobe.obp.Nt1cBMf.getBalance
import com.tesobe.obp.Nt1cTMf.getCompletedTransactions
import com.tesobe.obp.GetBankAccounts.base64EncodedSha256
import com.tesobe.obp.JoniMf.getMFToken
import com.typesafe.scalalogging.StrictLogging

import scala.collection.mutable.{ListBuffer, Map}


/**
  * Responsible for processing requests based on local example json files.
  *
  * Open Bank Project - Leumi Adapter
  * Copyright (C) 2016-2017, TESOBE Ltd.This program is free software: you can redistribute it and/or modify
  * it under the terms of the GNU Affero General Public License as published by
  * the Free Software Foundation, either version 3 of the License, or
  * (at your option) any later version.This program is distributed in the hope that it will be useful,
  * but WITHOUT ANY WARRANTY; without even the implied warranty of
  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  * GNU Affero General Public License for more details.You should have received a copy of the GNU Affero General Public License
  * along with this program.  If not, see <http://www.gnu.org/licenses/>.Email: contact@tesobe.com
  * TESOBE Ltd
  * Osloerstrasse 16/17
  * Berlin 13359, GermanyThis product includes software developed at TESOBE (http://www.tesobe.com/)
  * This software may also be distributed under a commercial license from TESOBE Ltd subject to separate terms.
  */
object LeumiDecoder extends Decoder with StrictLogging {
  
  val defaultCurrency = "ILS"
  //TODO: Replace with caching solution for production
  case class AccountValues (branchId: String,accountType: String, accountNumber:String)
  var mapAccountIdToAccountValues = Map[String, AccountValues]()
  var mapAccountNumberToAccountId= Map[String, String]()
  case class TransactionIdValues(amount: String, completedDate: String, newBalanceAmount: String)
  var mapTransactionIdToTransactionValues = Map[String, TransactionIdValues]()
  var mapTransactionValuesToTransactionId = Map[TransactionIdValues, String]()
  
  //Helper functions start here:---------------------------------------------------------------------------------------

  def getOrCreateAccountId(branchId: String, accountType: String, accountNumber: String): String = {
    logger.debug(s"getOrCreateAccountId-accountNr($accountNumber)")
    if (mapAccountNumberToAccountId.contains(accountNumber)) { mapAccountNumberToAccountId(accountNumber) }
    else {
      //TODO: Do random salting for production? Will lead to expired accountIds becoming invalid.
      val accountId = base64EncodedSha256(accountNumber + "fjdsaFDSAefwfsalfid")
      mapAccountIdToAccountValues += (accountId -> AccountValues(branchId, accountType, accountNumber))
      mapAccountNumberToAccountId += (accountNumber -> accountId)
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
    InboundAccountJune2017(errorCode = "errorcode",
      x.cbsToken,
      bankId = "10",
      branchId = x.branchNr,
      accountId = getOrCreateAccountId(x.branchNr, x.accountType, x.accountNr),
      accountNumber = x.accountNr,
      accountType = x.accountType,
      balanceAmount = getBalance(x.branchNr, x.accountType, x.accountNr, x.cbsToken),
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
    //Not cached or invalid AccountId
    if (!mapAccountIdToAccountValues.contains(getAccount.accountId)) {
      println("not mapped")
      getBankAccounts(GetAccounts(getAccount.authInfo))
    }
    val accountNr = mapAccountIdToAccountValues(getAccount.accountId).accountNumber
    val mfAccounts = getBasicBankAccountsForUser(getAccount.authInfo.username)
    InboundBankAccount(AuthInfo(getAccount.authInfo.userId,
      getAccount.authInfo.username,
      mfAccounts.head.cbsToken),
      mapAdapterAccountToInboundAccountJune2017(getAccount.authInfo.username,mfAccounts.filter(x => x.accountNr == accountNr ).head)
    )
  }
  
  def getBankAccountByAccountNumber(getAccount: GetAccountbyAccountNumber): InboundBankAccount = {
    val mfAccounts = getBasicBankAccountsForUser(getAccount.authInfo.username)
    InboundBankAccount(AuthInfo(getAccount.authInfo.userId,
      getAccount.authInfo.username,
      mfAccounts.head.cbsToken),
      //TODO: Error handling
      mapAdapterAccountToInboundAccountJune2017(getAccount.authInfo.username,mfAccounts.filter(x => 
      x.accountNr == getAccount.accountNumber).head))
  }

   def getBankAccounts(getAccountsInput: GetAccounts): InboundBankAccounts = {
    val mfAccounts = getBasicBankAccountsForUser(getAccountsInput.authInfo.username)
    var result = new ListBuffer[InboundAccountJune2017]()
    for (i <- mfAccounts) {
      
      result += mapAdapterAccountToInboundAccountJune2017(getAccountsInput.authInfo.username, i)
      }
    InboundBankAccounts(AuthInfo(getAccountsInput.authInfo.userId,
      //TODO: Error handling
      getAccountsInput.authInfo.username,
      mfAccounts.head.cbsToken), result.toList)
  }
  
  def getTransactions(getTransactionsRequest: GetTransactions): InboundTransactions = {
    //TODO: Error handling
    val accountValues = mapAccountIdToAccountValues(getTransactionsRequest.accountId)
    val mfTransactions = getCompletedTransactions(
      accountValues.branchId,
      accountValues.accountType,
      accountValues.accountNumber,
      //TODO: Get hardcoded parameters from North Side
      getTransactionsRequest.authInfo.cbsToken, List("2016","01","01"), List("2017","06","01"), "15")
    var result = new ListBuffer[InternalTransaction]
    for (i <- mfTransactions.TN2_TSHUVA_TAVLAIT.TN2_SHETACH_LE_SEND_NOSAF.TN2_TNUOT.TN2_PIRTEY_TNUA) {
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
    val allTransactions: List[InternalTransaction] = getTransactions(GetTransactions(
    getTransactionRequest.authInfo,
    getTransactionRequest.bankId, getTransactionRequest.accountId,
    ""
    )).data
    //TODO: Error handling
    val resultTransaction = allTransactions.filter(x => x.transactionId == getTransactionRequest.transactionId).head
    InboundTransaction(getTransactionRequest.authInfo, resultTransaction)
    
  }
  
  def getToken(getTokenRequest: GetToken): InboundToken = {
    InboundToken(getTokenRequest.username, getMFToken(getTokenRequest.username))
  }


}



