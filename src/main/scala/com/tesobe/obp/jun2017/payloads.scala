package com.tesobe.obp.jun2017

/**
  * Here are defined all the things that go through kafka
  *
  */

/**
  * Carries data related to auth
  *
  * @param userId
  * @param username
  */
case class AuthInfo(userId: String, username: String)

/**
  * Payloads for request topic
  *
  */
case class GetBanks(authInfo: AuthInfo, criteria: String)
case class GetBank(authInfo: AuthInfo, bankId: String)
case class GetUserByUsernamePassword(username: String, password: String)
case class UpdateUserAccountViews(username: String, password: String)
case class GetUserBankAccounts(authInfo: AuthInfo, bankId: String)

/**
  * Payloads for response topic
  *
  */
case class Banks(authInfo: AuthInfo, data: List[InboundBank])
case class BankWrapper(authInfo: AuthInfo, data: Option[InboundBank])
case class UserWrapper(data: Option[InboundValidatedUser])
case class BankAccounts(authInfo: AuthInfo, data: Seq[InboundAccount])

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

case class InboundAccount(
                           errorCode: String,
                           accountId: String,
                           bankId: String,
                           label: String,
                           number: String,
                           `type`: String,
                           balanceAmount: String,
                           balanceCurrency: String,
                           iban: String,
                           owners: List[String],
                           generatePublicView: Boolean,
                           generateAccountantsView: Boolean,
                           generateAuditorsView: Boolean,
                           accountRoutingScheme: String  = "None",
                           accountRoutingAddress: String  = "None",
                           branchId: String  = "None"
                         )

