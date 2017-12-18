package com.tesobe.obp.june2017

import java.text.SimpleDateFormat
import java.time._
import java.time.format.DateTimeFormatter
import java.util.{Date, TimeZone}

import com.tesobe.obp.ErrorMessages._
import com.tesobe.obp.GetBankAccounts.{base64EncodedSha256, getBasicBankAccountsForUser}
import com.tesobe.obp.LeumiBranches.getLeumiBranches
import com.tesobe.obp.LeumiBranches.LeumiBranch
import com.tesobe.obp.JoniMf._
import com.tesobe.obp.Nt1c3Mf.getNt1c3
import com.tesobe.obp.Nt1c4Mf.getNt1c4
import com.tesobe.obp.Nt1cBMf.getNt1cB
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
import com.tesobe.obp.Ntg6IMf.getNtg6IMf
import com.tesobe.obp.Ntg6KMf.getNtg6KMf
import com.tesobe.obp.Ntib2Mf.getNtib2Mf
import com.tesobe.obp.Ntlv1Mf.getNtlv1Mf
import com.tesobe.obp.Ntlv7Mf.getNtlv7Mf
import com.tesobe.obp.NttfWMf.getNttfWMf
import com.tesobe.obp.Util.TransactionRequestTypes
import com.tesobe.obp._
import com.typesafe.scalalogging.StrictLogging

