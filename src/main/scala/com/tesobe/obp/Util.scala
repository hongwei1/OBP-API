package com.tesobe.obp

import java.text.SimpleDateFormat

import com.typesafe.scalalogging.StrictLogging


/**
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
object Util extends StrictLogging {
  /*
    Return the git commit. If we can't for some reason (not a git root etc) then log and return ""
     */
  def gitCommit : String = {
    val commit = try {
      val properties = new java.util.Properties()
      logger.debug("Before getResourceAsStream git.properties")
      properties.load(getClass().getClassLoader().getResourceAsStream("git.properties"))
      logger.debug("Before get Property git.commit.id")
      properties.getProperty("git.commit.id", "")
    } catch {
      case e : Throwable => {
        logger.warn("gitCommit says: Could not return git commit. Does resources/git.properties exist?")
        logger.error(s"Exception in gitCommit: $e")
        "" // Return empty string
      }
    }
    commit
  }
  
  object TransactionRequestTypes extends Enumeration {
    type TransactionRequestTypes = Value
    val SANDBOX_TAN, COUNTERPARTY, SEPA, FREE_FORM, TRANSFER_TO_PHONE, TRANSFER_TO_ATM, TRANSFER_TO_ACCOUNT = Value
  }
  
}

object ErrorMessages {
  
  // Notes to developers. Please:
  // 1) Follow (the existing) grouping of messages
  // 2) Stick to existing terminology e.g. use "invalid" or "incorrect" rather than "wrong"
  // 3) Before adding a new message, check that you can't use one that already exists.
  // 4) Use Proper Names for OBP Resources.
  // 5) Don't use abbreviations.
  val defaultFilterFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
  val fallBackFilterFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")
  
  // Infrastructure / config level messages (OBP-00XXX)
  val HostnameNotSpecified = "OBP-00001: Hostname not specified. Could not get hostname from Props. Please edit your props file. Here are some example settings: hostname=http://127.0.0.1:8080 or hostname=https://www.example.com"
  val DataImportDisabled  = "OBP-00002: Data import is disabled for this API instance."
  val TransactionDisabled = "OBP-00003: Transaction Requests is disabled in this API instance."
  
  @deprecated("This is too generic","25-06-2017")
  val ServerAddDataError = "OBP-00004: Server error: could not add message" // Do not use this
  
  val PublicViewsNotAllowedOnThisInstance = "OBP-00005: Public views not allowed on this instance. Please set allow_public_views = true in props files. "
  
  
  val RemoteDataSecretMatchError = "OBP-00006: Remote data secret cannot be matched! Check OBP-API and OBP-Storage Props values for remotedata.hostname, remotedata.port and remotedata.secret." // (was OBP-20021)
  val RemoteDataSecretObtainError = "OBP-00007: Remote data secret cannot be obtained! Check OBP-API and OBP-Storage Props values for remotedata.hostname, remotedata.port and remotedata.secret." // (was OBP-20022)
  
  
  
  // General messages (OBP-10XXX)
  val InvalidJsonFormat = "OBP-10001: Incorrect json format."
  val InvalidNumber = "OBP-10002: Invalid Number. Could not convert value to a number."
  val InvalidISOCurrencyCode = "OBP-10003: Invalid Currency Value. It should be three letters ISO Currency Code. "
  val FXCurrencyCodeCombinationsNotSupported = "OBP-10004: ISO Currency code combination not supported for FX. Please modify the FROM_CURRENCY_CODE or TO_CURRENCY_CODE. "
  val InvalidDateFormat = "OBP-10005: Invalid Date Format. Could not convert value to a Date."
  val InvalidInputJsonFormat = "OBP-10006: Invalid input JSON format." // Why do we need this as well as InvalidJsonFormat?
  val IncorrectRoleName = "OBP-10007: Incorrect Role name: "
  val CouldNotTransformJsonToInternalModel = "OBP-10008: Could not transform Json to internal model."
  val CountNotSaveOrUpdateResource = "OBP-10009: Could not save or update resource."
  val NotImplemented = "OBP-10010: Not Implemented "
  
