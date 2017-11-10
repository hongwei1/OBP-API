package com.tesobe.obp.june2017

import java.text.SimpleDateFormat
import java.util.TimeZone

import com.tesobe.obp.ErrorMessages.{NoCreditCard, _}
import com.tesobe.obp.GetBankAccounts.{base64EncodedSha256, getBasicBankAccountsForUser}
import com.tesobe.obp.JoniMf.{correctArrayWithSingleElement, getMFToken, replaceEmptyObjects}
import com.tesobe.obp.Nt1c3Mf.getNt1c3
import com.tesobe.obp.Nt1c4Mf.getNt1c4
import com.tesobe.obp.Nt1cBMf.getBalance
import com.tesobe.obp.Nt1cTMf.getNt1cT
import com.tesobe.obp.Ntbd1v105Mf.getNtbd1v105Mf
import com.tesobe.obp.Ntbd1v135Mf.getNtbd1v135Mf
import com.tesobe.obp.Ntbd2v050Mf.getNtbd2v050
import com.tesobe.obp.Ntbd2v105Mf.getNtbd2v105Mf
import com.tesobe.obp.Ntbd2v135Mf.getNtbd2v135Mf
import com.tesobe.obp.NtbdAv050Mf.getNtbdAv050
import com.tesobe.obp.NtbdBv050Mf.getNtbdBv050
import com.tesobe.obp.NtbdGv050Mf.getNtbdGv050
import com.tesobe.obp.NtbdIv050Mf.getNtbdIv050
import com.tesobe.obp.Ntg6AMf.getNtg6A
import com.tesobe.obp.Ntg6BMf.getNtg6B
import com.tesobe.obp.Ntg6IMf.getNtg6I
import com.tesobe.obp.Ntg6KMf.getNtg6K
import com.tesobe.obp.Ntib2Mf.getNtib2Mf
import com.tesobe.obp.Ntlv1Mf.getNtlv1Mf
import com.tesobe.obp.Ntlv7Mf.getNtlv7Mf
import com.tesobe.obp.NttfWMf.getNttfWMf
import com.tesobe.obp.Util.TransactionRequestTypes
import com.tesobe.obp._
import com.typesafe.scalalogging.StrictLogging
import net.liftweb.json.JsonAST.JValue
import net.liftweb.json.JsonParser.parse

