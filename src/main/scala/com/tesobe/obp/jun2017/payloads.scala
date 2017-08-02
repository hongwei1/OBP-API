package com.tesobe.obp.june2017


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
  * Carries data related to auth
  *
  * @param userId
  * @param username
  */
case class AuthInfo(userId: String, username: String, cbsToken: String)

/**
  * Payloads for request topic
  *
  */
case class GetBanks(authInfo: AuthInfo, criteria: String)
case class GetBank(authInfo: AuthInfo, bankId: String)
case class GetAdapterInfo(date: String)
case class GetAccounts(authInfo: AuthInfo)
case class GetAccountbyAccountID(authInfo: AuthInfo, bankId: String, accountId: String)
case class GetAccountbyAccountNumber(authInfo: AuthInfo, bankId: String, accountNumber: String)
case class GetUserByUsernamePassword(authInfo: AuthInfo, password: String)
case class UpdateUserAccountViews(authInfo: AuthInfo, password: String)
case class GetTransactiondfss(authInfo: AuthInfo,bankId: String, accountId: String, limit: Int, fromDate: String, toDate: String)
case class GetTransactions(authInfo: AuthInfo, bankId: String, accountId: String, limit: Int, fromDate: String, toDate: String)
case class GetTransaction(authInfo: AuthInfo, bankId: String, accountId: String, transactionId: String)
case class GetToken(username: String)


/**
  * Payloads for response topic
  *
  */
case class Banks(authInfo: AuthInfo, data: List[InboundBank])
case class BankWrapper(authInfo: AuthInfo, data: Option[InboundBank])
case class AdapterInfo(data: Option[InboundAdapterInfo])
case class UserWrapper(data: Option[InboundValidatedUser])
case class OutboundUserAccountViewsBaseWapper(data: List[InboundAccountJune2017])
case class InboundBankAccounts(authInfo: AuthInfo, data: List[InboundAccountJune2017])
case class InboundBankAccount(authInfo: AuthInfo, data: InboundAccountJune2017)
case class InboundTransactions(authInfo: AuthInfo, data: List[InternalTransaction])
case class InboundTransaction(authInfo: AuthInfo, data: InternalTransaction)
case class InboundToken(username: String, token: String)

/**
  * All subsequent case classes must be the same structure as it is defined on North Side
  *
  */
case class InboundBank(
                        errorCode: String,
                        bankId: String,
                        name: String,
                        logo: String,
                        url: String
                      )
case class InboundValidatedUser(
                  errorCode: Option[String],
                  email: Option[String],
                  displayName: Option[String]
                )

case class InboundAdapterInfo(
  errorCode: String,
  name: String,
  version: String,
  git_commit: String,
  date: String
)

case class InboundAccountJune2017(errorCode: String,
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
                                  accountRoutingAddress: String)

abstract class InboundMessageBase(optionalFields: String*) {
  def errorCode: String
}

case class InternalTransaction(
  //Base : "TN2_TSHUVA_TAVLAIT":"TN2_SHETACH_LE_SEND_NOSAF":"TN2_TNUOT":"TN2_PIRTEY_TNUA":["TN2_TNUA_BODEDET"                              
  errorCode: String,
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