  // General Sort and Paging
  val FilterSortDirectionError = "OBP-10023: obp_sort_direction parameter can only take two values: DESC or ASC!" // was OBP-20023
  val FilterOffersetError = "OBP-10024: wrong value for obp_offset parameter. Please send a positive integer (=>0)!" // was OBP-20024
  val FilterLimitError = "OBP-10025: wrong value for obp_limit parameter. Please send a positive integer (=>1)!" // was OBP-20025
  val FilterDateFormatError = s"OBP-10026: Failed to parse date string. Please use this format ${defaultFilterFormat.toPattern} or that one ${fallBackFilterFormat.toPattern}!" // OBP-20026
  
  
  
  // Authentication / Authorisation / User messages (OBP-20XXX)
  val UserNotLoggedIn = "OBP-20001: User not logged in. Authentication is required!"
  val DirectLoginMissingParameters = "OBP-20002: These DirectLogin parameters are missing: "
  val DirectLoginInvalidToken = "OBP-20003: This DirectLogin token is invalid or expired: "
  val InvalidLoginCredentials = "OBP-20004: Invalid login credentials. Check username/password."
  val UserNotFoundById = "OBP-20005: User not found. Please specify a valid value for USER_ID."
  val UserHasMissingRoles = "OBP-20006: User is missing one or more roles: "
  val UserNotFoundByEmail = "OBP-20007: User not found by email."
  
  val InvalidConsumerKey = "OBP-20008: Invalid Consumer Key."
  val InvalidConsumerCredentials = "OBP-20009: Invalid consumer credentials"
  
  val InvalidValueLength = "OBP-20010: Value too long"
  val InvalidValueCharacters = "OBP-20011: Value contains invalid characters"
  
  val InvalidDirectLoginParameters = "OBP-20012: Invalid direct login parameters"
  
  val UsernameHasBeenLocked = "OBP-20013: The account has been locked, please contact administrator !"
  
  val InvalidConsumerId = "OBP-20014: Invalid Consumer ID. Please specify a valid value for CONSUMER_ID."
  
  val UserNoPermissionUpdateConsumer = "OBP-20015: Only the developer that created the consumer key should be able to edit it, please login with the right user."
  
  val UnexpectedErrorDuringLogin = "OBP-20016: An unexpected login error occurred. Please try again."
  
  val UserNoPermissionAccessView = "OBP-20017: Current user does not have access to the view. Please specify a valid value for VIEW_ID."
  
  
  val InvalidInternalRedirectUrl = "OBP-20018: Login failed, invalid internal redirectUrl."
  
  
  
  val UserNotFoundByUsername = "OBP-20027: User not found by username."
  val GatewayLoginMissingParameters = "OBP-20028: These GatewayLogin parameters are missing: "
  val GatewayLoginUnknownError = "OBP-20029: Unknown Gateway login error."
  val GatewayLoginHostPropertyMissing = "OBP-20030: Property gateway.host is not defined."
  val GatewayLoginWhiteListAddresses = "OBP-20031: Gateway login can be done only from allowed addresses."
  val GatewayLoginJwtTokenIsNotValid = "OBP-20040: The JWT is corrupted/changed during a transport."
  val GatewayLoginCannotExtractJwtToken = "OBP-20040: Header, Payload and Signature cannot be extracted from the JWT."
  
  
  
  
  // Resource related messages (OBP-30XXX)
  val BankNotFound = "OBP-30001: Bank not found. Please specify a valid value for BANK_ID."
  val CustomerNotFound = "OBP-30002: Customer not found. Please specify a valid value for CUSTOMER_NUMBER."
  val CustomerNotFoundByCustomerId = "OBP-30002: Customer not found. Please specify a valid value for CUSTOMER_ID."
  
  val AccountNotFound = "OBP-30003: Account not found. Please specify a valid value for ACCOUNT_ID."
  val CounterpartyNotFound = "OBP-30004: Counterparty not found. The BANK_ID / ACCOUNT_ID specified does not exist on this server."
  
  val ViewNotFound = "OBP-30005: View not found for Account. Please specify a valid value for VIEW_ID"
  
