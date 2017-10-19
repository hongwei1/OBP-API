package com.tesobe.obp.june2017

import java.util.Date
import net.liftweb.json.JsonAST.JValue

/**
  * Here are defined all the things that go through kafka
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
  *
  */

/**
  * case classes used to define topics
  */
sealed trait TopicTrait
/**
  * Payloads for request topic
  *
  */
case class OutboundGetAdapterInfo(date: String) extends TopicTrait
case class OutboundGetBanks(authInfo: AuthInfo) extends TopicTrait
case class OutboundGetBank(authInfo: AuthInfo, bankId: String) extends TopicTrait
case class OutboundGetUserByUsernamePassword(authInfo: AuthInfo, password: String) extends TopicTrait
case class OutboundGetAccounts(authInfo: AuthInfo, customers:InternalCustomers)  extends TopicTrait
case class OutboundGetCoreAccounts(authInfo: AuthInfo) extends TopicTrait
case class OutboundGetAccountbyAccountID(authInfo: AuthInfo, bankId: String, accountId: String) extends TopicTrait
case class OutboundGetAccountbyAccountNumber(authInfo: AuthInfo, bankId: String, accountNumber: String) extends TopicTrait
case class OutboundGetTransactions(authInfo: AuthInfo, bankId: String, accountId: String, limit: Int, fromDate: String, toDate: String) extends TopicTrait
case class OutboundGetTransaction(authInfo: AuthInfo, bankId: String, accountId: String, transactionId: String) extends TopicTrait
case class OutboundGetToken(username: String) extends TopicTrait
case class OutboundCreateTransaction(
  authInfo: AuthInfo,
  
  // fromAccount
  fromAccountBankId : String,
  fromAccountId : String,
  
  // transaction details
  transactionRequestType: String,
  transactionChargePolicy: String,
  transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
  
  // toAccount or toCounterparty
  toCounterpartyId: String,
  toCounterpartyName: String,
  toCounterpartyCurrency: String,
  toCounterpartyRoutingAddress: String,
  toCounterpartyRoutingScheme: String,
  toCounterpartyBankRoutingAddress: String,
  toCounterpartyBankRoutingScheme: String

) extends TopicTrait

case class OutboundCreateChallengeJune2017(
  authInfo: AuthInfo,
  bankId: String,
  accountId: String,
  userId: String,
  username: String,
  transactionRequestType: String,
  transactionRequestId: String
) extends TopicTrait

case class OutboundCreateCounterparty(
  authInfo: AuthInfo,
  counterparty: OutboundCounterparty
) extends TopicTrait

case class OutboundGetTransactionRequests210(authInfo: AuthInfo, counterparty: OutboundTransactionRequests) extends TopicTrait
/**
  * Payloads for response topic
  *
  */
case class InboundAdapterInfo(data: InboundAdapterInfoInternal)
case class InboundGetUserByUsernamePassword(authInfo: AuthInfo, data: InboundValidatedUser)
case class InboundGetBanks(authInfo: AuthInfo, data: List[InboundBank])
case class InboundGetBank(authInfo: AuthInfo, data: InboundBank)
case class InboundGetAccounts(authInfo: AuthInfo, data: List[InboundAccountJune2017])
case class InboundGetAccountbyAccountID(authInfo: AuthInfo, data: InboundAccountJune2017)
case class InboundGetTransactions(authInfo: AuthInfo, data: List[InternalTransaction])
case class InboundGetTransaction(authInfo: AuthInfo, data: InternalTransaction)
case class InboundCreateChallengeJune2017(authInfo: AuthInfo, data: InternalCreateChallengeJune2017)
case class InboundCreateCounterparty(authInfo: AuthInfo, data: InternalCreateCounterparty)
case class InboundToken(username: String, token: String)
case class InboundCreateTransactionId(authInfo: AuthInfo, data: InternalTransactionId)
case class InboundGetTransactionRequests210(authInfo: AuthInfo, data: InternalGetTransactionRequests)
case class InboundGetCoreAccounts(authInfo: AuthInfo,backendMessages: List[InboundStatusMessage], data: List[CoreAccountJsonV300])

/**
  * All subsequent case classes must be the same structure as it is defined on North Side
  *
  */

case class CoreAccountJsonV300(
                                id : String,
                                label : String,
                                bank_id : String,
                                account_routing: AccountRoutingJsonV121
                              )

case class AccountRoutingJsonV121(
                                   scheme: String,
                                   address: String
                                 )

case class PostCounterpartyBespoke(
  key: String,
  value: String
)

case class OutboundCounterparty(
  name: String,
  description: String,
  createdByUserId: String,
  thisBankId: String,
  thisAccountId: String,
  thisViewId: String,
  otherAccountRoutingScheme: String,
  otherAccountRoutingAddress: String,
  otherAccountSecondaryRoutingScheme: String,
  otherAccountSecondaryRoutingAddress: String,
  otherBankRoutingScheme: String,
  otherBankRoutingAddress: String,
  otherBranchRoutingScheme: String,
  otherBranchRoutingAddress: String,
  isBeneficiary:Boolean,
  bespoke: List[PostCounterpartyBespoke]
)

