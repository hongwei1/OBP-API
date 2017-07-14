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
case class GetAdapterInfo(date: String)
case class GetAccounts(authInfo: AuthInfo)
case class GetAccount(authInfo: AuthInfo, bankId: String, accountId: String)
case class GetUserByUsernamePassword(username: String, password: String)
case class UpdateUserAccountViews(username: String, password: String)


/**
  * Payloads for response topic
  *
  */
case class Banks(authInfo: AuthInfo, data: List[InboundBank])
case class BankWrapper(authInfo: AuthInfo, data: Option[InboundBank])
case class AdapterInfo(data: Option[InboundAdapterInfo])
case class UserWrapper(data: Option[InboundValidatedUser])
case class OutboundUserAccountViewsBaseWapper(data: List[InboundAccountJune2017])
case class BankAccounts(authInfo: AuthInfo, data: List[InboundAccountJune2017])
case class BankAccount(authInfo: AuthInfo, data: InboundAccountJune2017)

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

case class InboundAccountJune2017(
                                   errorCode: String,
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