  val CustomerNumberAlreadyExists = "OBP-30006: Customer Number already exists. Please specify a different value for BANK_ID or CUSTOMER_NUMBER."
  val CustomerAlreadyExistsForUser = "OBP-30007: The User is already linked to a Customer at the bank specified by BANK_ID"
  val UserCustomerLinksNotFoundForUser = "OBP-30008: User Customer Link not found by USER_ID"
  val AtmNotFoundByAtmId = "OBP-30009: ATM not found. Please specify a valid value for ATM_ID."
  val BranchNotFoundByBranchId = "OBP-300010: Branch not found. Please specify a valid value for BRANCH_ID."
  val ProductNotFoundByProductCode = "OBP-30011: Product not found. Please specify a valid value for PRODUCT_CODE."
  val CounterpartyNotFoundByIban = "OBP-30012: Counterparty not found. Please specify a valid value for IBAN."
  val CounterpartyBeneficiaryPermit = "OBP-30013: The account can not send money to the Counterparty. Please set the Counterparty 'isBeneficiary' true first"
  val CounterpartyAlreadyExists = "OBP-30014: Counterparty already exists. Please specify a different value for BANK_ID or ACCOUNT_ID or VIEW_ID or NAME."
  val CreateBranchError = "OBP-30015: Could not insert the Branch"
  val UpdateBranchError = "OBP-30016: Could not update the Branch"
  val CounterpartyNotFoundByCounterpartyId = "OBP-30017: Counterparty not found. Please specify a valid value for COUNTERPARTY_ID."
  val BankAccountNotFound = "OBP-30018: Bank Account not found. Please specify valid values for BANK_ID and ACCOUNT_ID. "
  val ConsumerNotFoundByConsumerId = "OBP-30019: Consumer not found. Please specify a valid value for CONSUMER_ID."
  
  val CreateBankError = "OBP-30020: Could not create the Bank"
  val UpdateBankError = "OBP-30021: Could not update the Bank"
  val ViewNoPermission = "OBP-30022: The current view does not have the permission: "
  val UpdateConsumerError = "OBP-30023: Cannot update Consumer "
  val CreateConsumerError = "OBP-30024: Could not create Consumer "
  val CreateUserCustomerLinksError = "OBP-30025: Could not create user_customer_links "
  val ConsumerKeyAlreadyExists = "OBP-30026: Consumer Key already exists. Please specify a different value."
  val NoExistingAccountHolders = "OBP-30027: Account Holders not found. The BANK_ID / ACCOUNT_ID specified for account holder does not exist on this server"
  
  
  val CreateAtmError = "OBP-30028: Could not insert the ATM"
  val UpdateAtmError = "OBP-30029: Could not update the ATM"
  
  val CreateProductError = "OBP-30030: Could not insert the Product"
  val UpdateProductError = "OBP-30031: Could not update the Product"
  
  val CreateCardError = "OBP-30032: Could not insert the Card"
  val UpdateCardError = "OBP-30033: Could not update the Card"
  
  val ViewIdNotSupported = "OBP-30034: This ViewId is do not supported. Only support four now: Owner, Public, Accountant, Auditor."
  
  
  val UserCustomerLinkNotFound = "OBP-30035: User Customer Link not found"
  
  
  
  // Meetings
  val MeetingsNotSupported = "OBP-30101: Meetings are not supported on this server."
  val MeetingApiKeyNotConfigured = "OBP-30102: Meeting provider API Key is not configured."
  val MeetingApiSecretNotConfigured = "OBP-30103: Meeting provider Secret is not configured."
  val MeetingNotFound = "OBP-30104: Meeting not found."
  
  
  val InvalidAccountBalanceCurrency = "OBP-30105: Invalid Balance Currency."
  val InvalidAccountBalanceAmount = "OBP-30106: Invalid Balance Amount."
  