import scala.collection.immutable.List
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

  implicit val formats = net.liftweb.json.DefaultFormats


  val defaultCurrency = "ILS"
  val defaultFilterFormat: SimpleDateFormat = new SimpleDateFormat("E MMM dd HH:mm:ss Z yyyy")
  val simpleTransactionDateFormat = new SimpleDateFormat("yyyyMMdd")
  simpleTransactionDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"))
  val simpleDateFormat: SimpleDateFormat = new SimpleDateFormat("dd/MM/yyyy")
  val simpleDayFormat: SimpleDateFormat = new SimpleDateFormat("dd")
  val simpleMonthFormat: SimpleDateFormat = new SimpleDateFormat("MM")
  val simpleYearFormat: SimpleDateFormat = new SimpleDateFormat("yyyy")
  val simpleLastLoginFormat: SimpleDateFormat = new SimpleDateFormat("yyyyMMddHHmmss")
  simpleLastLoginFormat.setTimeZone(TimeZone.getTimeZone("UTC"))

  val cachedJoni = TTLCache[String](10080) //1 week in minutes for now
  val cachedTransactionId = TTLCache[TransactionIdValues](10080)//1 week in minutes for now
  val cachedCounterparties = TTLCache[List[InternalCounterparty]](10080)
  

  case class AccountIdValues(branchId: String, accountType: String, accountNumber: String)
  case class TransactionIdValues(amount: String, completedDate: String, newBalanceAmount: String)


  //Helper functions start here:---------------------------------------------------------------------------------------



  def createAccountId(branchId: String, accountType: String, accountNumber: String): String = {
    logger.debug(s"createAccountId-accountNr($accountNumber)")
    base64EncodedSha256(branchId + accountType + accountNumber + config.getString("salt.global"))
  }
  
  def createCounterpartyId(counterpartyName: String,
                           counterpartyBankCode: String,
                           counterpartyBranchNr: String,
                           counterpartyAccountType: String,
                           counterpartyAccountNr:String) = {
    logger.debug(s"createCounterpartyId-counterpartyName($counterpartyName)")
    base64EncodedSha256(counterpartyName + counterpartyBankCode + counterpartyBranchNr + counterpartyAccountType + counterpartyAccountNr)
  }
  
  def createTransactionCounterpartyId(description: String, accountID: String) = {
    base64EncodedSha256(description + accountID + config.getString("salt.global"))
  }
  
  def getBasicBankAccountByAccountIdFromCachedJoni(username: String, accountId: String): BasicBankAccount = {
    val mfAccounts = getBasicBankAccountsForUser(username, true)
    mfAccounts.find(x => (base64EncodedSha256(x.branchNr + x.accountType + x.accountNr + config.getString("salt.global")) == accountId)).getOrElse(throw new InvalidAccountIdException(s"$InvalidAccountId accountId = $accountId"))
  }

  def createTransactionId(amount: String, completedDate: String, newBalanceAmount: String): String = {
    logger.debug(s"getOrCreateTransactionId for ($amount)($completedDate)($newBalanceAmount)")
    val transactionIdValues = TransactionIdValues(amount, completedDate, newBalanceAmount)
      val transactionId = base64EncodedSha256(amount + completedDate + newBalanceAmount)
     cachedTransactionId.set(transactionId,transactionIdValues)
      transactionId
      }

  def createCustomerId(username: String): String = {
    logger.debug(s"getOrCreateCustomerId for ($username)")
      val customerId = base64EncodedSha256(username + config.getString("salt.global"))
      customerId
      }


  def mapBasicBankAccountToInboundAccountJune2017(username: String, x: BasicBankAccount, iban: String, balance: String): InboundAccountJune2017 = {

    //Create OwnerRights and accountViewer for result InboundAccount2017 creation
    val hasOwnerRights: Boolean = x.accountPermissions.canMakeExternalPayments
    val hasAccountantRights = x.accountPermissions.canMakeInternalPayments
    val viewsToGenerate = {
      if (hasOwnerRights) {
        List("Owner")
      } else if (hasAccountantRights) {
        List("Accountant")
      } else {
        List("Auditor")
      }
    }
    //Create Owner for result InboundAccount2017 creation
    val accountOwner = if (hasOwnerRights) {
      List(username)
    } else {
      List("")
    }
    val accountRoutingScheme = if (iban.trim != "") "IBAN" else ""
    InboundAccountJune2017(
      errorCode = "",
      List(InboundStatusMessage("ESB", "Success", "0", "OK")), ////TODO, need to fill the coreBanking error
      x.cbsToken,
      bankId = "10",
      branchId = x.branchNr,
      accountId = createAccountId(x.branchNr, x.accountType, x.accountNr),
      accountNumber = x.accountNr,
      accountType = x.accountType,
      balanceAmount = balance,
      balanceCurrency = defaultCurrency,
      owners = accountOwner,
      viewsToGenerate = viewsToGenerate,
      bankRoutingScheme = "",
      bankRoutingAddress = "",
      branchRoutingScheme = "",
      branchRoutingAddress = "",
      accountRoutingScheme = accountRoutingScheme,
      accountRoutingAddress = iban)
  }

  def mapAdapterTransactionToInternalTransaction(userId: String,
                                                 bankId: String,
                                                 accountId: String,
                                                 adapterTransaction: Tn2TnuaBodedet): InternalTransaction = {

    // We can only get these six parameters from CBS. 
    val amount = adapterTransaction.TN2_TNUA_BODEDET.TN2_SCHUM //:"TN2_SCHUM"
    val completedDate = adapterTransaction.TN2_TNUA_BODEDET.TN2_TA_ERECH //"TN2_TA_ERECH": // Date of value for
    val newBalanceAmount = adapterTransaction.TN2_TNUA_BODEDET.TN2_ITRA //"TN2_ITRA":
    val description = adapterTransaction.TN2_TNUA_BODEDET.TN2_TEUR_PEULA //"TN2_TEUR_PEULA":
    val transactionType = adapterTransaction.TN2_TNUA_BODEDET.TN2_SUG_PEULA //"TN2_SUG_PEULA"
    val transactionProcessingDate = adapterTransaction.TN2_TNUA_BODEDET.TN2_TA_IBUD //"TN2_TA_IBUD": // Date of transaction

    InternalTransaction(
      errorCode = "",
      List(
        InboundStatusMessage("ESB", "Success", "0", "OK"), //TODO, need to fill the coreBanking error
        InboundStatusMessage("MF", "Success", "0", "OK") //TODO, need to fill the coreBanking error
      ),
      transactionId = createTransactionId(amount, completedDate, newBalanceAmount), // Find some
      accountId = accountId, //accountId
      amount = amount,
      bankId = "10", // 10 for now (Joni)
      completedDate = completedDate,
      counterpartyId = createTransactionCounterpartyId(description, accountId),
      counterpartyName = description,
      currency = defaultCurrency, //ILS 
      description = description,
      newBalanceAmount = newBalanceAmount,
      newBalanceCurrency = defaultCurrency, //ILS
      postedDate = transactionProcessingDate,
      `type` = transactionType,
      userId = userId //userId
    )
  }

  def getJoniMfUserFromCache(username: String) = {
    implicit val formats = net.liftweb.json.DefaultFormats
    val json = cachedJoni.get(username).getOrElse(throw new JoniCacheEmptyException(s"$JoniCacheEmpty The Joni Cache Input Key =$username "))
    logger.debug(s"getJoniMfUserFromCache.cacheJoni result:$json")
    val jsonAst: JValue = correctArrayWithSingleElement(parse(replaceEmptyObjects(json)))
    //Create case class object JoniMfUser
    jsonAst.extract[JoniMfUser]
  }

  def mapNt1c3ToTransactionRequest(transactions: Ta1TnuaBodedet, accountId: String): TransactionRequest = {
    TransactionRequest(
      id = TransactionRequestId(""),
      `type` = if (transactions.TA1_TNUA_BODEDET.TA1_IND_KARTIS_ASHRAI == "1") {
        "credit card"
      } else if (transactions.TA1_TNUA_BODEDET.TA1_IND_HOR_KEVA == "1") {
        "standing order"
      } else "", //nt1c3
      from = TransactionRequestAccount("10", accountId),
      details = TransactionRequestBody(
        TransactionRequestAccount("", ""),
        AmountOfMoney("ILS", transactions.TA1_TNUA_BODEDET.TA1_SCHUM_TNUA), //amount from Nt1c3
        description = transactions.TA1_TNUA_BODEDET.TA1_TEUR_TNUA), //description from NT1c3
      transaction_ids = "",
      status = "",
      start_date = simpleTransactionDateFormat.parse(transactions.TA1_TNUA_BODEDET.TA1_TA_TNUA), //nt1c3 date of request processing
      end_date = simpleTransactionDateFormat.parse(transactions.TA1_TNUA_BODEDET.TA1_TA_ERECH), //nt1c3 date of value for request
      challenge = TransactionRequestChallenge("", 0, ""),
      charge = TransactionRequestCharge("", AmountOfMoney("ILS", "0")),
      charge_policy = "",
      counterparty_id = CounterpartyId(createTransactionCounterpartyId(
        transactions.TA1_TNUA_BODEDET.TA1_TEUR_TNUA,
        accountId)),
      name = transactions.TA1_TNUA_BODEDET.TA1_TEUR_TNUA,
      this_bank_id = BankId("10"),
      this_account_id = AccountId(accountId),
      this_view_id = ViewId(""),
      other_account_routing_scheme = "",
      other_account_routing_address = "",
      other_bank_routing_scheme = "",
      other_bank_routing_address = "",
      is_beneficiary = false
    )
  }

  def mapNt1c4ToTransactionRequest(transactions: TnaTnuaBodedet, accountId: String): TransactionRequest = {
    TransactionRequest(
      id = TransactionRequestId(""),
      `type` = "notInNt1c4",
      from = TransactionRequestAccount("10", accountId),
      details = TransactionRequestBody(
        TransactionRequestAccount("", ""),
        AmountOfMoney("ILS", transactions.TNA_TNUA_BODEDET.TNA_SCHUM), //amount from Nt1c4
        description = transactions.TNA_TNUA_BODEDET.TNA_TEUR_PEULA), //description from NT1c4
      transaction_ids = "",
      status = "",
      start_date = simpleTransactionDateFormat.parse(transactions.TNA_TNUA_BODEDET.TNA_TA_BITZUA), //nt1c4 date of request processing
      end_date = simpleTransactionDateFormat.parse(transactions.TNA_TNUA_BODEDET.TNA_TA_ERECH), //nt1c4 date of value for request
      challenge = TransactionRequestChallenge("", 0, ""),
      charge = TransactionRequestCharge("", AmountOfMoney("ILS", "0")),
      charge_policy = "",
      counterparty_id = CounterpartyId(createTransactionCounterpartyId(
      transactions.TNA_TNUA_BODEDET.TNA_TEUR_PEULA,
        accountId)),
      name = transactions.TNA_TNUA_BODEDET.TNA_TEUR_PEULA,
      this_bank_id = BankId("10"),
      this_account_id = AccountId(accountId),
      this_view_id = ViewId(""),
      other_account_routing_scheme = "",
      other_account_routing_address = "",
      other_bank_routing_scheme = "",
      other_bank_routing_address = "",
      is_beneficiary = false
    )
  }


  def mapBasicBankAccountToCoreAccountJsonV300(account: BasicBankAccount): CoreAccount = {
    CoreAccount(
      id = createAccountId(account.branchNr, account.accountType, account.accountNr),
      label = "",
      bank_id = "10",
      account_routing = AccountRouting(scheme = "account_number", address = account.accountNr))
  }
  
  def mapAdapterCounterpartyToInternalCounterparty(CbsCounterparty: PmutPirteyMutav, OutboundCounterparty: InternalOutboundGetCounterparties): InternalCounterparty = {
    val iBan = CbsCounterparty.PMUT_IBAN.trim
    val accountRoutingScheme = if (iBan == "") "" else "IBAN"
    val counterpartyName = CbsCounterparty.PMUT_SHEM_MUTAV
    val counterpartyBankCode = CbsCounterparty.PMUT_BANK_MUTAV
    val counterpartyBranchNr = CbsCounterparty.PMUT_SNIF_MUTAV
    val counterpartyAccountType = CbsCounterparty.PMUT_SUG_CHEN_MUTAV
    val counterpartyAccountNr = CbsCounterparty.PMUT_CHEN_MUTAV
    
    val description = CbsCounterparty.PMUT_TEUR_MUTAV
    val englishName = CbsCounterparty.PMUT_SHEM_MUTAV_ANGLIT
    val englishDescription = CbsCounterparty.PMUT_TEUR_MUTAV_ANGLIT
    
    InternalCounterparty(
      errorCode = "",
      backendMessages = List(InboundStatusMessage("","","","")),
      createdByUserId = "",
      name = counterpartyName,
      thisBankId = OutboundCounterparty.thisBankId,
      thisAccountId = OutboundCounterparty.thisAccountId,
      thisViewId = OutboundCounterparty.viewId,
      counterpartyId = createCounterpartyId(
        counterpartyName,
        counterpartyBankCode,
        counterpartyBranchNr,
        counterpartyAccountType,
        counterpartyAccountNr),
      otherAccountRoutingScheme= "account_number",
      otherAccountRoutingAddress= counterpartyAccountNr,
      otherBankRoutingScheme= "bank_code",
      otherBankRoutingAddress= counterpartyBankCode,
      otherBranchRoutingScheme= "branch_number",
      otherBranchRoutingAddress= counterpartyBranchNr,
      isBeneficiary = true,
      description = description,
      otherAccountSecondaryRoutingScheme= accountRoutingScheme,
      otherAccountSecondaryRoutingAddress= iBan,
      bespoke = List(
        PostCounterpartyBespoke("englishName", englishName),
        PostCounterpartyBespoke("englishDescription", englishDescription)
    ))
  }

  //Helper functions end here--------------------------------------------------------------------------------------------

  //Processor functions start here---------------------------------------------------------------------------------------

  override def getBanks(getBanks: OutboundGetBanks) = {
    InboundGetBanks(getBanks.authInfo, List(InboundBank(
      "",
      List(InboundStatusMessage("ESB", "Success", "0", "OK")),
      "10", "leumi", "", "")))
  }

  override def getBank(getBank: OutboundGetBank) = {
    InboundGetBank(getBank.authInfo, InboundBank(
      "",
      List(InboundStatusMessage("ESB", "Success", "0", "OK")),
      "10", "leumi", "", ""))
  }


  def getBankAccountbyAccountId(getAccount: OutboundGetAccountbyAccountID): InboundGetAccountbyAccountID = {

    val username = getAccount.authInfo.username
    val account = getBasicBankAccountByAccountIdFromCachedJoni(username, getAccount.accountId)
    val cbsToken =  if (account.cbsToken != getAccount.authInfo.cbsToken) {
      throw new RuntimeException("Session Error")
    } else account.cbsToken
    val ntib2Call = getNtib2Mf(
      account.branchNr,
      account.accountType,
      account.accountNr,
      username,
      cbsToken
    )
    val iban = ntib2Call.SHETACHTCHUVA.TS00_PIRTEY_TCHUVA.TS00_TV_TCHUVA.TS00_NIGRERET_TCHUVA.TS00_IBAN
    val balance = getBalance(username, account.branchNr, account.accountType, account.accountNr, cbsToken)

    InboundGetAccountbyAccountID(AuthInfo(getAccount.authInfo.userId,
      getAccount.authInfo.username,
      account.cbsToken),
      mapBasicBankAccountToInboundAccountJune2017(username, account, iban, balance)
    )
  }

  def checkBankAccountExists(getAccount: OutboundCheckBankAccountExists): InboundGetAccountbyAccountID = {

    val account = getBasicBankAccountByAccountIdFromCachedJoni(getAccount.authInfo.username, getAccount.accountId)
    val iban = ""
    val cbsToken =  if (account.cbsToken != getAccount.authInfo.cbsToken) {
      throw new RuntimeException("Session Error")
    } else account.cbsToken
    InboundGetAccountbyAccountID(AuthInfo(getAccount.authInfo.userId,
      getAccount.authInfo.username,
      cbsToken),
      mapBasicBankAccountToInboundAccountJune2017(getAccount.authInfo.username, account, "", "0")
    )
  }


  def getBankAccounts(getAccountsInput: OutboundGetAccounts): InboundGetAccounts = {
    logger.debug("Enter getBankAccounts")
    val mfAccounts = getBasicBankAccountsForUser(getAccountsInput.authInfo.username, !getAccountsInput.callMfFlag)
    var result = new ListBuffer[InboundAccountJune2017]()
    for (i <- mfAccounts) {

      result += mapBasicBankAccountToInboundAccountJune2017(getAccountsInput.authInfo.username, i, "", "0")
    }
    InboundGetAccounts(AuthInfo(getAccountsInput.authInfo.userId,
      //TODO: Error handling
      getAccountsInput.authInfo.username,
      mfAccounts.headOption.getOrElse(
        throw new Exception("No Accounts for username: " + getAccountsInput.authInfo.username)).cbsToken), result.toList)
  }

    def getCoreBankAccounts(getCoreBankAccounts: OutboundGetCoreBankAccounts): InboundGetCoreBankAccounts = {
      val inputAccountIds = getCoreBankAccounts.bankIdAccountIds.map(_.accountId.value)
      val accounts = getBasicBankAccountsForUser(getCoreBankAccounts.authInfo.username, true)
  
      val result = new ListBuffer[InternalInboundCoreAccount]
      for (i <- inputAccountIds) {
        result += InternalInboundCoreAccount(
          errorCode = "",
          backendMessages = List(
            InboundStatusMessage("ESB", "Success", "0", "OK"),
            InboundStatusMessage("MF", "Success", "0", "OK")),
          id = i,
          label = "",
          bank_id = "10",
          account_routing = AccountRouting(scheme = "account_number", address = 
            accounts.find(x => (base64EncodedSha256(x.branchNr + x.accountType + x.accountNr + config.getString("salt.global")) == i)).getOrElse(throw new Exception("AccountId does not exist")).accountNr)
        )
      }
      InboundGetCoreBankAccounts(getCoreBankAccounts.authInfo, result.toList)
  }

  def getTransactions(getTransactionsRequest: OutboundGetTransactions): InboundGetTransactions = {
    //TODO: Error handling
    val account = getBasicBankAccountByAccountIdFromCachedJoni(getTransactionsRequest.authInfo.username, getTransactionsRequest.accountId)
    val fromDay = simpleDayFormat.format(defaultFilterFormat.parse(getTransactionsRequest.fromDate))
    val fromMonth = simpleMonthFormat.format(defaultFilterFormat.parse(getTransactionsRequest.fromDate))
    val fromYear = simpleYearFormat.format(defaultFilterFormat.parse(getTransactionsRequest.fromDate))
    val toDay = simpleDayFormat.format(defaultFilterFormat.parse(getTransactionsRequest.toDate))
    val toMonth = simpleMonthFormat.format(defaultFilterFormat.parse(getTransactionsRequest.toDate))
    val toYear = simpleYearFormat.format(defaultFilterFormat.parse(getTransactionsRequest.toDate))
    val cbsToken = if (account.cbsToken != getTransactionsRequest.authInfo.cbsToken) {
      throw new RuntimeException("Session Error")
    } else account.cbsToken

    val mfTransactions = getNt1cT(
      getTransactionsRequest.authInfo.username,
      account.branchNr,
      account.accountType,
      account.accountNr,
      cbsToken, List(fromYear, fromMonth, fromDay), List(toYear, toMonth, toDay), getTransactionsRequest.limit.toString)

    mfTransactions match {
      case Right(x) =>
        var result = new ListBuffer[InternalTransaction]
        for (i <- x.TN2_TSHUVA_TAVLAIT.TN2_SHETACH_LE_SEND_NOSAF.TN2_TNUOT.TN2_PIRTEY_TNUA) {
          result += mapAdapterTransactionToInternalTransaction(
            getTransactionsRequest.authInfo.userId,
            "10",
            getTransactionsRequest.accountId,
            i
          )
        }
        InboundGetTransactions(getTransactionsRequest.authInfo, result.toList)
      case Left(x) => 
        InboundGetTransactions(getTransactionsRequest.authInfo, List(InternalTransaction("backend error",List(InboundStatusMessage(
          "ESB",
          "Failure",
          x.PAPIErrorResponse.esbHeaderResponse.responseStatus.callStatus,
          x.PAPIErrorResponse.esbHeaderResponse.responseStatus.errorDesc.getOrElse("")
        )), "","","","","","","","","","","","","","")))
        
    }
  }

  def getTransaction(getTransactionRequest: OutboundGetTransaction): InboundGetTransaction = {
    logger.debug(s"get Transaction for ($getTransactionRequest)")
    val allTransactions: List[InternalTransaction] = {

      val transactionDate: String = cachedTransactionId.get(getTransactionRequest.transactionId).getOrElse(
        throw new Exception("Invalid TransactionId")
      ).completedDate
      val simpleTransactionDate = defaultFilterFormat.format(simpleTransactionDateFormat.parse(transactionDate))
      getTransactions(OutboundGetTransactions(getTransactionRequest.authInfo,
        getTransactionRequest.bankId,
        getTransactionRequest.accountId,
        50,
        simpleTransactionDate, simpleTransactionDate
      )).data

    }

    //TODO: Error handling
    val resultTransaction = allTransactions.find(x => x.transactionId == getTransactionRequest.transactionId).getOrElse(throw new Exception("Invalid TransactionID"))
    InboundGetTransaction(getTransactionRequest.authInfo, resultTransaction)

  }

  def createTransaction(createTransactionRequest: OutboundCreateTransaction): InboundCreateTransactionId = {
    logger.debug(s"LeumiDecoder-createTransaction input: ($createTransactionRequest)")
    // As to this page: https://github.com/OpenBankProject/OBP-Adapter_Leumi/wiki/NTBD_1_135#-these-parameters-have-to-come-from-the-api
    // OBP-API will provide: four values:
    val account = getBasicBankAccountByAccountIdFromCachedJoni(createTransactionRequest.authInfo.username, createTransactionRequest.fromAccountId)
    val branchId = account.branchNr
    val accountNumber = account.accountNr
    val accountType = account.accountType
    val username = createTransactionRequest.authInfo.username
    val cbsToken =  if (account.cbsToken != createTransactionRequest.authInfo.cbsToken) {
      throw new RuntimeException("Session Error")
    } else account.cbsToken
    val transactionNewId = "" //as we cannot determine the transactionid at creation, this will always be empty

    if (createTransactionRequest.transactionRequestType == (TransactionRequestTypes.TRANSFER_TO_PHONE.toString)) {
      val transactionRequestBodyPhoneToPhoneJson = createTransactionRequest.transactionRequestCommonBody.asInstanceOf[TransactionRequestBodyTransferToPhoneJson]
      val senderPhoneNumber = transactionRequestBodyPhoneToPhoneJson.from.mobile_phone_number
      val receiverPhoneNumber = transactionRequestBodyPhoneToPhoneJson.to.mobile_phone_number
      val transactionDescription = transactionRequestBodyPhoneToPhoneJson.description
      val transactionMessage = transactionRequestBodyPhoneToPhoneJson.message
      val transactionAmount = transactionRequestBodyPhoneToPhoneJson.value.amount

      val callNtbd1_135 = getNtbd1v135Mf(branch = branchId,
        accountType,
        accountNumber,
        username,
        cbsToken,
        mobileNumberOfMoneySender = senderPhoneNumber,
        mobileNumberOfMoneyReceiver = receiverPhoneNumber,
        description = transactionDescription,
        transferAmount = transactionAmount)
      match {
        case Right(x) =>
          val callNtbd2_135 = getNtbd2v135Mf(branchId,
            accountType,
            accountNumber,
            username,
            cbsToken,
            ntbd1v135_Token = x.P135_BDIKAOUT.P135_TOKEN,
            nicknameOfMoneySender = transactionRequestBodyPhoneToPhoneJson.from.nickname,
            messageToMoneyReceiver = transactionMessage)
          match {
            case Right(y) =>
              InboundCreateTransactionId(createTransactionRequest.authInfo,
                InternalTransactionId("", List(InboundStatusMessage("ESB", "Success", "0", "OK")),
                  transactionNewId))
            case Left(y) =>
              InboundCreateTransactionId(createTransactionRequest.authInfo,
                InternalTransactionId("", List(InboundStatusMessage(
                  "ESB",
                  "Failure",
                  y.PAPIErrorResponse.esbHeaderResponse.responseStatus.callStatus,
                  y.PAPIErrorResponse.esbHeaderResponse.responseStatus.errorDesc.getOrElse("")
                )),
                  transactionNewId))
          }
        case Left(x) =>
          InboundCreateTransactionId(createTransactionRequest.authInfo,
            InternalTransactionId("", List(InboundStatusMessage(
              "ESB",
              "Failure",
              x.PAPIErrorResponse.esbHeaderResponse.responseStatus.callStatus,
              x.PAPIErrorResponse.esbHeaderResponse.responseStatus.errorDesc.getOrElse("")
            )),
              transactionNewId))

      }


    } else if (createTransactionRequest.transactionRequestType == (TransactionRequestTypes.TRANSFER_TO_ATM.toString)) {
      val transactionRequestBodyTransferToAtmJson = createTransactionRequest.transactionRequestCommonBody.asInstanceOf[TransactionRequestBodyTransferToAtmJson]
      val transactionAmount = transactionRequestBodyTransferToAtmJson.value.amount
      val callNttfW = getNttfWMf(branchId, accountType, accountNumber, cbsToken)
      val cardData = callNttfW.PELET_NTTF_W.P_PRATIM.P_PIRTEY_KARTIS.find(x => x.P_TIKRAT_KARTIS >= transactionAmount).getOrElse(
        throw new RuntimeException(NoCreditCard)
      )
      val callNtbd1v105 = getNtbd1v105Mf(
        branch = branchId,
        accountType = accountType,
        accountNumber = accountNumber,
        cbsToken = cbsToken,
        cardNumber = cardData.P_MISPAR_KARTIS,
        cardExpirationDate = cardData.P_TOKEF_KARTIS,
        cardWithdrawalLimit = cardData.P_TIKRAT_KARTIS,
        mobileNumberOfMoneySender = transactionRequestBodyTransferToAtmJson.from.mobile_phone_number,
        amount = transactionAmount,
        description = transactionRequestBodyTransferToAtmJson.description,
        idNumber = transactionRequestBodyTransferToAtmJson.to.kyc_document.number,
        idType = transactionRequestBodyTransferToAtmJson.to.kyc_document.`type`,
        nameOfMoneyReceiver = transactionRequestBodyTransferToAtmJson.to.legal_name,
        birthDateOfMoneyReceiver = transactionRequestBodyTransferToAtmJson.to.date_of_birth,
        mobileNumberOfMoneyReceiver = transactionRequestBodyTransferToAtmJson.to.mobile_phone_number)
      match {
        case Right(x) =>
          val callNtbd2v105 = getNtbd2v105Mf(
            branchId,
            accountType,
            accountNumber,
            cbsToken,
            ntbd1v105Token = x.P135_BDIKAOUT.P135_TOKEN,
            nicknameOfSender = transactionRequestBodyTransferToAtmJson.from.nickname,
            messageToReceiver = transactionRequestBodyTransferToAtmJson.message)
          match {
            case Right(y) =>
              InboundCreateTransactionId(createTransactionRequest.authInfo,
                InternalTransactionId("", List(InboundStatusMessage(
                  "ESB",
                  "Success",
                  y.PELET_1352.esbHeaderResponse.responseStatus.callStatus,
                  y.PELET_1352.esbHeaderResponse.responseStatus.errorDesc.getOrElse(""))),
                  transactionNewId))
            case Left(y) =>
              InboundCreateTransactionId(createTransactionRequest.authInfo,
                InternalTransactionId("", List(InboundStatusMessage(
                  "ESB",
                  "Failure",
                  y.PAPIErrorResponse.esbHeaderResponse.responseStatus.callStatus,
                  y.PAPIErrorResponse.esbHeaderResponse.responseStatus.errorDesc.getOrElse(""))),
                  transactionNewId))
          }
        case Left(x) =>
          InboundCreateTransactionId(createTransactionRequest.authInfo,
            InternalTransactionId("", List(InboundStatusMessage(
              "ESB",
              "Failure",
              x.PAPIErrorResponse.esbHeaderResponse.responseStatus.callStatus,
              x.PAPIErrorResponse.esbHeaderResponse.responseStatus.errorDesc.getOrElse(""))),
              transactionNewId))

      }


    } else if (createTransactionRequest.transactionRequestType == (TransactionRequestTypes.TRANSFER_TO_ACCOUNT.toString)) {
      val transactionRequestBodyTransferToAccountJson = createTransactionRequest.transactionRequestCommonBody.asInstanceOf[TransactionRequestBodyTransferToAccount]

      val callNtbdAv050 = getNtbdAv050(branchId,
        accountType,
        accountNumber,
        cbsToken,
        transactionRequestBodyTransferToAccountJson.transfer_type,
        transferDateInFuture = transactionRequestBodyTransferToAccountJson.future_date
      ) match {
        case Right(a) =>

          val transferToAccountToken = a.P050_BDIKACHOVAOUT.P050_TOKEN_OUT

          val callNtdBv050 = getNtbdBv050(branchId,
            accountType,
            accountNumber,
            cbsToken,
            ntbdAv050Token = transferToAccountToken,
            toAccountBankId = transactionRequestBodyTransferToAccountJson.to.bank_code,
            toAccountBranchId = transactionRequestBodyTransferToAccountJson.to.branch_number,
            toAccountAccountNumber = transactionRequestBodyTransferToAccountJson.to.account.number,
            toAccountIban = transactionRequestBodyTransferToAccountJson.to.account.iban,
            transactionAmount = transactionRequestBodyTransferToAccountJson.value.amount,
            description = transactionRequestBodyTransferToAccountJson.description,
            referenceNameOfTo = transactionRequestBodyTransferToAccountJson.to.name
          ) match {
            case Right(b) =>

              val callNtbdIv050 = getNtbdIv050(
                branchId,
                accountType,
                accountNumber,
                cbsToken,
                ntbdAv050Token = transferToAccountToken,
                transactionAmount = transactionRequestBodyTransferToAccountJson.value.amount
              ) match {
                case Right(c) if c.P050_BDIKAZCHUTOUT.P050_MAHADURA_101.P050_KOD_ISHUR == "3" =>
                  throw new RuntimeException("Not permitted")

                case Right(c) =>
                  
                  val callNtbdGv050 = getNtbdGv050(
                    branchId,
                    accountType,
                    accountNumber,
                    cbsToken,
                    ntbdAv050Token = transferToAccountToken,
                    //TODO: check with leumi if bankID 10 implies leumi code 1 here
                    bankTypeOfTo = if (transactionRequestBodyTransferToAccountJson.to.bank_code == "10") "0" else "1"
                  ) match {
                    case Right(d) =>

                      val callNtbd2v050 = getNtbd2v050(
                        branchId,
                        accountType,
                        accountNumber,
                        cbsToken,
                        username,
                        ntbdAv050Token = transferToAccountToken,
                        ntbdAv050fromAccountOwnerName = a.P050_BDIKACHOVAOUT.P050_SHEM_HOVA_ANGLIT
                      ) match {
                        case Right(e) =>
                          InboundCreateTransactionId(createTransactionRequest.authInfo,
                            InternalTransactionId("", List(InboundStatusMessage("ESB", "Success", "0", "OK")),
                              transactionNewId))
                        case Left(e) =>
                          InboundCreateTransactionId(createTransactionRequest.authInfo,
                            InternalTransactionId("", List(InboundStatusMessage(
                              "ESB",
                              "Failure",
                              e.PAPIErrorResponse.esbHeaderResponse.responseStatus.callStatus,
                              e.PAPIErrorResponse.esbHeaderResponse.responseStatus.errorDesc.getOrElse(""))),
                              transactionNewId))
                      }
                    case Left(d) =>
                      InboundCreateTransactionId(createTransactionRequest.authInfo,
                        InternalTransactionId("", List(InboundStatusMessage(
                          "ESB",
                          "Failure",
                          d.PAPIErrorResponse.esbHeaderResponse.responseStatus.callStatus,
                          d.PAPIErrorResponse.esbHeaderResponse.responseStatus.errorDesc.getOrElse(""))),
                          transactionNewId))
                  }
                case Left(c) =>
                  InboundCreateTransactionId(createTransactionRequest.authInfo,
                    InternalTransactionId("", List(InboundStatusMessage(
                      "ESB",
                      "Failure",
                      c.PAPIErrorResponse.esbHeaderResponse.responseStatus.callStatus,
                      c.PAPIErrorResponse.esbHeaderResponse.responseStatus.errorDesc.getOrElse(""))),
                      transactionNewId))
              }
            case Left(b) =>
              InboundCreateTransactionId(createTransactionRequest.authInfo,
                InternalTransactionId("", List(InboundStatusMessage(
                  "ESB",
                  "Failure",
                  b.PAPIErrorResponse.esbHeaderResponse.responseStatus.callStatus,
                  b.PAPIErrorResponse.esbHeaderResponse.responseStatus.errorDesc.getOrElse(""))),
                  transactionNewId))
          }
        case Left(a) =>
          InboundCreateTransactionId(createTransactionRequest.authInfo,
            InternalTransactionId("", List(InboundStatusMessage(
              "ESB",
              "Failure",
              a.PAPIErrorResponse.esbHeaderResponse.responseStatus.callStatus,
              a.PAPIErrorResponse.esbHeaderResponse.responseStatus.errorDesc.getOrElse(""))),
              transactionNewId))
      }
      InboundCreateTransactionId(createTransactionRequest.authInfo,
        InternalTransactionId("", List(InboundStatusMessage("ESB", "Success", "0", "OK")),
          transactionNewId))
    } else if (createTransactionRequest.transactionRequestType == (TransactionRequestTypes.COUNTERPARTY.toString)) {
      val transactionRequestBodyPhoneToPhoneJson = createTransactionRequest.transactionRequestCommonBody.asInstanceOf[TransactionRequestBodyCounterpartyJSON]

    } else if (createTransactionRequest.transactionRequestType == (TransactionRequestTypes.SEPA.toString)) {
      val transactionRequestBodyPhoneToPhoneJson = createTransactionRequest.transactionRequestCommonBody.asInstanceOf[TransactionRequestBodySEPAJSON]

    } else
      throw new RuntimeException("Do not support this transaction type, please check it in OBP-API side")

    InboundCreateTransactionId(createTransactionRequest.authInfo,
      InternalTransactionId("", List(InboundStatusMessage("ESB", "Success", "0", "OK")),
        transactionNewId))

  }

  def getToken(getTokenRequest: OutboundGetToken): InboundToken = {
    InboundToken(getTokenRequest.username, getMFToken(getTokenRequest.username))
  }

  def createChallenge(createChallenge: OutboundCreateChallengeJune2017): InboundCreateChallengeJune2017 = {
    logger.debug(s"LeumiDecoder-createChallenge input: ($createChallenge)")
    val jsonExtract = getJoniMfUserFromCache(createChallenge.authInfo.username)
    val account = getBasicBankAccountByAccountIdFromCachedJoni(createChallenge.authInfo.username, createChallenge.accountId)
    val branchId = account.branchNr
    val accountNumber = account.accountNr
    val accountType = account.accountType
    val username = createChallenge.authInfo.username
    val cbsToken =  if (account.cbsToken != createChallenge.authInfo.cbsToken) {
      throw new RuntimeException("Session Error")
    } else account.cbsToken
    val callNtlv1 = getNtlv1Mf(username,
      jsonExtract.SDR_JONI.SDR_MANUI.SDRM_ZEHUT,
      jsonExtract.SDR_JONI.SDR_MANUI.SDRM_SUG_ZIHUY,
      cbsToken
    )
    //TODO: will use the first mobile phone contact available. Check.
    val mobilePhoneData = callNtlv1.O1OUT1AREA_1.O1_CONTACT_REC.find(x => x.O1_TEL_USE_TYPE_CODE == "10").getOrElse(
      O1contactRec(O1recId("", ""), "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", ""))


    val callNtlv7 = getNtlv7Mf(branchId,
      accountType,
      accountNumber,
      username,
      cbsToken,
      mobilePhoneData.O1_TEL_AREA,
      mobilePhoneData.O1_TEL_NUM
    )

    val answer = callNtlv7.DFHPLT_1.DFH_OPT
    InboundCreateChallengeJune2017(createChallenge.authInfo, InternalCreateChallengeJune2017(
      "",
      List(
        //Todo: We did 3 MfCalls so far. Shall they all go in?
        InboundStatusMessage("ESB", "Success", "0", "OK"), //TODO, need to fill the coreBanking error
        InboundStatusMessage("MF", "Success", "0", "OK") //TODO, need to fill the coreBanking error
      ),
      answer))
  }

  def getTransactionRequests(outboundGetTransactionRequests210: OutboundGetTransactionRequests210): InboundGetTransactionRequests210 = {

    val accountId = outboundGetTransactionRequests210.counterparty.accountId
    val account = getBasicBankAccountByAccountIdFromCachedJoni(outboundGetTransactionRequests210.authInfo.username, outboundGetTransactionRequests210.counterparty.accountId)
    val branchId = account.branchNr
    val accountNumber = account.accountNr
    val accountType = account.accountType
    val username = outboundGetTransactionRequests210.authInfo.username
    val cbsToken = if (outboundGetTransactionRequests210.authInfo.cbsToken != account.cbsToken) {
      throw new RuntimeException("Session Error")
    } else account.cbsToken

      val nt1c3result = getNt1c3(
      branchId,
      accountType,
      accountNumber,
      username,
      cbsToken
    ) 
      
      nt1c3result match {
      case Right(x) =>

        val nt1c4result = getNt1c4(
          branchId,
          accountType,
          accountNumber,
          username,
          cbsToken
        ) 
        
        nt1c4result match {
          case Right(y) =>


            var result = new ListBuffer[TransactionRequest]
            for (i <- x.TA1TSHUVATAVLAIT1.TA1_SHETACH_LE_SEND_NOSAF.TA1_TNUOT.TA1_PIRTEY_TNUA) {
              result += mapNt1c3ToTransactionRequest(i, accountId)
            }
            for (i <- y.TNATSHUVATAVLAIT1.TNA_SHETACH_LE_SEND_NOSAF.TNA_TNUOT.TNA_PIRTEY_TNUA) {
              result += mapNt1c4ToTransactionRequest(i, accountId)
            }

            InboundGetTransactionRequests210(
              outboundGetTransactionRequests210.authInfo,
              InternalGetTransactionRequests(
                "",
                List(
                  //Todo: We did 3 MfCalls so far. Shall they all go in?
                  InboundStatusMessage("ESB", "Success", "0", "OK"), //TODO, need to fill the coreBanking error
                  InboundStatusMessage("MF", "Success", "0", "OK") //TODO, need to fill the coreBanking error
                ),
                result.toList))

          case Left(y) =>

            InboundGetTransactionRequests210(
              outboundGetTransactionRequests210.authInfo,
              InternalGetTransactionRequests(
                "",
                List(InboundStatusMessage(
                  "ESB",
                  "Failure",
                  y.PAPIErrorResponse.esbHeaderResponse.responseStatus.callStatus,
                  y.PAPIErrorResponse.esbHeaderResponse.responseStatus.errorDesc.getOrElse(""))),
                Nil))


        }
      case Left(x) =>

        InboundGetTransactionRequests210(
          outboundGetTransactionRequests210.authInfo,
          InternalGetTransactionRequests(
            "",
            List(InboundStatusMessage(
              "ESB",
              "Failure",
              x.PAPIErrorResponse.esbHeaderResponse.responseStatus.callStatus,
              x.PAPIErrorResponse.esbHeaderResponse.responseStatus.errorDesc.getOrElse(""))),
            Nil))


    }
  }        

  def createCounterparty(outboundCreateCounterparty: OutboundCreateCounterparty): InboundCreateCounterparty = {
    val account = getBasicBankAccountByAccountIdFromCachedJoni(outboundCreateCounterparty.authInfo.username, outboundCreateCounterparty.counterparty.thisAccountId)
    val branchId = account.branchNr
    val accountNumber = account.accountNr
    val accountType = account.accountType
    val cbsToken = account.cbsToken
    if (cbsToken != outboundCreateCounterparty.authInfo.cbsToken) throw new RuntimeException("Session Error")

    if (outboundCreateCounterparty.counterparty.thisBankId == "10") {
      val ntg6ACall = getNtg6A(
        branch = branchId,
        accountType = accountType,
        accountNumber = accountNumber,
        cbsToken = cbsToken,
        counterpartyBranchNumber = outboundCreateCounterparty.counterparty.otherBranchRoutingAddress,
        counterpartyAccountNumber = outboundCreateCounterparty.counterparty.otherAccountSecondaryRoutingAddress,
        counterpartyName = outboundCreateCounterparty.counterparty.name,
        counterpartyDescription = outboundCreateCounterparty.counterparty.description,
        counterpartyIBAN = outboundCreateCounterparty.counterparty.otherAccountRoutingAddress,
        counterpartyNameInEnglish = outboundCreateCounterparty.counterparty.bespoke(0).value,
        counterpartyDescriptionInEnglish = outboundCreateCounterparty.counterparty.bespoke(1).value
      )
      ntg6ACall match {
        case Right(x) =>

          InboundCreateCounterparty(
            outboundCreateCounterparty.authInfo,
            InternalCreateCounterparty(
              "",
              List(
                InboundStatusMessage(
                  "ESB",
                  "Success",
                  x.NTDriveNoResp.esbHeaderResponse.responseStatus.callStatus,
                  x.NTDriveNoResp.esbHeaderResponse.responseStatus.errorDesc.getOrElse("")),
                InboundStatusMessage(
                  "MF",
                  "Success",
                  x.NTDriveNoResp.MFAdminResponse.returnCode,
                  x.NTDriveNoResp.MFAdminResponse.messageText.getOrElse(""))
              ),
              true.toString,
              thisBankId = outboundCreateCounterparty.counterparty.thisBankId,
              thisAccountId = outboundCreateCounterparty.counterparty.thisAccountId,
              thisViewId = outboundCreateCounterparty.counterparty.thisViewId,
              counterpartyId = "",//TODO need generate a CounterpartyId
              otherAccountRoutingScheme = outboundCreateCounterparty.counterparty.otherAccountRoutingScheme,
              otherAccountRoutingAddress = outboundCreateCounterparty.counterparty.otherAccountRoutingAddress,
              otherBankRoutingScheme = outboundCreateCounterparty.counterparty.otherBankRoutingScheme,
              otherBankRoutingAddress = outboundCreateCounterparty.counterparty.otherBankRoutingAddress,
              otherBranchRoutingScheme = outboundCreateCounterparty.counterparty.otherBranchRoutingScheme,
              otherBranchRoutingAddress = outboundCreateCounterparty.counterparty.otherBranchRoutingAddress,
              isBeneficiary = outboundCreateCounterparty.counterparty.isBeneficiary,
              description = outboundCreateCounterparty.counterparty.description,
              otherAccountSecondaryRoutingScheme = outboundCreateCounterparty.counterparty.otherAccountSecondaryRoutingScheme,
              otherAccountSecondaryRoutingAddress = outboundCreateCounterparty.counterparty.otherAccountSecondaryRoutingAddress,
              bespoke = outboundCreateCounterparty.counterparty.bespoke
            )
          )
        case Left(x) =>
          InboundCreateCounterparty(
            outboundCreateCounterparty.authInfo,
            InternalCreateCounterparty(
              "",
              List(
                InboundStatusMessage(
                  "ESB",
                  "Success",
                  x.PAPIErrorResponse.esbHeaderResponse.responseStatus.callStatus,
                  x.PAPIErrorResponse.esbHeaderResponse.responseStatus.errorDesc.getOrElse("")),
                InboundStatusMessage(
                  "MF",
                  "Success",
                  x.PAPIErrorResponse.MFAdminResponse.returnCode,
                  x.PAPIErrorResponse.MFAdminResponse.messageText.getOrElse(""))
              ),
              true.toString,
              thisBankId = outboundCreateCounterparty.counterparty.thisBankId,
              thisAccountId = outboundCreateCounterparty.counterparty.thisAccountId,
              thisViewId = outboundCreateCounterparty.counterparty.thisViewId,
              counterpartyId = "",//TODO need generate a CounterpartyId
              otherAccountRoutingScheme = outboundCreateCounterparty.counterparty.otherAccountRoutingScheme,
              otherAccountRoutingAddress = outboundCreateCounterparty.counterparty.otherAccountRoutingAddress,
              otherBankRoutingScheme = outboundCreateCounterparty.counterparty.otherBankRoutingScheme,
              otherBankRoutingAddress = outboundCreateCounterparty.counterparty.otherBankRoutingAddress,
              otherBranchRoutingScheme = outboundCreateCounterparty.counterparty.otherBranchRoutingScheme,
              otherBranchRoutingAddress = outboundCreateCounterparty.counterparty.otherBranchRoutingAddress,
              isBeneficiary = outboundCreateCounterparty.counterparty.isBeneficiary,
              description = outboundCreateCounterparty.counterparty.description,
              otherAccountSecondaryRoutingScheme = outboundCreateCounterparty.counterparty.otherAccountSecondaryRoutingScheme,
              otherAccountSecondaryRoutingAddress = outboundCreateCounterparty.counterparty.otherAccountSecondaryRoutingAddress,
              bespoke = outboundCreateCounterparty.counterparty.bespoke
            )
          )
      }
    } else {
      val ntg6BCall = getNtg6B(
        branch = branchId,
        accountType = accountType,
        accountNumber = accountNumber,
        cbsToken = cbsToken,
        counterpartyBankId = outboundCreateCounterparty.counterparty.otherBankRoutingAddress,
        counterpartyBranchNumber = outboundCreateCounterparty.counterparty.otherBranchRoutingAddress,
        counterpartyAccountNumber = outboundCreateCounterparty.counterparty.otherAccountSecondaryRoutingAddress,
        counterpartyName = outboundCreateCounterparty.counterparty.name,
        counterpartyDescription = outboundCreateCounterparty.counterparty.description,
        counterpartyIBAN = outboundCreateCounterparty.counterparty.otherAccountRoutingAddress,
        counterpartyNameInEnglish = outboundCreateCounterparty.counterparty.bespoke(0).value,
        counterpartyDescriptionInEnglish = outboundCreateCounterparty.counterparty.bespoke(1).value
      )
      ntg6BCall match {
        case Right(x) =>

          InboundCreateCounterparty(
            outboundCreateCounterparty.authInfo,
            InternalCreateCounterparty(
              "",
              List(
                InboundStatusMessage(
                  "ESB",
                  "Success",
                  x.NTDriveNoResp.esbHeaderResponse.responseStatus.callStatus,
                  x.NTDriveNoResp.esbHeaderResponse.responseStatus.errorDesc.getOrElse("")),
                InboundStatusMessage(
                  "MF",
                  "Success",
                  x.NTDriveNoResp.MFAdminResponse.returnCode,
                  x.NTDriveNoResp.MFAdminResponse.messageText.getOrElse(""))
              ),
              true.toString,
              thisBankId = outboundCreateCounterparty.counterparty.thisBankId,
              thisAccountId = outboundCreateCounterparty.counterparty.thisAccountId,
              thisViewId = outboundCreateCounterparty.counterparty.thisViewId,
              counterpartyId = "",//TODO need generate a CounterpartyId
              otherAccountRoutingScheme = outboundCreateCounterparty.counterparty.otherAccountRoutingScheme,
              otherAccountRoutingAddress = outboundCreateCounterparty.counterparty.otherAccountRoutingAddress,
              otherBankRoutingScheme = outboundCreateCounterparty.counterparty.otherBankRoutingScheme,
              otherBankRoutingAddress = outboundCreateCounterparty.counterparty.otherBankRoutingAddress,
              otherBranchRoutingScheme = outboundCreateCounterparty.counterparty.otherBranchRoutingScheme,
              otherBranchRoutingAddress = outboundCreateCounterparty.counterparty.otherBranchRoutingAddress,
              isBeneficiary = outboundCreateCounterparty.counterparty.isBeneficiary,
              description = outboundCreateCounterparty.counterparty.description,
              otherAccountSecondaryRoutingScheme = outboundCreateCounterparty.counterparty.otherAccountSecondaryRoutingScheme,
              otherAccountSecondaryRoutingAddress = outboundCreateCounterparty.counterparty.otherAccountSecondaryRoutingAddress,
              bespoke = outboundCreateCounterparty.counterparty.bespoke
            )
          )

        case Left(x) =>
          InboundCreateCounterparty(
            outboundCreateCounterparty.authInfo,
            InternalCreateCounterparty(
              "",
              List(
                InboundStatusMessage(
                  "ESB",
                  "Failure",
                  x.PAPIErrorResponse.esbHeaderResponse.responseStatus.callStatus,
                  x.PAPIErrorResponse.esbHeaderResponse.responseStatus.errorDesc.getOrElse("")),
                InboundStatusMessage(
                  "MF",
                  "Failure",
                  x.PAPIErrorResponse.MFAdminResponse.returnCode,
                  x.PAPIErrorResponse.MFAdminResponse.messageText.getOrElse(""))
              ),
              true.toString,
              thisBankId = outboundCreateCounterparty.counterparty.thisBankId,
              thisAccountId = outboundCreateCounterparty.counterparty.thisAccountId,
              thisViewId = outboundCreateCounterparty.counterparty.thisViewId,
              counterpartyId = "", //TODO need generate a CounterpartyId
              otherAccountRoutingScheme = outboundCreateCounterparty.counterparty.otherAccountRoutingScheme,
              otherAccountRoutingAddress = outboundCreateCounterparty.counterparty.otherAccountRoutingAddress,
              otherBankRoutingScheme = outboundCreateCounterparty.counterparty.otherBankRoutingScheme,
              otherBankRoutingAddress = outboundCreateCounterparty.counterparty.otherBankRoutingAddress,
              otherBranchRoutingScheme = outboundCreateCounterparty.counterparty.otherBranchRoutingScheme,
              otherBranchRoutingAddress = outboundCreateCounterparty.counterparty.otherBranchRoutingAddress,
              isBeneficiary = outboundCreateCounterparty.counterparty.isBeneficiary,
              description = outboundCreateCounterparty.counterparty.description,
              otherAccountSecondaryRoutingScheme = outboundCreateCounterparty.counterparty.otherAccountSecondaryRoutingScheme,
              otherAccountSecondaryRoutingAddress = outboundCreateCounterparty.counterparty.otherAccountSecondaryRoutingAddress,
              bespoke = outboundCreateCounterparty.counterparty.bespoke
            )
          )
      }


    }

  }

  def getCustomer(outboundGetCustomersByUserIdFuture: OutboundGetCustomersByUserId): InboundGetCustomersByUserId = {
    val username = outboundGetCustomersByUserIdFuture.authInfo.username
    val joniMfCall = getJoniMfUserFromCache(username)
    //Todo: just gets limit for the leading account instead of limit and balance for all
    if (joniMfCall.SDR_JONI.MFTOKEN != outboundGetCustomersByUserIdFuture.authInfo.cbsToken) throw new RuntimeException("Session Error")
    val callNtlv1 = getNtlv1Mf(username,
      joniMfCall.SDR_JONI.SDR_MANUI.SDRM_ZEHUT,
      joniMfCall.SDR_JONI.SDR_MANUI.SDRM_SUG_ZIHUY,
      joniMfCall.SDR_JONI.MFTOKEN
    )
    val mobilePhoneData = callNtlv1.O1OUT1AREA_1.O1_CONTACT_REC.find(x => x.O1_TEL_USE_TYPE_CODE == "10").getOrElse(
      O1contactRec(O1recId("", ""), "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", ""))

    val emailAddress = callNtlv1.O1OUT1AREA_1.O1_CONTACT_REC.find(x => !x.O1_MAIL_ADDRESS.trim().isEmpty).getOrElse(
      O1contactRec(O1recId("", ""), "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", "")
    )


    val result = InternalFullCustomer(
      status = "",
      errorCode = "",
      backendMessages = List(InboundStatusMessage("", "", "", "")),
      customerId = createCustomerId(username),
      bankId = "10",
      number = username,
      legalName = joniMfCall.SDR_JONI.SDR_MANUI.SDRM_SHEM_PRATI + " " + joniMfCall.SDR_JONI.SDR_MANUI.SDRM_SHEM_MISHPACHA,
      mobileNumber = mobilePhoneData.O1_TEL_AREA + mobilePhoneData.O1_TEL_NUM, //first mobile (type:10) nr. in ntlv1
      email = emailAddress.O1_MAIL_ADDRESS, //first not empty email address in ntlv1
      faceImage = CustomerFaceImage(null, ""),
      dateOfBirth = simpleTransactionDateFormat.parse(joniMfCall.SDR_JONI.SDR_MANUI.SDRM_TAR_LEIDA), //JONI
      relationshipStatus = "",
      dependents = null,
      dobOfDependents = List(null),
      highestEducationAttained = "",
      employmentStatus = "",
      creditRating = CreditRating("", ""),
      creditLimit = AmountOfMoney("", ""),
      kycStatus = null,
      lastOkDate = simpleLastLoginFormat.parse(joniMfCall.SDR_JONI.SDR_MANUI.SDRM_DATE_LAST + joniMfCall.SDR_JONI.SDR_MANUI.SDRM_TIME_LAST) //JONI
    )
    InboundGetCustomersByUserId(outboundGetCustomersByUserIdFuture.authInfo, List(result))
  }

  def getCounterpartiesForAccount(outboundGetCounterparties: OutboundGetCounterparties): InboundGetCounterparties = {
    val joniCall = getJoniMfUserFromCache(outboundGetCounterparties.authInfo.username)
    if (joniCall.SDR_JONI.MFTOKEN != outboundGetCounterparties.authInfo.cbsToken) throw new RuntimeException("Session Error")

    val account = getBasicBankAccountByAccountIdFromCachedJoni(outboundGetCounterparties.authInfo.username, outboundGetCounterparties.counterparty.thisAccountId)
    val branchId = account.branchNr
    val accountNumber = account.accountNr
    val accountType = account.accountType
    
    var result = new ListBuffer[InternalCounterparty]
    
    val ntg6ICall = getNtg6I(branchId,accountType,accountNumber, outboundGetCounterparties.authInfo.cbsToken) 
    
    ntg6ICall match {
      case Right(x) =>
        for (i <- x.PMUTSHLIFA_OUT.PMUT_RESHIMAT_MUTAVIM) {
          result += mapAdapterCounterpartyToInternalCounterparty(i.PMUT_PIRTEY_MUTAV, outboundGetCounterparties.counterparty)
        }
        
        val ntg6KCall = getNtg6K(branchId,accountType,accountNumber,outboundGetCounterparties.authInfo.cbsToken) 
        ntg6KCall match {
          case Right(y) => 
            for (u <- y.PMUTSHLIFA_OUT.PMUT_RESHIMAT_MUTAVIM){
              result += mapAdapterCounterpartyToInternalCounterparty(u.PMUT_PIRTEY_MUTAV, outboundGetCounterparties.counterparty)
            }
            val returnValue = result.toList
            cachedCounterparties.set(outboundGetCounterparties.authInfo.username, returnValue)
            InboundGetCounterparties(outboundGetCounterparties.authInfo, returnValue)


          case Left(y) =>
            InboundGetCounterparties(outboundGetCounterparties.authInfo, List(InternalCounterparty(
              errorCode = "",
              backendMessages = List(InboundStatusMessage(
                "ESB",
                "Failure",
                y.PAPIErrorResponse.esbHeaderResponse.responseStatus.callStatus,
                y.PAPIErrorResponse.esbHeaderResponse.responseStatus.errorDesc.getOrElse("")),
                InboundStatusMessage(
                  "MF",
                  "Failure",
                  y.PAPIErrorResponse.MFAdminResponse.returnCode,
                  y.PAPIErrorResponse.MFAdminResponse.messageText.getOrElse(""))
              ),
              createdByUserId = "",
              name = "",
              thisBankId = "",
              thisAccountId = "",
              thisViewId = "",
              counterpartyId = "",
              otherAccountRoutingScheme= "",
              otherAccountRoutingAddress= "",
              otherBankRoutingScheme= "",
              otherBankRoutingAddress= "",
              otherBranchRoutingScheme= "",
              otherBranchRoutingAddress= "",
              isBeneficiary = false,
              description = "",
              otherAccountSecondaryRoutingScheme= "",
              otherAccountSecondaryRoutingAddress= "",
              bespoke = List(PostCounterpartyBespoke("englishName", ""),
                PostCounterpartyBespoke("englishDescription", "")


              ))))

        }
      case Left(x) =>
        InboundGetCounterparties(outboundGetCounterparties.authInfo, List(InternalCounterparty(
          errorCode = "",
          backendMessages = List(InboundStatusMessage(
            "ESB",
            "Failure",
            x.PAPIErrorResponse.esbHeaderResponse.responseStatus.callStatus,
            x.PAPIErrorResponse.esbHeaderResponse.responseStatus.errorDesc.getOrElse("")),
            InboundStatusMessage(
              "MF",
              "Failure",
              x.PAPIErrorResponse.MFAdminResponse.returnCode,
              x.PAPIErrorResponse.MFAdminResponse.messageText.getOrElse(""))
          ),
          createdByUserId = "",
          name = "",
          thisBankId = "",
          thisAccountId = "",
          thisViewId = "",
          counterpartyId = "",
          otherAccountRoutingScheme= "",
          otherAccountRoutingAddress= "",
          otherBankRoutingScheme= "",
          otherBankRoutingAddress= "",
          otherBranchRoutingScheme= "",
          otherBranchRoutingAddress= "",
          isBeneficiary = false,
          description = "",
          otherAccountSecondaryRoutingScheme= "",
          otherAccountSecondaryRoutingAddress= "",
          bespoke = List(PostCounterpartyBespoke("englishName", ""),
            PostCounterpartyBespoke("englishDescription", "")


          ))))
        
        
    }
  }
  
  def getCounterpartyByCounterpartyId(outboundGetCounterpartyByCounterpartyId: OutboundGetCounterpartyByCounterpartyId) = {
    val counterpartiesFromCache = cachedCounterparties.get(outboundGetCounterpartyByCounterpartyId.authInfo.username).getOrElse(
      getCounterpartiesForAccount(OutboundGetCounterparties(
        outboundGetCounterpartyByCounterpartyId.authInfo, 
        InternalOutboundGetCounterparties(
          outboundGetCounterpartyByCounterpartyId.counterparty.thisBankId,
          outboundGetCounterpartyByCounterpartyId.counterparty.thisAccountId,
          outboundGetCounterpartyByCounterpartyId.counterparty.viewId))).data
    )
    val internalCounterparty = counterpartiesFromCache.find(x => 
        x.counterpartyId == outboundGetCounterpartyByCounterpartyId.counterparty.counterpartyId).getOrElse(throw new InvalidCounterPartyIdException(s"$InvalidCounterPartyId Current CounterpartyId =${outboundGetCounterpartyByCounterpartyId.counterparty.counterpartyId}"))
    InboundGetCounterparty(outboundGetCounterpartyByCounterpartyId.authInfo, internalCounterparty)
      
  }

}