case class AuthInfo(userId: String, username: String, cbsToken: String)

case class InboundBank(
  errorCode: String,
  backendMessages: List[InboundStatusMessage],
  bankId: String,
  name: String,
  logo: String,
  url: String
)

case class InboundStatusMessage(
  source: String,
  status: String,
  errorCode: String,
  text: String
)

case class InboundValidatedUser(
  errorCode: String,
  backendMessages: List[InboundStatusMessage],
  email: String,
  displayName: String
)

case class InboundAdapterInfoInternal(
  errorCode: String,
  backendMessages: List[InboundStatusMessage],
  name: String,
  version: String,
  git_commit: String,
  date: String
)

case class InboundAccountJune2017(
  errorCode: String,
  backendMessages: List[InboundStatusMessage],
  cbsToken: String,
  bankId: String,
  branchId: String,
  accountId: String,
  accountNumber: String,
  accountType: String,
  balanceAmount: String,
  balanceCurrency: String,
  owners: List[String],
  viewsToGenerate: List[String],
  bankRoutingScheme: String,
  bankRoutingAddress: String,
  branchRoutingScheme: String,
  branchRoutingAddress: String,
  accountRoutingScheme: String,
  accountRoutingAddress: String
)

abstract class InboundMessageBase(optionalFields: String*) {
  def errorCode: String
}

case class InternalTransaction(
  //Base : "TN2_TSHUVA_TAVLAIT":"TN2_SHETACH_LE_SEND_NOSAF":"TN2_TNUOT":"TN2_PIRTEY_TNUA":["TN2_TNUA_BODEDET"                              
  errorCode: String,
  backendMessages: List[InboundStatusMessage],
  transactionId: String, // Find some
  accountId: String, //accountId
  amount: String, //:"TN2_SCHUM"
  bankId: String, // 10 for now (Joni)
  completedDate: String, //"TN2_TA_ERECH": // Date of value for
  counterpartyId: String,
  counterpartyName: String,
  currency: String, //ILS 
  description: String, //"TN2_TEUR_PEULA":
  newBalanceAmount: String, //"TN2_ITRA":
  newBalanceCurrency: String, //ILS
  postedDate: String, //"TN2_TA_IBUD": // Date of transaction
  `type`: String, //"TN2_SUG_PEULA"
  userId: String //userId
) extends InboundMessageBase


// these are for create transactions
case class AmountOfMoneyJsonV121(
  currency: String,
  amount: String
)

case class TransactionRequestAccountJsonV140(
  bank_id: String,
  account_id: String
)

//For SEPA, it need the iban to find the toCounterpaty--> toBankAccount
case class IbanJson(val iban: String)

//For COUNTERPATY, it need the counterparty_id to find the toCounterpaty--> toBankAccount
case class CounterpartyIdJson(val counterparty_id: String)

//high level of four different kinds of transaction request types: FREE_FROM, SANDBOXTAN, COUNTERPATY and SEPA.
//They share the same AmountOfMoney and description fields
//Note : in scala case-to-case inheritance is prohibited, so used trait instead
sealed trait TransactionRequestCommonBodyJSON {
  val value: AmountOfMoneyJsonV121
  val description: String
}
// the common parts of four types
// note: there is TransactionRequestCommonBodyJSON trait, so this case class call TransactionRequestBodyCommonJSON
case class TransactionRequestBodyCommonJSON(
  value: AmountOfMoneyJsonV121,
  description: String
) extends TransactionRequestCommonBodyJSON

// the data from endpoint, extract as valid JSON
case class TransactionRequestBodySandBoxTanJSON(
  to: TransactionRequestAccountJsonV140,
  value: AmountOfMoneyJsonV121,
  description: String
) extends TransactionRequestCommonBodyJSON

case class TransactionRequestBodyCounterpartyJSON(
  to: CounterpartyIdJson,
  value: AmountOfMoneyJsonV121,
  description: String,
  charge_policy: String
) extends TransactionRequestCommonBodyJSON

// the data from endpoint, extract as valid JSON
case class TransactionRequestBodySEPAJSON(
  value: AmountOfMoneyJsonV121,
  to: IbanJson,
  description: String,
  charge_policy: String
) extends TransactionRequestCommonBodyJSON

case class ToAccountTransferToPhoneJson(
  mobile_phone_number: String
)

case class FromAccountTransfer (
  mobile_phone_number: String,
  nickname: String
)

case class TransactionRequestBodyTransferToPhoneJson(
  value: AmountOfMoneyJsonV121,
  description: String,
  message: String,
  from: FromAccountTransfer,
  to: ToAccountTransferToPhoneJson
) extends TransactionRequestCommonBodyJSON