  val InvalidUserId = "OBP-30107: Invalid User Id."
  val InvalidAccountType = "OBP-30108: Invalid Account Type."
  val InitialBalanceMustBeZero = "OBP-30109: Initial Balance of Account must be Zero (0)."
  val InvalidAccountIdFormat = "OBP-30110: Invalid Account Id. The ACCOUNT_ID should only contain 0-9/a-z/A-Z/'-'/'.'/'_', the length should be smaller than 255."
  val InvalidBankIdFormat = "OBP-30111: Invalid Bank Id. The BANK_ID should only contain 0-9/a-z/A-Z/'-'/'.'/'_', the length should be smaller than 255."
  val InvalidAccountInitialBalance = "OBP-30112: Invalid Number. Initial balance must be a number, e.g 1000.00"
  
  
  val EntitlementIsBankRole = "OBP-30205: This entitlement is a Bank Role. Please set bank_id to a valid bank id."
  val EntitlementIsSystemRole = "OBP-30206: This entitlement is a System Role. Please set bank_id to empty string."
  
  
  val InvalidStrongPasswordFormat = "OBP-30207: Invalid Password Format. Your password should EITHER be at least 10 characters long and contain mixed numbers and both upper and lower case letters and at least one special character, OR be longer than 16 characters."
  
  val AccountIdAlreadyExsits = "OBP-30208: Account_ID already exists at the Bank."
  
  
  val InsufficientAuthorisationToCreateBranch  = "OBP-30209: Insufficient authorisation to Create Branch. You do not have the role CanCreateBranch." // was OBP-20019
  val InsufficientAuthorisationToCreateBank  = "OBP-30210: Insufficient authorisation to Create Bank. You do not have the role CanCreateBank." // was OBP-20020
  
  // General Resource related messages above here
  
  
  // Transaction Request related messages (OBP-40XXX)
  val InvalidTransactionRequestType = "OBP-40001: Invalid value for TRANSACTION_REQUEST_TYPE"
  val InsufficientAuthorisationToCreateTransactionRequest  = "OBP-40002: Insufficient authorisation to create TransactionRequest. The Transaction Request could not be created because you don't have access to the owner view of the from account or you don't have access to canCreateAnyTransactionRequest."
  val InvalidTransactionRequestCurrency = "OBP-40003: Transaction Request Currency must be the same as From Account Currency."
  val InvalidTransactionRequestId = "OBP-40004: Transaction Request Id not found."
  val InsufficientAuthorisationToCreateTransactionType  = "OBP-40005: Insufficient authorisation to Create Transaction Type offered by the bank. The Request could not be created because you don't have access to CanCreateTransactionType."
  val CreateTransactionTypeInsertError  = "OBP-40006: Could not insert Transaction Type: Non unique BANK_ID / SHORT_CODE"
  val CreateTransactionTypeUpdateError  = "OBP-40007: Could not update Transaction Type: Non unique BANK_ID / SHORT_CODE"
  val NotPositiveAmount = "OBP-40008: Can't send a payment with a value of 0 or less."
  val TransactionRequestTypeHasChanged = "OBP-40009: The TRANSACTION_REQUEST_TYPE has changed."
  val InvalidTransactionRequesChallengeId = "OBP-40010: Invalid Challenge Id. Please specify a valid value for CHALLENGE_ID."
  val TransactionRequestStatusNotInitiated = "OBP-40011: Transaction Request Status is not INITIATED."
  val CounterpartyNotFoundOtherAccountProvider = "OBP-40012: Please set up the otherAccountRoutingScheme and otherBankRoutingScheme fields of the Counterparty to 'OBP'"
  val InvalidChargePolicy = "OBP-40013: Invalid Charge Policy. Please specify a valid value for Charge_Policy: SHARED, SENDER or RECEIVER. "
  val AllowedAttemptsUsedUp = "OBP-40014: Sorry, you've used up your allowed attempts. "
  val InvalidChallengeType = "OBP-40015: Invalid Challenge Type. Please specify a valid value for CHALLENGE_TYPE, when you create the transaction request."
  val InvalidChallengeAnswer = "OBP-40016: Invalid Challenge Answer. Please specify a valid value for answer in Json body."
  val InvalidPhoneNumber = "OBP-40017: Invalid Phone Number. Please specify a valid value for PHONE_NUMBER. Eg:+9722398746 "
  
  
  