import scala.collection.immutable.List
import scala.collection.mutable.ListBuffer


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
  val defaultBankId = "10"
  val defaultFilterFormat: SimpleDateFormat = new SimpleDateFormat("E MMM dd HH:mm:ss Z yyyy")
  val defaultInboundFormat: SimpleDateFormat = new SimpleDateFormat("E MMM dd HH:mm:ss z yyyy")
  defaultFilterFormat.setTimeZone(TimeZone.getTimeZone("UTC"))
  val simpleTransactionDateFormat = new SimpleDateFormat("yyyyMMdd")
  //simpleTransactionDateFormat.setTimeZone(TimeZone.getTimeZone("UTC"))
  val simpleDateFormat: SimpleDateFormat = new SimpleDateFormat("dd/MM/yyyy")
  val simpleDayFormat: SimpleDateFormat = new SimpleDateFormat("dd")
  val simpleMonthFormat: SimpleDateFormat = new SimpleDateFormat("MM")
  val simpleYearFormat: SimpleDateFormat = new SimpleDateFormat("yyyy")
  val simpleLastLoginFormat: SimpleDateFormat = new SimpleDateFormat("yyyyMMddHHmmss")
  val formatterUTC = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
  val formatterDefaultFilter = DateTimeFormatter.ofPattern("E MMM dd HH:mm:ss z yyyy")
  simpleLastLoginFormat.setTimeZone(TimeZone.getTimeZone("UTC"))
  val defaultIsraelTimeFormat: SimpleDateFormat = new SimpleDateFormat("yyyyMMddHHmmss")
  defaultIsraelTimeFormat.setTimeZone(TimeZone.getTimeZone("Asia/Jerusalem"))
  val formatter = DateTimeFormatter.BASIC_ISO_DATE
 
  

  val cachedTransactionId = TTLCache[TransactionIdValues](10080)//1 week in minutes for now
  val cachedTransactionRequestIds = TTLCache[TransactionRequestIdValues](10080)
  val cachedCounterpartyIds = TTLCache[String](10080) 

  case class AccountIdValues(branchId: String, accountType: String, accountNumber: String)
  case class TransactionIdValues(amount: String, completedDate: String, newBalanceAmount: String)
  case class TransactionRequestIdValues(amount: String, description: String, makor: String, asmachta: String)



  //Helper functions start here:---------------------------------------------------------------------------------------



  def createAccountId(branchId: String, accountType: String, accountNumber: String): String = {
    logger.debug(s"createAccountId-accountNr($accountNumber)")
    base64EncodedSha256(branchId + accountType + accountNumber + config.getString("salt.global"))
  }
  
  def createCounterpartyId(
                            counterpartyName: String,
                            counterpartyBankCode: String,
                            counterpartyBranchNr: String,
                            counterpartyAccountType: String,
                            counterpartyAccountNr: String,
                            ownerAccountId: String): String = {
    logger.debug(s"createCounterpartyId-counterpartyName($counterpartyName)")

    val counterpartyId = base64EncodedSha256(counterpartyName + counterpartyBankCode + counterpartyBranchNr + counterpartyAccountType + counterpartyAccountNr + ownerAccountId)
    cachedCounterpartyIds.set(counterpartyId, ownerAccountId)
    counterpartyId
  }
  
  def createTransactionCounterpartyId(description: String, accountID: String) = {
    base64EncodedSha256(description + accountID + config.getString("salt.global"))
  }
  
  def getBasicBankAccountByAccountIdFromCachedJoni(username: String, accountId: String): Either[PAPIErrorResponse,BasicBankAccount] = {
    val mfAccounts = getBasicBankAccountsForUser(username, true)
    mfAccounts match {
      case Right(mfAccounts) =>
        Right(mfAccounts.find(x => (base64EncodedSha256(x.branchNr + x.accountType + x.accountNr + config.getString("salt.global")) == accountId)).getOrElse(throw new InvalidAccountIdException(s"$InvalidAccountId accountId = $accountId")))
      case Left(x) => 
        Left(x)
    }
  }

  def createTransactionId(amount: String, completedDate: String, newBalanceAmount: String): String = {
    logger.debug(s"getOrCreateTransactionId for ($amount)($completedDate)($newBalanceAmount)")
    val transactionIdValues = TransactionIdValues(amount, completedDate, newBalanceAmount)
      val transactionId = base64EncodedSha256(amount + completedDate + newBalanceAmount)
     cachedTransactionId.set(transactionId,transactionIdValues)
      transactionId
      }
  
  def createTransactionRequestId(amount: String, description: String, makor: String, asmachta: String): String = {
    logger.debug(s"createTransactionRequestId for ($amount) ($description) ($makor) ($asmachta)")
    val transactionRequestIdValues = TransactionRequestIdValues(amount, description, makor, asmachta)
    val transactionRequestId = base64EncodedSha256(amount + description + makor + asmachta)
    cachedTransactionRequestIds.set(transactionRequestId, transactionRequestIdValues)
    transactionRequestId
  }

  
  def getUtcDatefromLeumiDateTime(leumiDate: String, leumiTime: String): Date  =  {
    val dateWithIsraelTime = defaultIsraelTimeFormat.parse(leumiDate + leumiTime)
    
    val utcFormat: SimpleDateFormat = new SimpleDateFormat("yyyyMMddHHmmss")
    utcFormat.setTimeZone(TimeZone.getTimeZone("UTC"))
    val dateUtcAsString: String = utcFormat.format(dateWithIsraelTime)
    val dateIUtc: Date = utcFormat.parse(dateUtcAsString)
    dateIUtc
    }

  def getUtcDatefromLeumiLocalDate(leumiDate: String): Date  =  {
    simpleLastLoginFormat.parse(leumiDate + "120000")
  }

  def createCustomerId(username: String): String = {
    logger.debug(s"getOrCreateCustomerId for ($username)")
      val customerId = base64EncodedSha256(username + config.getString("salt.global"))
      customerId
      }
  
  def createInboundStatusMessages(x: PAPIErrorResponse) = {
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
    )
  }


  def mapBasicBankAccountToInboundAccountJune2017(username: String, x: BasicBankAccount, iban: String, balance: String, creditLimit: String): InboundAccountJune2017 = {

    //Create OwnerRights and accountViewer for result InboundAccount2017 creation
    val hasOwnerRights: Boolean = x.accountPermissions.canMakeExternalPayments
    val hasAccountantRights = x.accountPermissions.canMakeInternalPayments
    val hasAuditorRights = x.accountPermissions.canSee
    val viewsToGenerate = {
      if (hasOwnerRights) {
        List("Owner","Accountant", "Auditor")
      } else if (hasAccountantRights) {
        List("Accountant", "Auditor")
      } else if (hasAuditorRights){
        List("Auditor")
      } else {
        List("")
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
      List(InboundStatusMessage("ESB", "Success", "0", "OK")), 
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
      accountRoutingAddress = iban,
      accountRules = if (creditLimit.isEmpty) List() else List(AccountRules("CREDIT_LIMIT", creditLimit)))
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
        InboundStatusMessage("ESB", "Success", "0", "OK"), 
        InboundStatusMessage("MF", "Success", "0", "OK") 
      ),
      transactionId = createTransactionId(amount, completedDate, newBalanceAmount), // Find some
      accountId = accountId, //accountId
      amount = amount,
      bankId = "10", // 10 for now (Joni)
      completedDate = getUtcDatefromLeumiLocalDate(completedDate),
      counterpartyId = "",
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
  

  def mapNt1c3ToTransactionRequest(transactions: Ta1TnuaBodedet, accountId: String): TransactionRequest = {
    TransactionRequest(
      id = TransactionRequestId(createTransactionRequestId(
        amount = transactions.TA1_TNUA_BODEDET.TA1_SCHUM_TNUA,
        description = transactions.TA1_TNUA_BODEDET.TA1_TEUR_TNUA,
        makor = transactions.TA1_TNUA_BODEDET.TA1_MAKOR_TNUA,
        asmachta = transactions.TA1_TNUA_BODEDET.TA1_ASMACHTA
      )),
      `type` = if (transactions.TA1_TNUA_BODEDET.TA1_IND_KARTIS_ASHRAI == "1") {
        "_FUTURE_CREDIT_CARD"
      } else if (transactions.TA1_TNUA_BODEDET.TA1_IND_HOR_KEVA == "1") {
        "_FUTURE_STANDING_ORDER"
      } else "_FUTURE", 
      from = TransactionRequestAccount("10", accountId),
      body = TransactionRequestBody(
        TransactionRequestAccount("", ""),
        AmountOfMoney("ILS", transactions.TA1_TNUA_BODEDET.TA1_SCHUM_TNUA), //amount from Nt1c3
        description = transactions.TA1_TNUA_BODEDET.TA1_TEUR_TNUA), //description from NT1c3
      transaction_ids = "" ,
      status = "COMPLETED",
      start_date = null,
      end_date = getUtcDatefromLeumiLocalDate(transactions.TA1_TNUA_BODEDET.TA1_TA_ERECH), //nt1c3 date of value for request
      challenge = TransactionRequestChallenge("", 0, ""),
      charge = TransactionRequestCharge("", AmountOfMoney("ILS", "0")),
      charge_policy = "",
      counterparty_id = CounterpartyId(""),
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
      id = TransactionRequestId(createTransactionId(
        amount = transactions.TNA_TNUA_BODEDET.TNA_SCHUM,
        completedDate = transactions.TNA_TNUA_BODEDET.TNA_TA_ERECH,
        newBalanceAmount = transactions.TNA_TNUA_BODEDET.TNA_ITRA
      )),
      `type` = "_INTRADAY",
      from = TransactionRequestAccount("10", accountId),
      body = TransactionRequestBody(
        TransactionRequestAccount("", ""),
        AmountOfMoney("ILS", transactions.TNA_TNUA_BODEDET.TNA_SCHUM), //amount from Nt1c4
        description = transactions.TNA_TNUA_BODEDET.TNA_TEUR_PEULA), //description from NT1c4
      transaction_ids = "",
      status = "COMPLETED",
      start_date = null,
      end_date = getUtcDatefromLeumiLocalDate(transactions.TNA_TNUA_BODEDET.TNA_TA_ERECH), //nt1c4 date of value for request
      challenge = TransactionRequestChallenge("", 0, ""),
      charge = TransactionRequestCharge("", AmountOfMoney("ILS", "0")),
      charge_policy = "",
      counterparty_id = CounterpartyId(""),
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
  
  def mapAdapterCounterpartyToInternalCounterparty(
                                                    CbsCounterparty: PmutPirteyMutav,
                                                    OutboundCounterparty: InternalOutboundGetCounterparties,
                                                    thisAccountId: String): InternalCounterparty = {
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
      counterpartyId = createCounterpartyId(counterpartyName, counterpartyBankCode, counterpartyBranchNr, counterpartyAccountType, counterpartyAccountNr, thisAccountId),
      otherAccountRoutingScheme= "",
      otherAccountRoutingAddress= counterpartyAccountNr,
      otherBankRoutingScheme= "",
      otherBankRoutingAddress= counterpartyBankCode,
      otherBranchRoutingScheme= "",
      otherBranchRoutingAddress= counterpartyBranchNr,
      isBeneficiary = true,
      description = description,
      otherAccountSecondaryRoutingScheme= accountRoutingScheme,
      otherAccountSecondaryRoutingAddress= iBan,
      bespoke = List(
        PostCounterpartyBespoke("", englishName),
        PostCounterpartyBespoke("", englishDescription)
    ))
  }
  
  def mapLeumiBranchToObpBranch(leumiBranch: LeumiBranch): InboundBranchVJune2017 = {
    InboundBranchVJune2017(
      branchId = BranchId(leumiBranch.branchCode),
      bankId = BankId("10"),
      name = leumiBranch.name,
      address = Address(
        line1 = leumiBranch.address,
        line2 = "",
        line3 = "",
        city = leumiBranch.city,
        county = None,
        state = "",
        postCode = leumiBranch.zipcode, 
        countryCode = "IL"),
      location = Location(leumiBranch.x.toDouble,leumiBranch.y.toDouble),
      // lobbyString = Option[String],
      // driveUpString = Option[String],
      meta = Meta(License(id = "pddl", name = "Open Data Commons Public Domain Dedication and License (PDDL)")),
      branchRouting = None,
      lobby = Some(getLobbyFromLeumiBranch(leumiBranch)),
      driveUp = None,
      // Easy access for people who use wheelchairs etc.
      isAccessible  = Some(leumiBranch.accessibility),
      accessibleFeatures = Some(leumiBranch.accessible_features),
      branchType  = None,
      moreInfo  = None,
      phoneNumber  = Some("")
    )
  }
  
  def getLobbyFromLeumiBranch(leumiBranch: LeumiBranch): Lobby = {
    Lobby(List(
      OpeningTimes(leumiBranch.shaot.ob1.patch(2,":",0),leumiBranch.shaot.cb1.patch(2,":",0)),
      OpeningTimes(leumiBranch.shaot.oe1.patch(2,":",0), leumiBranch.shaot.ce1.patch(2,":",0))),
      List(
        OpeningTimes(leumiBranch.shaot.ob2.patch(2,":",0),leumiBranch.shaot.cb2.patch(2,":",0)),
        OpeningTimes(leumiBranch.shaot.oe2.patch(2,":",0), leumiBranch.shaot.ce2.patch(2,":",0))),
      List(
        OpeningTimes(leumiBranch.shaot.ob3.patch(2,":",0),leumiBranch.shaot.cb3.patch(2,":",0)),
        OpeningTimes(leumiBranch.shaot.oe3.patch(2,":",0), leumiBranch.shaot.ce3.patch(2,":",0))),
      List(
        OpeningTimes(leumiBranch.shaot.ob4.patch(2,":",0),leumiBranch.shaot.cb4.patch(2,":",0)),
        OpeningTimes(leumiBranch.shaot.oe4.patch(2,":",0), leumiBranch.shaot.ce4.patch(2,":",0))),
      List(
        OpeningTimes(leumiBranch.shaot.ob5.patch(2,":",0),leumiBranch.shaot.cb5.patch(2,":",0)),
        OpeningTimes(leumiBranch.shaot.oe5.patch(2,":",0), leumiBranch.shaot.ce5.patch(2,":",0))),
      List(
        OpeningTimes(leumiBranch.shaot.ob6.patch(2,":",0),leumiBranch.shaot.cb6.patch(2,":",0)),
        OpeningTimes(leumiBranch.shaot.oe6.patch(2,":",0), leumiBranch.shaot.ce6.patch(2,":",0))),
      List(
        OpeningTimes(leumiBranch.shaot.ob7.patch(2,":",0),leumiBranch.shaot.cb7.patch(2,":",0)),
        OpeningTimes(leumiBranch.shaot.oe7.patch(2,":",0), leumiBranch.shaot.ce7.patch(2,":",0)))
    )
  }
  
  def mapLeumiBranchToObpAtm(leumiBranch: LeumiBranch): InboundAtmJune2017 = {
    
    InboundAtmJune2017(
      atmId = AtmId(leumiBranch.branchCode),
      bankId = BankId("10"),
      name = leumiBranch.name,
      address = Address(
        line1 = leumiBranch.address,
        line2 = "",
        line3 = "",
        city = leumiBranch.city,
        county = None,
        state = "",
        postCode = leumiBranch.zipcode,
        countryCode = "IL"),
      location = Location(leumiBranch.x.toDouble,leumiBranch.y.toDouble),
      meta = Meta(License(id = "pddl", name = "Open Data Commons Public Domain Dedication and License (PDDL)")),

      OpeningTimeOnMonday = Some(leumiBranch.shaot.ob1.patch(2,":",0)),
      ClosingTimeOnMonday = Some(leumiBranch.shaot.ce1.patch(2,":",0)),

      OpeningTimeOnTuesday = Some(leumiBranch.shaot.ob2.patch(2,":",0)),
      ClosingTimeOnTuesday = Some(leumiBranch.shaot.ce2.patch(2,":",0)),

      OpeningTimeOnWednesday = Some(leumiBranch.shaot.ob3.patch(2,":",0)),
      ClosingTimeOnWednesday = Some(leumiBranch.shaot.ce3.patch(2,":",0)),

      OpeningTimeOnThursday = Some(leumiBranch.shaot.ob4.patch(2,":",0)),
      ClosingTimeOnThursday = Some(leumiBranch.shaot.ce4.patch(2,":",0)),

      OpeningTimeOnFriday = Some(leumiBranch.shaot.ob5.patch(2,":",0)),
      ClosingTimeOnFriday = Some(leumiBranch.shaot.ce5.patch(2,":",0)),

      OpeningTimeOnSaturday  = Some(leumiBranch.shaot.ob6.patch(2,":",0)),
      ClosingTimeOnSaturday = Some(leumiBranch.shaot.ce6.patch(2,":",0)),

      OpeningTimeOnSunday = Some(leumiBranch.shaot.ob7.patch(2,":",0)),
      ClosingTimeOnSunday = Some(leumiBranch.shaot.ce7.patch(2,":",0)),
      isAccessible = Some(leumiBranch.accessibility),

      locatedAt = Some(""),
      moreInfo = if (leumiBranch.isAnAtmUsablebyVisuallyImpaired) Some("כספומט מותאם ללקויי ראייה") else Some(""),
      hasDepositCapability = Some(leumiBranch.hasAtmWithDeposit)
    )
  }
  
  def createInboundGetCounterpartiesError(authInfo: AuthInfo, x: PAPIErrorResponse) = InboundGetCounterparties(authInfo, List(InternalCounterparty(
    errorCode = MainFrameError,
    backendMessages = createInboundStatusMessages(x),
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

  def createInboundGetBranchesError(authInfo: AuthInfo) = {
    
  }
  
  //Helper functions end here--------------------------------------------------------------------------------------------

  //Processor functions start here---------------------------------------------------------------------------------------

  override def getBanks(getBanks: OutboundGetBanks) = {
    InboundGetBanks(getBanks.authInfo,Status(), List(InboundBank("10", "leumi", "", "")))
  }

  override def getBank(getBank: OutboundGetBank) = {
    if (getBank.bankId == "10")  {
      InboundGetBank(getBank.authInfo, Status(),
        InboundBank("10", "leumi", "", ""))
    } else {
      InboundGetBank(getBank.authInfo, Status("error: Bank not found: " + getBank.bankId),
        InboundBank("", "", "", ""))
    }
  }


  def getBankAccountbyAccountId(getAccount: OutboundGetAccountbyAccountID): InboundGetAccountbyAccountID = {

    val username = getAccount.authInfo.username
    val account = getBasicBankAccountByAccountIdFromCachedJoni(username, getAccount.accountId)
    account match {
      case Right(account) =>
    
    val cbsToken =  if (getAccount.authInfo.isFirst == false && account.cbsToken != getAccount.authInfo.cbsToken) {
      throw new RuntimeException(mFTokenMatchError)
    } else account.cbsToken
    val ntib2Call = getNtib2Mf(
      account.branchNr,
      account.accountType,
      account.accountNr,
      username,
      cbsToken
    )
    ntib2Call match {
      case Right(x) =>

        val iban = x.SHETACHTCHUVA.TS00_PIRTEY_TCHUVA.TS00_TV_TCHUVA.TS00_NIGRERET_TCHUVA.TS00_IBAN
        val nt1cbCall = getNt1cB(username, account.branchNr, account.accountType, account.accountNr, cbsToken)
        nt1cbCall match {
          case Right(y) =>

            val balance = y.TSHUVATAVLAIT.HH_MISGAROT_ASHRAI.HH_PIRTEY_CHESHBON.HH_MATI.HH_ITRA_NOCHECHIT
            val creditLimit = y.TSHUVATAVLAIT.HH_MISGAROT_ASHRAI.HH_PIRTEY_CHESHBON.HH_MATI.HH_MISGERET_ASHRAI
            println("creditLimit - " + creditLimit)
            InboundGetAccountbyAccountID(AuthInfo(getAccount.authInfo.userId,
              getAccount.authInfo.username,
              account.cbsToken),
              mapBasicBankAccountToInboundAccountJune2017(username, account, iban, balance, creditLimit)
            )
          case Left(y) =>
            InboundGetAccountbyAccountID(getAccount.authInfo, InboundAccountJune2017(MainFrameError, 
              createInboundStatusMessages(y),
              "", "", "", "", "", "", "", "", List(""), List(""), "", "", "", "", "", "", Nil))
        }
      case Left(x) =>
        InboundGetAccountbyAccountID(getAccount.authInfo, InboundAccountJune2017(MainFrameError,
         createInboundStatusMessages(x),
          "", "", "", "", "", "", "", "", List(""), List(""), "", "", "", "", "", "", Nil))
    }
      case Left(account) =>
        InboundGetAccountbyAccountID(getAccount.authInfo, InboundAccountJune2017(MainFrameError,
          createInboundStatusMessages(account),
          "", "", "", "", "", "", "", "", List(""), List(""), "", "", "", "", "", "", Nil))
    }
        
  }

  def checkBankAccountExists(getAccount: OutboundCheckBankAccountExists): InboundGetAccountbyAccountID = {

    val account = getBasicBankAccountByAccountIdFromCachedJoni(getAccount.authInfo.username, getAccount.accountId)
    account match {
      case Right(account) =>
    val iban = ""
    val cbsToken =  if (getAccount.authInfo.isFirst == false && account.cbsToken != getAccount.authInfo.cbsToken) {
      throw new RuntimeException(mFTokenMatchError)
    } else account.cbsToken
    InboundGetAccountbyAccountID(AuthInfo(getAccount.authInfo.userId,
      getAccount.authInfo.username,
      cbsToken),
      mapBasicBankAccountToInboundAccountJune2017(getAccount.authInfo.username, account, "", "0", "")
    )
      case Left(account) =>
        InboundGetAccountbyAccountID(getAccount.authInfo, InboundAccountJune2017(MainFrameError
          , createInboundStatusMessages(account),
          "", "", "", "", "", "", "", "", List(""), List(""), "", "", "", "", "", "", Nil))
    }
  }

  def getBankAccounts(getAccountsInput: OutboundGetAccounts): InboundGetAccounts = {
    logger.debug("Enter getBankAccounts")
    val mfAccounts = getBasicBankAccountsForUser(getAccountsInput.authInfo.username, !getAccountsInput.callMfFlag)
    mfAccounts match {
      case Right(mfAccounts) =>
          val result = mfAccounts.map(x => mapBasicBankAccountToInboundAccountJune2017(getAccountsInput.authInfo.username, x, "", "0", ""))
        
        InboundGetAccounts(AuthInfo(getAccountsInput.authInfo.userId,
          getAccountsInput.authInfo.username,
          mfAccounts.headOption.getOrElse(
            throw new Exception("No Accounts for username: " + getAccountsInput.authInfo.username)).cbsToken), result)
      case Left(x) =>
        InboundGetAccounts(getAccountsInput.authInfo,
          List(InboundAccountJune2017(
            errorCode = MainFrameError,
            createInboundStatusMessages(x), 
            "",
            bankId = "",
            branchId = "",
            accountId = "",
            accountNumber = "",
            accountType = "",
            balanceAmount = "",
            balanceCurrency = "",
            owners = List(""),
            viewsToGenerate = List(""),
            bankRoutingScheme = "",
            bankRoutingAddress = "",
            branchRoutingScheme = "",
            branchRoutingAddress = "",
            accountRoutingScheme = "",
            accountRoutingAddress = "",
            accountRules = Nil)))
    }
  }

  def getCoreBankAccounts(getCoreBankAccounts: OutboundGetCoreBankAccounts): InboundGetCoreBankAccounts = {
      val inputAccountIds = getCoreBankAccounts.bankIdAccountIds.map(_.accountId.value)
      val accounts = getBasicBankAccountsForUser(getCoreBankAccounts.authInfo.username, true)
      accounts match {
        case Right(accounts) =>


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
        case Left(x) =>
          InboundGetCoreBankAccounts(getCoreBankAccounts.authInfo, List(InternalInboundCoreAccount(
            errorCode = "",
            backendMessages = createInboundStatusMessages(x),
            id = "",
            label = "",
            bank_id = "",
            account_routing = AccountRouting(scheme = "", address = "")
          )))
      }
          
  }

  def getTransactions(getTransactionsRequest: OutboundGetTransactions): InboundGetTransactions = {
    
    val account = getBasicBankAccountByAccountIdFromCachedJoni(getTransactionsRequest.authInfo.username, getTransactionsRequest.accountId)
    account match {
      case Right(account) =>
    val fromDate = LocalDate.parse(getTransactionsRequest.fromDate, formatterDefaultFilter)
    val toDate = LocalDate.parse(getTransactionsRequest.toDate,formatterDefaultFilter)
        val fromDay = fromDate.getDayOfMonth.toString
        val fromMonth = fromDate.getMonthValue.toString
        val fromYear = fromDate.getYear.toString
        val toDay = toDate.getDayOfMonth.toString
        val toMonth = toDate.getMonthValue.toString
        val toYear = toDate.getYear.toString 
    val cbsToken = if (account.cbsToken != getTransactionsRequest.authInfo.cbsToken  && 
    !getTransactionsRequest.authInfo.isFirst) {
      throw new RuntimeException(mFTokenMatchError)
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
        InboundGetTransactions(getTransactionsRequest.authInfo, List(InternalTransaction(MainFrameError,
          createInboundStatusMessages(x), "","","","",null,"","","","","","","","","")))
    }
      case Left(account) =>
        InboundGetTransactions(getTransactionsRequest.authInfo, List(InternalTransaction(MainFrameError,
          createInboundStatusMessages(account), "","","","",null,"","","","","","","","","")))
    }
        
  }

  def getTransaction(getTransactionRequest: OutboundGetTransaction): InboundGetTransaction = {
    logger.debug(s"get Transaction for ($getTransactionRequest)")
    val allTransactions: List[InternalTransaction] = {

      val transactionDate: String = cachedTransactionId.get(getTransactionRequest.transactionId).getOrElse(
        throw new Exception("Invalid TransactionId")
      ).completedDate
      val simpleTransactionDate: String = defaultInboundFormat.format(simpleTransactionDateFormat.parse(transactionDate))
      getTransactions(OutboundGetTransactions(getTransactionRequest.authInfo,
        getTransactionRequest.bankId,
        getTransactionRequest.accountId,
        50,
        simpleTransactionDate, simpleTransactionDate
      )).data

    }

    val resultTransaction = allTransactions.find(x => x.transactionId == getTransactionRequest.transactionId).getOrElse(throw new Exception("Invalid TransactionID"))
    InboundGetTransaction(getTransactionRequest.authInfo, resultTransaction)

  }

  def createTransaction(createTransactionRequest: OutboundCreateTransaction): InboundCreateTransactionId = {
    logger.debug(s"LeumiDecoder-createTransaction input: ($createTransactionRequest)")
    // As to this page: https://github.com/OpenBankProject/OBP-Adapter_Leumi/wiki/NTBD_1_135#-these-parameters-have-to-come-from-the-api
    // OBP-API will provide: four values:
    val account = getBasicBankAccountByAccountIdFromCachedJoni(createTransactionRequest.authInfo.username, createTransactionRequest.fromAccountId)
    account match {
      case Right(account) =>

        val branchId = account.branchNr
        val accountNumber = account.accountNr
        val accountType = account.accountType
        val username = createTransactionRequest.authInfo.username
        val cbsToken = if (account.cbsToken != createTransactionRequest.authInfo.cbsToken && 
        !createTransactionRequest.authInfo.isFirst) {
          throw new RuntimeException(mFTokenMatchError)
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
          callNtbd1_135 match {
            case Right(x) =>
              val callNtbd2_135 = getNtbd2v135Mf(branchId,
                accountType,
                accountNumber,
                username,
                cbsToken,
                ntbd1v135_Token = x.P135_BDIKAOUT.P135_TOKEN,
                nicknameOfMoneySender = transactionRequestBodyPhoneToPhoneJson.from.nickname,
                messageToMoneyReceiver = transactionMessage)
              callNtbd2_135 match {
                case Right(y) =>
                  InboundCreateTransactionId(createTransactionRequest.authInfo,
                    InternalTransactionId("", List(InboundStatusMessage("ESB", "Success", "0", "OK")),
                      transactionNewId))
                case Left(y) =>
                  InboundCreateTransactionId(createTransactionRequest.authInfo,
                    InternalTransactionId(MainFrameError, createInboundStatusMessages(y),
                      ""))
              }
            case Left(x) =>
              InboundCreateTransactionId(createTransactionRequest.authInfo,
                InternalTransactionId(MainFrameError, createInboundStatusMessages(x),
                  ""))

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
              
          callNtbd1v105 match {
            case Right(x) =>
              val callNtbd2v105 = getNtbd2v105Mf(
                branchId,
                accountType,
                accountNumber,
                cbsToken,
                ntbd1v105Token = x.P135_BDIKAOUT.P135_TOKEN,
                nicknameOfSender = transactionRequestBodyTransferToAtmJson.from.nickname,
                messageToReceiver = transactionRequestBodyTransferToAtmJson.message)
              callNtbd2v105 match {
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
                    InternalTransactionId(MainFrameError, createInboundStatusMessages(y),
                      ""))
              }
            case Left(x) =>
              InboundCreateTransactionId(createTransactionRequest.authInfo,
                InternalTransactionId(MainFrameError, createInboundStatusMessages(x),
                  ""))

          }


        } else if (createTransactionRequest.transactionRequestType == (TransactionRequestTypes.TRANSFER_TO_ACCOUNT.toString)) {
          val transactionRequestBodyTransferToAccountJson = createTransactionRequest.transactionRequestCommonBody.asInstanceOf[TransactionRequestBodyTransferToAccount]

          val callNtbdAv050 = getNtbdAv050(branchId,
            accountType,
            accountNumber,
            cbsToken,
            transactionRequestBodyTransferToAccountJson.transfer_type,
            transferDateInFuture = transactionRequestBodyTransferToAccountJson.future_date
          ) 
          callNtbdAv050 match {
            case Right(a) =>
              
              if (transactionRequestBodyTransferToAccountJson.value.amount.toDouble > a.P050_BDIKACHOVAOUT.P050_SCUM_MAX_LE_HAVARA.toDouble) throw new InvalidAmountException("Maximum Amount Allowed: " + a.P050_BDIKACHOVAOUT.P050_SCUM_MAX_LE_HAVARA)

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
              ) 
              callNtdBv050 match {
                case Right(b) =>

                  val callNtbdIv050 = getNtbdIv050(
                    branchId,
                    accountType,
                    accountNumber,
                    cbsToken,
                    ntbdAv050Token = transferToAccountToken,
                    transactionAmount = transactionRequestBodyTransferToAccountJson.value.amount
                  ) 
                  callNtbdIv050 match {
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
                      ) 
                      callNtbdGv050 match {
                        case Right(d) =>

                          val callNtbd2v050 = getNtbd2v050(
                            branchId,
                            accountType,
                            accountNumber,
                            cbsToken,
                            username,
                            ntbdAv050Token = transferToAccountToken,
                            ntbdAv050fromAccountOwnerName = a.P050_BDIKACHOVAOUT.P050_SHEM_HOVA_ANGLIT
                          ) 
                          callNtbd2v050 match {
                            case Right(e) =>
                              InboundCreateTransactionId(createTransactionRequest.authInfo,
                                InternalTransactionId("", List(InboundStatusMessage("ESB", "Success", "0", "OK")),
                                  transactionNewId))
                            case Left(e) =>
                              InboundCreateTransactionId(createTransactionRequest.authInfo,
                                InternalTransactionId(MainFrameError, createInboundStatusMessages(e),
                                  ""))
                          }
                        case Left(d) =>
                          InboundCreateTransactionId(createTransactionRequest.authInfo,
                            InternalTransactionId(MainFrameError, createInboundStatusMessages(d),
                              ""))
                      }
                    case Left(c) =>
                      InboundCreateTransactionId(createTransactionRequest.authInfo,
                        InternalTransactionId(MainFrameError, createInboundStatusMessages(c),
                          ""))
                  }
                case Left(b) =>
                  InboundCreateTransactionId(createTransactionRequest.authInfo,
                    InternalTransactionId(MainFrameError, createInboundStatusMessages(b),
                      ""))
              }
            case Left(a) =>
              InboundCreateTransactionId(createTransactionRequest.authInfo,
                InternalTransactionId(MainFrameError, createInboundStatusMessages(a),
                  ""))
          }

        } else {
          throw new RuntimeException("Do not support this transaction type, please check it in OBP-API side")
        }
      case Left(account) =>
        InboundCreateTransactionId(createTransactionRequest.authInfo,
          InternalTransactionId(MainFrameError, createInboundStatusMessages(account),
            ""))
    }
        



  }

  def createChallenge(createChallenge: OutboundCreateChallengeJune2017): InboundCreateChallengeJune2017 = {
    logger.debug(s"LeumiDecoder-createChallenge input: ($createChallenge)")
    val joniMfCall = getJoniMf(createChallenge.authInfo.username,false)
    joniMfCall match {
      case Right(joniMfCall) =>
        
    val account = getBasicBankAccountByAccountIdFromCachedJoni(createChallenge.authInfo.username, createChallenge.accountId)
    account match {
      case Right(account) =>
      val branchId = account.branchNr
      val accountNumber = account.accountNr
      val accountType = account.accountType
      val username = createChallenge.authInfo.username
      val cbsToken =  if (account.cbsToken != createChallenge.authInfo.cbsToken && 
      !createChallenge.authInfo.isFirst) {
        throw new RuntimeException(mFTokenMatchError)
      } else account.cbsToken
      val callNtlv1 = getNtlv1Mf(username,
        joniMfCall.SDR_JONI.SDR_MANUI.SDRM_ZEHUT,
        joniMfCall.SDR_JONI.SDR_MANUI.SDRM_SUG_ZIHUY,
        cbsToken
      )
      
      callNtlv1 match {
        case Right(x) =>
          //TODO: will use the first mobile phone contact available. Check.
          val mobilePhoneData = x.O1OUT1AREA_1.O1_CONTACT_REC.find(x => x.O1_TEL_USE_TYPE_CODE == "10").getOrElse(
            throw new InvalidMobilNumberException())
  
  
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
              InboundStatusMessage("ESB", "Success", "0", "OK"), 
              InboundStatusMessage("MF", "Success", "0", "OK") 
            ),
            answer))
  
        case Left(x) =>
  
          InboundCreateChallengeJune2017(createChallenge.authInfo, InternalCreateChallengeJune2017(
            "",
            createInboundStatusMessages(x),
            ""))
      }

      case Left(account) =>
        InboundCreateChallengeJune2017(createChallenge.authInfo, InternalCreateChallengeJune2017(
          "",
          createInboundStatusMessages(account),
          ""))
    }
      case Left(joniMfCall) =>
        InboundCreateChallengeJune2017(createChallenge.authInfo, InternalCreateChallengeJune2017(
          "",
          createInboundStatusMessages(joniMfCall),
          ""))
    }
        
  }

  def getTransactionRequests(outboundGetTransactionRequests210: OutboundGetTransactionRequests210): InboundGetTransactionRequests210 = {

    val accountId = outboundGetTransactionRequests210.counterparty.accountId
    val account = getBasicBankAccountByAccountIdFromCachedJoni(outboundGetTransactionRequests210.authInfo.username, outboundGetTransactionRequests210.counterparty.accountId)
    account match {
      case Right(account) =>
    
    val branchId = account.branchNr
    val accountNumber = account.accountNr
    val accountType = account.accountType
    val username = outboundGetTransactionRequests210.authInfo.username
    val cbsToken = if (outboundGetTransactionRequests210.authInfo.cbsToken != account.cbsToken && 
      !outboundGetTransactionRequests210.authInfo.isFirst) {
      throw new RuntimeException(mFTokenMatchError)
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


               
            val xResult = x.TA1TSHUVATAVLAIT1.TA1_SHETACH_LE_SEND_NOSAF.TA1_TNUOT.TA1_PIRTEY_TNUA.map(
              x => mapNt1c3ToTransactionRequest(x, accountId)) ++ y.TNATSHUVATAVLAIT1.TNA_SHETACH_LE_SEND_NOSAF.TNA_TNUOT.TNA_PIRTEY_TNUA.map(
                y => mapNt1c4ToTransactionRequest(y, accountId)
              )
            

            InboundGetTransactionRequests210(
              outboundGetTransactionRequests210.authInfo,
              InternalGetTransactionRequests(
                "",
                List(
                  InboundStatusMessage("ESB", "Success", "0", "OK"),
                  InboundStatusMessage("MF", "Success", "0", "OK")
                ),
                xResult))

          case Left(y) =>

            InboundGetTransactionRequests210(
              outboundGetTransactionRequests210.authInfo,
              InternalGetTransactionRequests(
                MainFrameError,
                createInboundStatusMessages(y),
                Nil))


        }
      case Left(x) =>

        InboundGetTransactionRequests210(
          outboundGetTransactionRequests210.authInfo,
          InternalGetTransactionRequests(
            MainFrameError,
            createInboundStatusMessages(x),
            Nil))


    }
      case Left(account) =>
        InboundGetTransactionRequests210(
          outboundGetTransactionRequests210.authInfo,
          InternalGetTransactionRequests(
            MainFrameError,
            createInboundStatusMessages(account),
            Nil))


    }
        
  }        

  def createCounterparty(outboundCreateCounterparty: OutboundCreateCounterparty): InboundCreateCounterparty = {
    val account = getBasicBankAccountByAccountIdFromCachedJoni(outboundCreateCounterparty.authInfo.username, outboundCreateCounterparty.counterparty.thisAccountId)
    account match {
      case Right(account) =>

        val branchId = account.branchNr
        val accountNumber = account.accountNr
        val accountType = account.accountType
        val cbsToken = account.cbsToken
        if (cbsToken != outboundCreateCounterparty.authInfo.cbsToken  && !outboundCreateCounterparty.authInfo.isFirst) throw new RuntimeException(mFTokenMatchError)

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
                  status = "",
                  createdByUserId = outboundCreateCounterparty.counterparty.createdByUserId,
                  name = outboundCreateCounterparty.counterparty.name,
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
            case Left(x) =>
              InboundCreateCounterparty(
                outboundCreateCounterparty.authInfo,
                InternalCreateCounterparty(
                  MainFrameError,
                  createInboundStatusMessages(x),
                  status = "",
                  createdByUserId = outboundCreateCounterparty.counterparty.createdByUserId,
                  name = outboundCreateCounterparty.counterparty.name,
                  thisBankId = outboundCreateCounterparty.counterparty.thisBankId,
                  thisAccountId = outboundCreateCounterparty.counterparty.thisAccountId,
                  thisViewId = outboundCreateCounterparty.counterparty.thisViewId,
                  counterpartyId = "",
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
                  status = "",
                  createdByUserId = outboundCreateCounterparty.counterparty.createdByUserId,
                  name = outboundCreateCounterparty.counterparty.name,
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

            case Left(x) =>
              InboundCreateCounterparty(
                outboundCreateCounterparty.authInfo,
                InternalCreateCounterparty(
                  MainFrameError,
                  createInboundStatusMessages(x),
                  status = "",
                  createdByUserId = outboundCreateCounterparty.counterparty.createdByUserId,
                  name = outboundCreateCounterparty.counterparty.name, 
                  thisBankId = outboundCreateCounterparty.counterparty.thisBankId,
                  thisAccountId = outboundCreateCounterparty.counterparty.thisAccountId,
                  thisViewId = outboundCreateCounterparty.counterparty.thisViewId,
                  counterpartyId = "", 
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
      case Left(account) =>
        InboundCreateCounterparty(
          outboundCreateCounterparty.authInfo,
          InternalCreateCounterparty(
            MainFrameError,
            createInboundStatusMessages(account),
            status = "",
            createdByUserId = outboundCreateCounterparty.counterparty.createdByUserId,
            name = outboundCreateCounterparty.counterparty.name,
            thisBankId = outboundCreateCounterparty.counterparty.thisBankId,
            thisAccountId = outboundCreateCounterparty.counterparty.thisAccountId,
            thisViewId = outboundCreateCounterparty.counterparty.thisViewId,
            counterpartyId = "",
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
          ))


    }
  }

  def getCustomer(outboundGetCustomersByUserIdFuture: OutboundGetCustomersByUserId): InboundGetCustomersByUserId = {
    val username = outboundGetCustomersByUserIdFuture.authInfo.username
    val joniMfCall = getJoniMf(username, false)
    joniMfCall match {
      case Right(joniMfCall) =>
        if (outboundGetCustomersByUserIdFuture.authInfo.isFirst == false &&
            outboundGetCustomersByUserIdFuture.authInfo.cbsToken != joniMfCall.SDR_JONI.MFTOKEN)
      throw new RuntimeException(mFTokenMatchError)
    val callNtlv1 = getNtlv1Mf(username,
      joniMfCall.SDR_JONI.SDR_MANUI.SDRM_ZEHUT,
      joniMfCall.SDR_JONI.SDR_MANUI.SDRM_SUG_ZIHUY,
      joniMfCall.SDR_JONI.MFTOKEN,
      outboundGetCustomersByUserIdFuture.authInfo.isFirst
    )
    callNtlv1 match {
      case Right(x) =>

        val mobilePhoneData = x.O1OUT1AREA_1.O1_CONTACT_REC.find(x => x.O1_TEL_USE_TYPE_CODE == "10").getOrElse(
          O1contactRec(O1recId("", ""), "", "", "", "", "", "", "", "", "", "", "", "", "", "", "", ""))

        val emailAddress = x.O1OUT1AREA_1.O1_CONTACT_REC.find(x => !x.O1_MAIL_ADDRESS.trim().isEmpty).getOrElse(
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
          dateOfBirth = getUtcDatefromLeumiLocalDate(joniMfCall.SDR_JONI.SDR_MANUI.SDRM_TAR_LEIDA), //JONI
          relationshipStatus = "",
          dependents = null,
          dobOfDependents = List(null),
          highestEducationAttained = "",
          employmentStatus = "",
          creditRating = CreditRating("", ""),
          creditLimit = AmountOfMoney("", ""),
          kycStatus = null,
          lastOkDate = getUtcDatefromLeumiDateTime(joniMfCall.SDR_JONI.SDR_MANUI.SDRM_DATE_LAST , joniMfCall.SDR_JONI.SDR_MANUI.SDRM_TIME_LAST) //JONI
        )
        InboundGetCustomersByUserId(outboundGetCustomersByUserIdFuture.authInfo, List(result))

      case Left(x)  =>
        
        
        InboundGetCustomersByUserId(outboundGetCustomersByUserIdFuture.authInfo, 
          List(InternalFullCustomer(
            status = "",
            errorCode = MainFrameError,
            backendMessages = createInboundStatusMessages(x),
            customerId = "",
            bankId = "",
            number = "",
            legalName = "",
            mobileNumber = "",
            email = "",
            faceImage = null,
            dateOfBirth = null,
            relationshipStatus = "",
            dependents = null,
            dobOfDependents = List(null),
            highestEducationAttained = "",
            employmentStatus = "",
            creditRating = CreditRating("", ""),
            creditLimit = AmountOfMoney("", ""),
            kycStatus = null,
            lastOkDate = null
          )))
    }
      case Left(joniMfCall) =>
        InboundGetCustomersByUserId(outboundGetCustomersByUserIdFuture.authInfo,
          List(InternalFullCustomer(
            status = "",
            errorCode = MainFrameError,
            backendMessages = createInboundStatusMessages(joniMfCall),
            customerId = "",
            bankId = "",
            number = "",
            legalName = "",
            mobileNumber = "",
            email = "",
            faceImage = null,
            dateOfBirth = null,
            relationshipStatus = "",
            dependents = null,
            dobOfDependents = List(null),
            highestEducationAttained = "",
            employmentStatus = "",
            creditRating = CreditRating("", ""),
            creditLimit = AmountOfMoney("", ""),
            kycStatus = null,
            lastOkDate = null
          )))
    }
        
  }

  def getCounterpartiesForAccount(outboundGetCounterparties: OutboundGetCounterparties): InboundGetCounterparties = {
    val isFirst = outboundGetCounterparties.authInfo.isFirst
    val joniCall = getJoniMf(outboundGetCounterparties.authInfo.username, false)
    joniCall match {
      case Right(joniCall) =>
    
    if (joniCall.SDR_JONI.MFTOKEN != outboundGetCounterparties.authInfo.cbsToken && !isFirst) throw new RuntimeException(mFTokenMatchError)

    val account = getBasicBankAccountByAccountIdFromCachedJoni(outboundGetCounterparties.authInfo.username, outboundGetCounterparties.counterparty.thisAccountId)
    account match {
      case Right(account) =>
        
    val branchId = account.branchNr
    val accountNumber = account.accountNr
    val accountType = account.accountType
        
    
    var result = new ListBuffer[InternalCounterparty]
    
    val ntg6ICall = getNtg6IMf(branchId,accountType,accountNumber, outboundGetCounterparties.authInfo.cbsToken, isFirst)

        ntg6ICall match {
          case Right(x) =>
            for (i <- x.PMUTSHLIFA_OUT.PMUT_RESHIMAT_MUTAVIM) {
              result += mapAdapterCounterpartyToInternalCounterparty(i.PMUT_PIRTEY_MUTAV,
                outboundGetCounterparties.counterparty,
                outboundGetCounterparties.counterparty.thisAccountId)
            }

            val ntg6KCall = getNtg6KMf(branchId, accountType, accountNumber, outboundGetCounterparties.authInfo.cbsToken, isFirst)
            ntg6KCall match {
              case Right(y) =>
                for (u <- y.PMUTSHLIFA_OUT.PMUT_RESHIMAT_MUTAVIM){
                  result += mapAdapterCounterpartyToInternalCounterparty(u.PMUT_PIRTEY_MUTAV, outboundGetCounterparties.counterparty, outboundGetCounterparties.counterparty.thisAccountId)
                }
                val returnValue = result.toList
                InboundGetCounterparties(outboundGetCounterparties.authInfo, returnValue)

              case Left(y) if y.PAPIErrorResponse.MFAdminResponse.returnCode == "B" =>

                val returnValue = result.toList
                InboundGetCounterparties(outboundGetCounterparties.authInfo, returnValue)

              case Left(y) =>
                createInboundGetCounterpartiesError(outboundGetCounterparties.authInfo, y)

            }
          case Left(x) if x.PAPIErrorResponse.MFAdminResponse.returnCode == "B" =>

            val ntg6KCall = getNtg6KMf(branchId, accountType, accountNumber, outboundGetCounterparties.authInfo.cbsToken, isFirst)
            ntg6KCall match {
              case Right(y) =>
                for (u <- y.PMUTSHLIFA_OUT.PMUT_RESHIMAT_MUTAVIM) {
                  result += mapAdapterCounterpartyToInternalCounterparty(u.PMUT_PIRTEY_MUTAV, outboundGetCounterparties.counterparty, "")
                }
                val returnValue = result.toList
                InboundGetCounterparties(outboundGetCounterparties.authInfo, returnValue)

              case Left(y) if y.PAPIErrorResponse.MFAdminResponse.returnCode == "B" =>

                val returnValue = result.toList
                InboundGetCounterparties(outboundGetCounterparties.authInfo, returnValue)

              case Left(y) =>
                createInboundGetCounterpartiesError(outboundGetCounterparties.authInfo, y)
            }

          case Left(x) =>
            createInboundGetCounterpartiesError(outboundGetCounterparties.authInfo, x)
        }
      case Left(account) =>
        createInboundGetCounterpartiesError(outboundGetCounterparties.authInfo, account)
    }
      case Left(joniCall) =>
        createInboundGetCounterpartiesError(outboundGetCounterparties.authInfo, joniCall)
    }

  }
      
  def getCounterpartyByCounterpartyId(outboundGetCounterpartyByCounterpartyId: OutboundGetCounterpartyByCounterpartyId) = {
    
    val thisAccountId = cachedCounterpartyIds.get(outboundGetCounterpartyByCounterpartyId.counterparty.counterpartyId).getOrElse(throw new CounterpartyIdCacheEmptyException())
    if  (thisAccountId != checkBankAccountExists(
      OutboundCheckBankAccountExists(
        outboundGetCounterpartyByCounterpartyId.authInfo,
        defaultBankId,
        thisAccountId)).data.accountId) throw new InvalidCounterPartyIdException
   
    val counterparties = getCounterpartiesForAccount(OutboundGetCounterparties(
        outboundGetCounterpartyByCounterpartyId.authInfo, 
        InternalOutboundGetCounterparties(
          "10",
          thisAccountId,
          ""))).data
    
    val internalCounterparty = counterparties.find(x => 
        x.counterpartyId == outboundGetCounterpartyByCounterpartyId.counterparty.counterpartyId).getOrElse(throw new InvalidCounterPartyIdException(s"$InvalidCounterPartyId Current CounterpartyId =${outboundGetCounterpartyByCounterpartyId.counterparty.counterpartyId}"))
    InboundGetCounterparty(outboundGetCounterpartyByCounterpartyId.authInfo, internalCounterparty)
      
  }

  def getCounterparty(outboundGetCounterparty: OutboundGetCounterparty) = {
    
    val thisAccountId = outboundGetCounterparty.thisAccountId

    if  (thisAccountId != checkBankAccountExists(
      OutboundCheckBankAccountExists(
        outboundGetCounterparty.authInfo,
        outboundGetCounterparty.thisBankId,
        thisAccountId)).data.accountId) throw new InvalidCounterPartyIdException

    val counterparties = getCounterpartiesForAccount(OutboundGetCounterparties(
      outboundGetCounterparty.authInfo,
      InternalOutboundGetCounterparties(
        "10",
        thisAccountId,
        ""))).data

    val internalCounterparty = counterparties.find(x =>
        x.counterpartyId == outboundGetCounterparty.counterpartyId).getOrElse(throw new InvalidCounterPartyIdException(s"$InvalidCounterPartyId Current CounterpartyId =${outboundGetCounterparty.counterpartyId}"))
    InboundGetCounterparty(outboundGetCounterparty.authInfo, internalCounterparty)

  }
  
  def getBranches(outboundGetBranches: OutboundGetBranches): InboundGetBranches = {
    
      InboundGetBranches(
        outboundGetBranches.authInfo,Status(),
        getLeumiBranches.map(x => mapLeumiBranchToObpBranch(x))
      )
    }
 
  def getBranch(outboundGetBranch: OutboundGetBranch): InboundGetBranch = {
    val branch = getLeumiBranches.find(x => x.branchCode == outboundGetBranch.branchId).getOrElse(throw new InvalidBranchIdExecption())
    InboundGetBranch(outboundGetBranch.authInfo, Status(),mapLeumiBranchToObpBranch(branch))
  }
  
  def getAtms(outboundGetAtms: OutboundGetAtms): InboundGetAtms = {
    val branchesWithAtm: List[LeumiBranch] = getLeumiBranches.filter(x => x.hasAtm)
    branchesWithAtm.map( x => mapLeumiBranchToObpAtm(x))
    InboundGetAtms(outboundGetAtms.authInfo, Status(), branchesWithAtm.map(x => mapLeumiBranchToObpAtm(x)))
  }
  
  def getAtm(outboundGetAtm: OutboundGetAtm): InboundGetAtm = { 
    val atm = getLeumiBranches.find(x => x.hasAtm && x.branchCode == outboundGetAtm.atmId).getOrElse(throw new InvalidAtmIdExecption(InvalidAtmId + "is: " + outboundGetAtm.atmId))
    InboundGetAtm(outboundGetAtm.authInfo, Status(), mapLeumiBranchToObpAtm(atm))
  }

}