case class ToAccountTransferToAtmKycDocumentJson(
  `type`: String,
  number: String
)

case class ToAccountTransferToAtmJson(
  legal_name: String,
  date_of_birth: String,
  mobile_phone_number: String,
  kyc_document: ToAccountTransferToAtmKycDocumentJson
)

case class TransactionRequestBodyTransferToAtmJson(
  value: AmountOfMoneyJsonV121,
  description: String,
  message: String,
  from: FromAccountTransfer,
  to: ToAccountTransferToAtmJson
) extends TransactionRequestCommonBodyJSON

case class ToAccountTransferToAccountAccountJson(
  number: String,
  iban: String
)

case class ToAccountTransferToAccountJson(
  name: String,
  bank_code: String,
  branch_number : String,
  account:ToAccountTransferToAccountAccountJson
)

case class TransactionRequestBodyTransferToAccount(
  value: AmountOfMoneyJsonV121,
  description: String,
  transfer_type: String,
  future_date: String,
  to: ToAccountTransferToAccountJson
) extends TransactionRequestCommonBodyJSON

case class InternalTransactionId(
  errorCode: String,
  backendMessages: List[InboundStatusMessage],
  id : String
)

case class InternalCreateChallengeJune2017(
  errorCode: String,
  backendMessages: List[InboundStatusMessage],
  answer : String
)

case class InternalCreateCounterparty(
  errorCode: String,
  backendMessages: List[InboundStatusMessage],
  status: String,
  createdByUserId: String = "",
  name: String  = "",
  thisBankId: String = "",
  thisAccountId: String= "",
  thisViewId: String= "",
  counterpartyId: String= "",
  otherAccountRoutingScheme: String= "",
  otherAccountRoutingAddress: String= "",
  otherBankRoutingScheme: String= "",
  otherBankRoutingAddress: String = "",
  otherBranchRoutingScheme: String= "",
  otherBranchRoutingAddress: String= "",
  isBeneficiary: Boolean = true,
  description: String= "",
  otherAccountSecondaryRoutingScheme: String = "",
  otherAccountSecondaryRoutingAddress: String = "",
  bespoke: List[PostCounterpartyBespoke] = Nil
)

case class InternalGetTransactionRequests(
  errorCode: String,
  backendMessages: List[InboundStatusMessage],
  transactionRequests:List[TransactionRequest]
)

case class TransactionRequestId(val value : String) {
  override def toString = value
}

object TransactionRequestId {
  def unapply(id : String) = Some(TransactionRequestId(id))
}
case class TransactionRequestAccount (
  val bank_id: String,
  val account_id : String
)
case class TransactionRequestBody (
  val to: TransactionRequestAccount,
  val value : AmountOfMoney,
  val description : String
)
case class AmountOfMoney (
  val currency: String,
  val amount: String
)

case class TransactionRequestChallenge (
  val id: String,
  val allowed_attempts : Int,
  val challenge_type: String
)

case class TransactionRequestCharge(
  val summary: String,
  val value : AmountOfMoney
)
case class CounterpartyId(val value : String) {
  override def toString = value
}

object CounterpartyId {
  def unapply(id : String) = Some(CounterpartyId(id))
}

case class AccountId(val value : String) {
  override def toString = value
}

object AccountId {
  def unapply(id : String) = Some(AccountId(id))
}

case class BankId(val value : String) {
  override def toString = value
}

object BankId {
  def unapply(id : String) = Some(BankId(id))
}
case class ViewId(val value : String) {
  override def toString = value
}

object ViewId {
  def unapply(id : String) = Some(ViewId(id))
}

case class TransactionRequest (
  id: TransactionRequestId,
  `type` : String,
  from: TransactionRequestAccount,
  details: JValue,
  body: TransactionRequestBody, 
  transaction_ids: String,
  status: String,
  start_date: Date,
  end_date: Date,
  challenge: TransactionRequestChallenge,
  charge: TransactionRequestCharge,
  charge_policy: String,
  counterparty_id :CounterpartyId,
  name :String,
  this_bank_id : BankId,
  this_account_id : AccountId,
  this_view_id :ViewId,
  other_account_routing_scheme : String,
  other_account_routing_address : String,
  other_bank_routing_scheme : String,
  other_bank_routing_address : String,
  is_beneficiary :Boolean
)

case class CustomerFaceImageJson(
  url: String,
  date: String
)

case class CustomerCreditRatingJSON(
  rating: String,
  source: String
)

case class InternalCustomer(
  bankId:String,
  customerId: String,
  customerNumber: String,
  legalName: String,
  dateOfBirth: String
)

case class InternalCustomers(customers: List[InternalCustomer])

case class OutboundTransactionRequests(
  accountId: String,
  accountType: String,
  currency: String,
  iban: String,
  number: String,
  bankId: String,
  branchId: String,
  accountRoutingScheme: String,
  accountRoutingAddress: String
)