  // Exceptions (OBP-50XXX)
  val UnknownError = "OBP-50000: Unknown Error."
  val FutureTimeoutException = "OBP-50001: Future Timeout Exception."
  val KafkaMessageClassCastException = "OBP-50002: Kafka Response Message Class Cast Exception."
  val AdapterOrCoreBankingSystemException = "OBP-50003: Adapter Or Core Banking System Exception. Failed to get a valid response from the south side Adapter or Core Banking System."
  // This error may not be shown to user, just for debugging.
  val CurrentUserNotFoundException = "OBP-50004: Method (AuthUser.getCurrentUser) can not find the current user in the current context!"
  
  // Connector Data Exceptions (OBP-502XX)
  val ConnectorEmptyResponse = "OBP-50200: Connector cannot return the data we requested." // was OBP-30200
  val InvalidConnectorResponseForGetBankAccounts = "OBP-50201: Connector did not return the set of accounts we requested."  // was OBP-30201
  val InvalidConnectorResponseForGetBankAccount = "OBP-50202: Connector did not return the account we requested."  // was OBP-30202
  val InvalidConnectorResponseForGetTransaction = "OBP-50203: Connector did not return the transaction we requested."  // was OBP-30203
  val InvalidConnectorResponseForGetTransactions = "OBP-50204: Connector did not return the set of transactions we requested."  // was OBP-30204
  
  
  // Adapter Exceptions (OBP-6XXXX)
  // Reserved for adapter (south of Kafka) messages
  val NoCreditCard = "OBP-60000: No valid credit card or no credit card with sufficient withdrawal limit found. "
  val JoniCacheEmpty = "OBP-60001: Joni Cache return empty, do not cache it before! "
  val JoniFailed = "OBP-60002: Joni Call to CBS failed. "
  val SessionError = "OBP-60003: Invalid Session. "
  val InvalidMobilNumber = "OBP-60004: Invalid Mobile Number, we only support Israel country code. It should start as :+972xxxxxxxxx ."
  val InvalidAccountId = "OBP-60005: Invalid AccountId, the following Account Id is not in Joni cache. "
  val InvalidRequestFormat = "OBP-60006: Invalid Request Format: Parameter for Backendcall not in malformated."
  val InvalidCounterPartyId = "OBP-60007: Invalid or uncached counterpartyId. "
  val MainFrameError = "OBP-60008: Main Frame Error ."
  val InvalidPassportOrNationalId = "OBP-60009: Invalid Passport or National ID number"
  val InvalidAmount = "OBP-60010: Invalid Amount"
  val InvalidIdType = "OBP-60011: Invalid Id Type: valid types: 1 - National; 5- Passport"
  val mFTokenMatchError = "OBP-60012: Session error - cbs_token from North side does not match MFTOKEN from South side."
  val EmptyUsernameError = "OBP-60013: Request was sent with empty username."
  val InvalidTimeError = "OBP-60014: Invalid time or time format."
  val CounterpartyIdNotCached = "OBP-60015: CounterpartyId not in Cache."

  
  
  ///////////
  
  // Adapter Exceptions (OBP-6XXXX)
  // Reserved for adapter (south of Kafka) messages
  
  
  class JoniCacheEmptyException(msg: String = JoniCacheEmpty) extends Exception(msg: String)
  class JoniFailedException(msg: String = JoniFailed) extends Exception(msg: String)
  class InvalidMobilNumberException(msg: String = InvalidMobilNumber) extends Exception(msg: String)
  class InvalidAccountIdException(msg: String = InvalidAccountId) extends Exception(msg: String)
  class SessionErrorException(msg: String = SessionError) extends Exception(msg: String)
  class InvalidRequestFormatException(msg: String = InvalidRequestFormat) extends Exception(msg: String)
  class InvalidCounterPartyIdException(msg: String = InvalidCounterPartyId) extends Exception(msg: String)
  class InvalidPassportOrNationalIdException(msg: String = InvalidPassportOrNationalId) extends Exception(msg: String)
  class InvalidAmountException(msg: String = InvalidAmount) extends Exception(msg: String)
  class InvalidIdTypeException(msg: String = InvalidIdType) extends Exception(msg: String)
  class CounterpartyIdCacheEmptyException(msg: String = CounterpartyIdNotCached)extends Exception(msg: String)
  
}


