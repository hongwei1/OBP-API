package code.api.berlin.group.v1_3

import java.text.SimpleDateFormat

import code.api.APIFailureNewStyle
import code.api.berlin.group.v1_3.JSONFactory_BERLIN_GROUP_1_3._
import code.api.util.APIUtil.{defaultBankId, passesPsd2Aisp, _}
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.NewStyle.HttpCode
import code.api.util.{ApiTag, NewStyle}
import code.bankconnectors.Connector
import code.consent.{ConsentStatus, Consents}
import code.model._
import code.util.Helper
import code.views.Views
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model.{AccountId, BankId, BankIdAccountId, ViewId}
import net.liftweb.common.Full
import net.liftweb.http.js.JE.JsRaw
import net.liftweb.http.rest.RestHelper
import net.liftweb.json
import net.liftweb.json._

import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

object APIMethods_AccountInformationServiceAISApi extends RestHelper {
    val apiVersion =  OBP_BERLIN_GROUP_1_3.apiVersion
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    protected implicit def JvalueToSuper(what: JValue): JvalueCaseClass = JvalueCaseClass(what)

    val endpoints = 
      createConsent ::
      deleteConsent ::
      getAccountList ::
      getBalances ::
      getConsentAuthorisation ::
      getConsentInformation ::
      getConsentScaStatus ::
      getConsentStatus ::
      getTransactionList ::
      startConsentAuthorisation ::
      Nil

            
     resourceDocs += ResourceDoc(
       createConsent,
       apiVersion,
       nameOf(createConsent),
       "POST",
       "/consents",
       "Create consent",
       s"""${mockedDataText(true)}
This method create a consent resource, defining access rights to dedicated accounts of 
a given PSU-ID. These accounts are addressed explicitly in the method as 
parameters as a core function.

**Side Effects**
When this Consent Request is a request where the "recurringIndicator" equals "true", 
and if it exists already a former consent for recurring access on account information 
for the addressed PSU, then the former consent automatically expires as soon as the new 
consent request is authorised by the PSU.

Optional Extension:
As an option, an ASPSP might optionally accept a specific access right on the access on all psd2 related services for all available accounts. 

As another option an ASPSP might optionally also accept a command, where only access rights are inserted without mentioning the addressed account. 
The relation to accounts is then handled afterwards between PSU and ASPSP. 
This option is not supported for the Embedded SCA Approach. 
As a last option, an ASPSP might in addition accept a command with access rights
* to see the list of available payment accounts or
* to see the list of available payment accounts with balances.
""",
       json.parse("""{
                    |  "access": {
                    |    "accounts": [
                    |      {
                    |        "iban": "FR7612345987650123456789014",
                    |        "bban": "BARC12345612345678",
                    |        "pan": "5409050000000000",
                    |        "maskedPan": "123456xxxxxx1234",
                    |        "msisdn": "+49 170 1234567",
                    |        "currency": "EUR"
                    |      }
                    |    ],
                    |    "balances": [
                    |      {
                    |        "iban": "FR7612345987650123456789014",
                    |        "bban": "BARC12345612345678",
                    |        "pan": "5409050000000000",
                    |        "maskedPan": "123456xxxxxx1234",
                    |        "msisdn": "+49 170 1234567",
                    |        "currency": "EUR"
                    |      }
                    |    ],
                    |    "transactions": [
                    |      {
                    |        "iban": "FR7612345987650123456789014",
                    |        "bban": "BARC12345612345678",
                    |        "pan": "5409050000000000",
                    |        "maskedPan": "123456xxxxxx1234",
                    |        "msisdn": "+49 170 1234567",
                    |        "currency": "EUR"
                    |      }
                    |    ],
                    |    "availableAccounts": "allAccounts",
                    |    "allPsd2": "allAccounts"
                    |  },
                    |  "recurringIndicator": false,
                    |  "validUntil": "2020-12-31",
                    |  "frequencyPerDay": 4,
                    |  "combinedServiceIndicator": false
                    |}""".stripMargin),
       json.parse("""{
                    |  "consentStatus": "received",
                    |  "consentId": "string",
                    |  "scaMethods": [
                    |    {
                    |      "authenticationType": "SMS_OTP",
                    |      "authenticationVersion": "string",
                    |      "authenticationMethodId": "myAuthenticationID",
                    |      "name": "SMS OTP on phone +49160 xxxxx 28",
                    |      "explanation": "Detailed information about the SCA method for the PSU."
                    |    }
                    |  ],
                    |  "chosenScaMethod": {
                    |    "authenticationType": "SMS_OTP",
                    |    "authenticationVersion": "string",
                    |    "authenticationMethodId": "myAuthenticationID",
                    |    "name": "SMS OTP on phone +49160 xxxxx 28",
                    |    "explanation": "Detailed information about the SCA method for the PSU."
                    |  },
                    |  "challengeData": {
                    |    "image": "string",
                    |    "data": [
                    |      "string"
                    |    ],
                    |    "imageLink": "string",
                    |    "otpMaxLength": 0,
                    |    "otpFormat": "characters",
                    |    "additionalInformation": "string"
                    |  },
                    |  "_links": {
                    |    "scaRedirect": {
                    |      "href": "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
                    |    },
                    |    "scaOAuth": {
                    |      "href": "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
                    |    },
                    |    "startAuthorisation": {
                    |      "href": "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
                    |    },
                    |    "startAuthorisationWithPsuIdentification": {
                    |      "href": "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
                    |    },
                    |    "startAuthorisationWithPsuAuthentication": {
                    |      "href": "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
                    |    },
                    |    "startAuthorisationWithEncryptedPsuAuthentication": {
                    |      "href": "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
                    |    },
                    |    "startAuthorisationWithAuthenticationMethodSelection": {
                    |      "href": "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
                    |    },
                    |    "startAuthorisationWithTransactionAuthorisation": {
                    |      "href": "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
                    |    },
                    |    "self": {
                    |      "href": "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
                    |    },
                    |    "status": {
                    |      "href": "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
                    |    },
                    |    "scaStatus": {
                    |      "href": "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
                    |    },
                    |    "additionalProp1": {
                    |      "href": "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
                    |    },
                    |    "additionalProp2": {
                    |      "href": "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
                    |    },
                    |    "additionalProp3": {
                    |      "href": "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
                    |    }
                    |  },
                    |  "message": "string"
                    |}""".stripMargin),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG),
       ApiTag("Account Information Service (AIS)") :: apiTagMockedData :: apiTagBerlinGroupM :: Nil
     )

     lazy val createConsent : OBPEndpoint = {
       case "consents" :: Nil JsonPost json -> _  =>  {
         cc =>
           for {
             (Full(u), callContext) <- authorizedAccess(cc)
             _ <- passesPsd2Aisp(callContext)
             failMsg = s"$InvalidJsonFormat The Json body should be the $PostConsentJson "
             consentJson <- NewStyle.function.tryons(failMsg, 400, callContext) {
               json.extract[PostConsentJson]
             }

             failMsg = s"$InvalidDateFormat Current `validUntil` field is ${consentJson.validUntil}. Please use this format ${DateWithDayFormat.toPattern}!"
             validUntil <- NewStyle.function.tryons(failMsg, 400, callContext) {
               new SimpleDateFormat(DateWithDay).parse(consentJson.validUntil)
             }
             
             failMsg = s"$InvalidDateFormat Only Support empty accout List for now. It will retrun an accessible account list. "
             _ <- Helper.booleanToFuture(failMsg) {consentJson.access.accounts.get.isEmpty}
             
             createdConsent <- Future(Consents.consentProvider.vend.createBerlinGroupConsent(
               u,
               recurringIndicator = consentJson.recurringIndicator,
               validUntil = validUntil,
               frequencyPerDay = consentJson.frequencyPerDay,
               combinedServiceIndicator = consentJson.combinedServiceIndicator
             )) map {
               i => connectorEmptyResponse(i, callContext)
             }
           } yield {
             (createPostConsentResponseJson(createdConsent), HttpCode.`201`(callContext))
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       deleteConsent,
       apiVersion,
       nameOf(deleteConsent),
       "DELETE",
       "/consents/CONSENTID",
       "Delete Consent",
       s"""${mockedDataText(false)}
            The TPP can delete an account information consent object if needed.""",
       json.parse(""""""),
       json.parse(""""""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG),
       ApiTag("Account Information Service (AIS)")   :: apiTagBerlinGroupM :: Nil
     )

     lazy val deleteConsent : OBPEndpoint = {
       case "consents" :: consentId :: Nil JsonDelete _ => {
         cc =>
           for {
             (Full(user), callContext) <- authorizedAccess(cc)
             _ <- passesPsd2Aisp(callContext)
             consent <- Future(Consents.consentProvider.vend.getConsentByConsentId(consentId)) map {
               unboxFullOrFail(_, callContext, ConsentNotFound)
             }
             consent <- Future(Consents.consentProvider.vend.revoke(consentId)) map {
               i => connectorEmptyResponse(i, callContext)
             }
           } yield {
             (JsRaw(""), HttpCode.`204`(callContext))
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getAccountList,
       apiVersion,
       nameOf(getAccountList),
       "GET",
       "/accounts",
       "Read Account List",
       s"""${mockedDataText(true)}
            Read the identifiers of the available payment account together with booking balance information, depending on the consent granted.
            It is assumed that a consent of the PSU to this access is already given and stored on the ASPSP system.
            The addressed list of accounts depends then on the PSU ID and the stored consent addressed by consentId, respectively the OAuth2 access token.
            Returns all identifiers of the accounts, to which an account access has been granted to through the /consents endpoint by the PSU.
            In addition, relevant information about the accounts and hyperlinks to corresponding account information resources are provided if a related consent has been already granted.

            Remark: Note that the /consents endpoint optionally offers to grant an access on all available payment accounts of a PSU.
            In this case, this endpoint will deliver the information about all available payment accounts of the PSU at this ASPSP.

            """,
       json.parse(""""""),
       json.parse("""{
                    |  "accounts": [
                    |    {
                    |      "resourceId": "string",
                    |      "iban": "FR7612345987650123456789014",
                    |      "bban": "BARC12345612345678",
                    |      "msisdn": "+49 170 1234567",
                    |      "currency": "EUR",
                    |      "name": "string",
                    |      "product": "string",
                    |      "cashAccountType": "string",
                    |      "status": "enabled",
                    |      "bic": "AAAADEBBXXX",
                    |      "linkedAccounts": "string",
                    |      "usage": "PRIV",
                    |      "details": "string",
                    |      "balances": [
                    |        {
                    |          "balanceAmount": {
                    |            "currency": "EUR",
                    |            "amount": "123"
                    |          },
                    |          "balanceType": "closingBooked",
                    |          "creditLimitIncluded": false,
                    |          "lastChangeDateTime": "2019-06-28T13:46:05.062Z",
                    |          "referenceDate": "2019-06-28",
                    |          "lastCommittedTransaction": "string"
                    |        }
                    |      ],
                    |      "_links": {
                    |        "balances": {
                    |          "href": "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
                    |        },
                    |        "transactions": {
                    |          "href": "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
                    |        },
                    |        "additionalProp1": {
                    |          "href": "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
                    |        },
                    |        "additionalProp2": {
                    |          "href": "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
                    |        },
                    |        "additionalProp3": {
                    |          "href": "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
                    |        }
                    |      }
                    |    }
                    |  ]
                    |}""".stripMargin),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG),
       ApiTag("Account Information Service (AIS)")  :: apiTagMockedData :: apiTagBerlinGroupM :: Nil
     )

     lazy val getAccountList : OBPEndpoint = {
       case "accounts" :: Nil JsonGet _ => {
         cc =>
           for {
            (Full(u), callContext) <- authorizedAccess(cc)
  
            _ <- Helper.booleanToFuture(failMsg= DefaultBankIdNotSet ) {defaultBankId != "DEFAULT_BANK_ID_NOT_SET"}
  
            bankId = BankId(defaultBankId)
  
            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)
  
            availablePrivateAccounts <- Views.views.vend.getPrivateBankAccountsFuture(u, bankId)
            
            Full((coreAccounts,callContext1)) <- {Connector.connector.vend.getCoreBankAccounts(availablePrivateAccounts, callContext)}
            
          } yield {
            (JSONFactory_BERLIN_GROUP_1_3.createAccountListJson(coreAccounts), callContext)
          }
         }
       }
            
     resourceDocs += ResourceDoc(
       getBalances,
       apiVersion,
       nameOf(getBalances),
       "GET",
       "/accounts/ACCOUNT_ID/balances",
       "Read Balance",
       s"""${mockedDataText(false)}
            |Reads account data from a given account addressed by "account-id".
            |
            |**Remark:** This account-id can be a tokenised identification due to data protection reason since the path
            |information might be logged on intermediary servers within the ASPSP sphere. This account-id then can be
            |retrieved by the "GET Account List" call. The account-id is constant at least throughout the lifecycle of a
            |given consent.
            """.stripMargin,
       json.parse(""""""),
       json.parse(
         """
           |{
           |  "account": {
           |    "iban": "FR7612345987650123456789014",
           |    "bban": "BARC12345612345678",
           |    "pan": "5409050000000000",
           |    "maskedPan": "123456xxxxxx1234",
           |    "msisdn": "+49 170 1234567",
           |    "currency": "EUR"
           |  },
           |  "balances": [
           |    {
           |      "balanceAmount": {
           |        "currency": "EUR",
           |        "amount": "123"
           |      },
           |      "balanceType": "closingBooked",
           |      "creditLimitIncluded": false,
           |      "lastChangeDateTime": "2019-06-28T13:46:05.107Z",
           |      "referenceDate": "2019-06-28",
           |      "lastCommittedTransaction": "string"
           |    }
           |  ]
           |}
         """.stripMargin),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG),
       ApiTag("Account Information Service (AIS)")  :: apiTagMockedData :: apiTagBerlinGroupM :: Nil
     )

     lazy val getBalances : OBPEndpoint = {
       case "accounts" :: AccountId(accountId):: "balances" :: Nil JsonGet _ => {
         cc =>
           for {
            (Full(u), callContext) <- authorizedAccess(cc)
            _ <- passesPsd2Aisp(callContext)
            _ <- Helper.booleanToFuture(failMsg= DefaultBankIdNotSet ) { defaultBankId != "DEFAULT_BANK_ID_NOT_SET" }
            (_, callContext) <- NewStyle.function.getBank(BankId(defaultBankId), callContext)
            (bankAccount, callContext) <- NewStyle.function.checkBankAccountExists(BankId(defaultBankId), accountId, callContext)
            view <- NewStyle.function.view(ViewId("owner"), BankIdAccountId(bankAccount.bankId, bankAccount.accountId), callContext)
            _ <- Helper.booleanToFuture(failMsg = s"${UserNoPermissionAccessView} Current VIEW_ID (${view.viewId.value})") {(u.hasViewAccess(view))}
            (transactionRequests, callContext) <- Future { Connector.connector.vend.getTransactionRequests210(u, bankAccount)} map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(InvalidConnectorResponseForGetTransactionRequests210, 400, callContext.map(_.toLight)))
            } map { unboxFull(_) }
          } yield {
            (JSONFactory_BERLIN_GROUP_1_3.createAccountBalanceJSON(bankAccount, transactionRequests), HttpCode.`200`(callContext))
           }
         }
       }

     resourceDocs += ResourceDoc(
       getConsentAuthorisation,
       apiVersion,
       nameOf(getConsentAuthorisation),
       "GET",
       "/consents/CONSENTID/authorisations",
       "Get Consent Authorisation Sub-Resources Request",
       s"""
          |${mockedDataText(true)}
          |Return a list of all authorisation subresources IDs which have been created. This function returns an
          |array of hyperlinks to all generated authorisation sub-resources.
        """.stripMargin,
       json.parse(""""""),
       json.parse(
         """
           |{
           |  "authorisationIds": [
           |    "123auth456"
           |  ]
           |}
         """.stripMargin),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG),
       ApiTag("Account Information Service (AIS)")  :: apiTagMockedData :: apiTagBerlinGroupM :: Nil
     )

     lazy val getConsentAuthorisation : OBPEndpoint = {
       case "consents" :: consentId:: "authorisations" :: Nil JsonGet _ => {
         cc =>
           for {
             (_, callContext) <- authorizedAccess(cc)
             _ <- passesPsd2Aisp(callContext)
             consent <- Future(Consents.consentProvider.vend.getConsentByConsentId(consentId)) map {
               unboxFullOrFail(_, callContext, ConsentNotFound)
             }
           } yield {
             (JSONFactory_BERLIN_GROUP_1_3.AuthorisationJsonV13(List(consent.secret)), HttpCode.`200`(callContext))
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getConsentInformation,
       apiVersion,
       nameOf(getConsentInformation),
       "GET",
       "/consents/CONSENTID",
       "Get Consent Request",
       s"""
          |${mockedDataText(true)}
          |Returns the content of an account information consent object. This is returning the data for the TPP especially
          |in cases, where the consent was directly managed between ASPSP and PSU e.g. in a re-direct SCA Approach.
        """.stripMargin,
       json.parse(""""""),
       json.parse(
         """
           |{
           |  "access": {
           |    "accounts": [
           |      {
           |        "iban": "FR7612345987650123456789014",
           |        "bban": "BARC12345612345678",
           |        "pan": "5409050000000000",
           |        "maskedPan": "123456xxxxxx1234",
           |        "msisdn": "+49 170 1234567",
           |        "currency": "EUR"
           |      }
           |    ],
           |    "balances": [
           |      {
           |        "iban": "FR7612345987650123456789014",
           |        "bban": "BARC12345612345678",
           |        "pan": "5409050000000000",
           |        "maskedPan": "123456xxxxxx1234",
           |        "msisdn": "+49 170 1234567",
           |        "currency": "EUR"
           |      }
           |    ],
           |    "transactions": [
           |      {
           |        "iban": "FR7612345987650123456789014",
           |        "bban": "BARC12345612345678",
           |        "pan": "5409050000000000",
           |        "maskedPan": "123456xxxxxx1234",
           |        "msisdn": "+49 170 1234567",
           |        "currency": "EUR"
           |      }
           |    ],
           |    "availableAccounts": "allAccounts",
           |    "allPsd2": "allAccounts"
           |  },
           |  "recurringIndicator": false,
           |  "validUntil": "2020-12-31",
           |  "frequencyPerDay": 4,
           |  "lastActionDate": "2018-07-01",
           |  "consentStatus": "received",
           |  "_links": {
           |    "account": {
           |      "href": "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
           |    },
           |    "card-account": {
           |      "href": "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
           |    },
           |    "additionalProp1": {
           |      "href": "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
           |    },
           |    "additionalProp2": {
           |      "href": "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
           |    },
           |    "additionalProp3": {
           |      "href": "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
           |    }
           |  }
           |}
         """.stripMargin),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG),
       ApiTag("Account Information Service (AIS)")  :: apiTagMockedData :: apiTagBerlinGroupM :: Nil
     )

     lazy val getConsentInformation : OBPEndpoint = {
       case "consents" :: consentId :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizedAccess(cc)
             _ <- passesPsd2Aisp(callContext)
             createdConsent <- Future(Consents.consentProvider.vend.getConsentByConsentId(consentId)) map {
                 i => connectorEmptyResponse(i, callContext)
               }
           } yield {
             (createGetConsentResponseJson(createdConsent), HttpCode.`201`(callContext))
           }
         }
       }



      def tweakStatusNames(status: String) = {
        val scaStatus = status
          .replace(ConsentStatus.INITIATED.toString, "started")
          .replace(ConsentStatus.ACCEPTED.toString, "finalised")
          .replace(ConsentStatus.REJECTED.toString, "failed")
        scaStatus
      }
            
     resourceDocs += ResourceDoc(
       getConsentScaStatus,
       apiVersion,
       nameOf(getConsentScaStatus),
       "GET",
       "/consents/CONSENTID/authorisations/AUTHORISATIONID",
       "Read the SCA status of the consent authorisation.",
       s"""${mockedDataText(true)}
            |This method returns the SCA status of a consent initiation's authorisation sub-resource.
        """.stripMargin,
       json.parse(""""""),
       json.parse("""{
                |"scaStatus" : "psuAuthenticated"
              }""".stripMargin),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG),
       ApiTag("Account Information Service (AIS)")  :: apiTagMockedData :: apiTagBerlinGroupM :: Nil
     )

     lazy val getConsentScaStatus : OBPEndpoint = {
       case "consents" :: consentId:: "authorisations" :: authorisationId :: Nil JsonGet _ => {
         cc =>
           for {
             (_, callContext) <- authorizedAccess(cc)
             _ <- passesPsd2Aisp(callContext)
             consent <- Future(Consents.consentProvider.vend.getConsentByConsentId(consentId)) map {
               unboxFullOrFail(_, callContext, ConsentNotFound)
             }
             _ <- Helper.booleanToFuture(failMsg = AuthorizationNotFound) {
               consent.secret == authorisationId
             }
           } yield {
             (JSONFactory_BERLIN_GROUP_1_3.ScaStatusJsonV13(tweakStatusNames(consent.status)), HttpCode.`200`(callContext))
           }
         }
       }
            
     resourceDocs += ResourceDoc(
       getConsentStatus,
       apiVersion,
       nameOf(getConsentStatus),
       "GET",
       "/consents/CONSENTID/status",
       "Consent status request",
       s"""${mockedDataText(false)}
            Read the status of an account information consent resource.""",
       json.parse(""""""),
       json.parse("""{
                      |"consentStatus": "received"
                     }""".stripMargin),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG),
       ApiTag("Account Information Service (AIS)")   :: apiTagBerlinGroupM :: Nil
     )

     lazy val getConsentStatus : OBPEndpoint = {
       case "consents" :: consentId:: "status" :: Nil JsonGet _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizedAccess(cc)
             _ <- passesPsd2Aisp(callContext)
             consent <- Future(Consents.consentProvider.vend.getConsentByConsentId(consentId)) map {
               unboxFullOrFail(_, callContext, ConsentNotFound)
             }
           } yield {
             (JSONFactory_BERLIN_GROUP_1_3.ConsentStatusJsonV13(consent.status), HttpCode.`200`(callContext))
           }
             
         }
       }
            
     resourceDocs += ResourceDoc(
       getTransactionList,
       apiVersion,
       nameOf(getTransactionList),
       "GET",
       "/accounts/ACCOUNT_ID/transactions",
       "Read transaction list of an account",
       s"""
          |${mockedDataText(true)}
          |Read transaction reports or transaction lists of a given account ddressed by "account-id",
          |depending on the steering parameter "bookingStatus" together with balances.
          |For a given account, additional parameters are e.g. the attributes "dateFrom" and "dateTo".
          |The ASPSP might add balance information, if transaction lists without balances are not supported.
          |
        """.stripMargin,
       json.parse(""""""),
       json.parse(
         """
           |{
           |  "account": {
           |    "iban": "FR7612345987650123456789014",
           |    "bban": "BARC12345612345678",
           |    "pan": "5409050000000000",
           |    "maskedPan": "123456xxxxxx1234",
           |    "msisdn": "+49 170 1234567",
           |    "currency": "EUR"
           |  },
           |  "transactions": {
           |    "booked": [
           |      {
           |        "transactionId": "string",
           |        "entryReference": "string",
           |        "endToEndId": "string",
           |        "mandateId": "string",
           |        "checkId": "string",
           |        "creditorId": "string",
           |        "bookingDate": "2019-06-28",
           |        "valueDate": "2019-06-28",
           |        "transactionAmount": {
           |          "currency": "EUR",
           |          "amount": "123"
           |        },
           |        "currencyExchange": [
           |          {
           |            "sourceCurrency": "EUR",
           |            "exchangeRate": "string",
           |            "unitCurrency": "string",
           |            "targetCurrency": "EUR",
           |            "quotationDate": "2019-06-28",
           |            "contractIdentification": "string"
           |          }
           |        ],
           |        "creditorName": "Creditor Name",
           |        "creditorAccount": {
           |          "iban": "FR7612345987650123456789014",
           |          "bban": "BARC12345612345678",
           |          "pan": "5409050000000000",
           |          "maskedPan": "123456xxxxxx1234",
           |          "msisdn": "+49 170 1234567",
           |          "currency": "EUR"
           |        },
           |        "ultimateCreditor": "Ultimate Creditor",
           |        "debtorName": "Debtor Name",
           |        "debtorAccount": {
           |          "iban": "FR7612345987650123456789014",
           |          "bban": "BARC12345612345678",
           |          "pan": "5409050000000000",
           |          "maskedPan": "123456xxxxxx1234",
           |          "msisdn": "+49 170 1234567",
           |          "currency": "EUR"
           |        },
           |        "ultimateDebtor": "Ultimate Debtor",
           |        "remittanceInformationUnstructured": "Ref Number Merchant",
           |        "remittanceInformationStructured": "string",
           |        "additionalInformation": "string",
           |        "purposeCode": "BKDF",
           |        "bankTransactionCode": "PMNT-RCDT-ESCT",
           |        "proprietaryBankTransactionCode": "string",
           |        "_links": {
           |          "transactionDetails": {
           |            "href": "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
           |          },
           |          "additionalProp1": {
           |            "href": "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
           |          },
           |          "additionalProp2": {
           |            "href": "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
           |          },
           |          "additionalProp3": {
           |            "href": "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
           |          }
           |        }
           |      }
           |    ],
           |    "pending": [
           |      {
           |        "transactionId": "string",
           |        "entryReference": "string",
           |        "endToEndId": "string",
           |        "mandateId": "string",
           |        "checkId": "string",
           |        "creditorId": "string",
           |        "bookingDate": "2019-06-28",
           |        "valueDate": "2019-06-28",
           |        "transactionAmount": {
           |          "currency": "EUR",
           |          "amount": "123"
           |        },
           |        "currencyExchange": [
           |          {
           |            "sourceCurrency": "EUR",
           |            "exchangeRate": "string",
           |            "unitCurrency": "string",
           |            "targetCurrency": "EUR",
           |            "quotationDate": "2019-06-28",
           |            "contractIdentification": "string"
           |          }
           |        ],
           |        "creditorName": "Creditor Name",
           |        "creditorAccount": {
           |          "iban": "FR7612345987650123456789014",
           |          "bban": "BARC12345612345678",
           |          "pan": "5409050000000000",
           |          "maskedPan": "123456xxxxxx1234",
           |          "msisdn": "+49 170 1234567",
           |          "currency": "EUR"
           |        },
           |        "ultimateCreditor": "Ultimate Creditor",
           |        "debtorName": "Debtor Name",
           |        "debtorAccount": {
           |          "iban": "FR7612345987650123456789014",
           |          "bban": "BARC12345612345678",
           |          "pan": "5409050000000000",
           |          "maskedPan": "123456xxxxxx1234",
           |          "msisdn": "+49 170 1234567",
           |          "currency": "EUR"
           |        },
           |        "ultimateDebtor": "Ultimate Debtor",
           |        "remittanceInformationUnstructured": "Ref Number Merchant",
           |        "remittanceInformationStructured": "string",
           |        "additionalInformation": "string",
           |        "purposeCode": "BKDF",
           |        "bankTransactionCode": "PMNT-RCDT-ESCT",
           |        "proprietaryBankTransactionCode": "string",
           |        "_links": {
           |          "transactionDetails": {
           |            "href": "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
           |          },
           |          "additionalProp1": {
           |            "href": "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
           |          },
           |          "additionalProp2": {
           |            "href": "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
           |          },
           |          "additionalProp3": {
           |            "href": "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
           |          }
           |        }
           |      }
           |    ],
           |    "_links": {
           |      "account": {
           |        "href": "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
           |      },
           |      "first": {
           |        "href": "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
           |      },
           |      "next": {
           |        "href": "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
           |      },
           |      "previous": {
           |        "href": "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
           |      },
           |      "last": {
           |        "href": "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
           |      },
           |      "additionalProp1": {
           |        "href": "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
           |      },
           |      "additionalProp2": {
           |        "href": "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
           |      },
           |      "additionalProp3": {
           |        "href": "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
           |      }
           |    }
           |  },
           |  "balances": [
           |    {
           |      "balanceAmount": {
           |        "currency": "EUR",
           |        "amount": "123"
           |      },
           |      "balanceType": "closingBooked",
           |      "creditLimitIncluded": false,
           |      "lastChangeDateTime": "2019-06-28T14:15:19.359Z",
           |      "referenceDate": "2019-06-28",
           |      "lastCommittedTransaction": "string"
           |    }
           |  ],
           |  "_links": {
           |    "download": {
           |      "href": "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
           |    },
           |    "additionalProp1": {
           |      "href": "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
           |    },
           |    "additionalProp2": {
           |      "href": "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
           |    },
           |    "additionalProp3": {
           |      "href": "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
           |    }
           |  }
           |}
         """.stripMargin),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG),
       ApiTag("Account Information Service (AIS)")  :: apiTagBerlinGroupM :: Nil
     )

     lazy val getTransactionList : OBPEndpoint = {
       case "accounts" :: AccountId(account_id):: "transactions" :: Nil JsonGet _ => {
         cc =>
           for {

            (Full(u), callContext) <- authorizedAccess(cc)
            _ <- passesPsd2Aisp(callContext)

            _ <- Helper.booleanToFuture(failMsg= DefaultBankIdNotSet ) {defaultBankId != "DEFAULT_BANK_ID_NOT_SET"}

            bankId = BankId(defaultBankId)

            (_, callContext) <- NewStyle.function.getBank(bankId, callContext)

            (bankAccount, callContext) <- NewStyle.function.checkBankAccountExists(bankId, account_id, callContext)

            view <- NewStyle.function.view(ViewId("owner"), BankIdAccountId(bankAccount.bankId, bankAccount.accountId), callContext) 

            params <- Future { createQueriesByHttpParams(callContext.get.requestHeaders)} map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(UnknownError, 400, callContext.map(_.toLight)))
            } map { unboxFull(_) }

            (transactionRequests, callContext) <- Future { Connector.connector.vend.getTransactionRequests210(u, bankAccount)} map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(InvalidConnectorResponseForGetTransactionRequests210, 400, callContext.map(_.toLight)))
            } map { unboxFull(_) }

            (transactions, callContext) <- Future { bankAccount.getModeratedTransactions(Full(u), view, callContext, params)} map {
              x => fullBoxOrException(x ~> APIFailureNewStyle(UnknownError, 400, callContext.map(_.toLight)))
            } map { unboxFull(_) }

            } yield {
              (JSONFactory_BERLIN_GROUP_1_3.createTransactionsJson(transactions, transactionRequests), callContext)
            }
         }
       }

     resourceDocs += ResourceDoc(
       startConsentAuthorisation,
       apiVersion,
       nameOf(startConsentAuthorisation),
       "POST",
       "/consents/CONSENTID/authorisations",
       "Start the authorisation process for a consent",
       s"""
          |${mockedDataText(true)}
          |Create an authorisation sub-resource and start the authorisation process of a consent.
          |The message might in addition transmit authentication and authorisation related data.
          |his method is iterated n times for a n times SCA authorisation in a corporate context,
          |each creating an own authorisation sub-endpoint for the corresponding PSU authorising the consent.
          |The ASPSP might make the usage of this access method unnecessary, since the related authorisation
          | resource will be automatically created by the ASPSP after the submission of the consent data with the
          | first POST consents call. The start authorisation process is a process which is needed for creating
          | a new authorisation or cancellation sub-resource.
          |
          |This applies in the following scenarios: * The ASPSP has indicated with an 'startAuthorisation' hyperlink
          |in the preceding Payment Initiation Response that an explicit start of the authorisation process is needed by the TPP.
          |The 'startAuthorisation' hyperlink can transport more information about data which needs to be uploaded by using
          |the extended forms.
          |* 'startAuthorisationWithPsuIdentfication',
          |* 'startAuthorisationWithPsuAuthentication'
          |* 'startAuthorisationWithEncryptedPsuAuthentication'
          |* 'startAuthorisationWithAuthentciationMethodSelection'
          |* The related payment initiation cannot yet be executed since a multilevel SCA is mandated.
          |* The ASPSP has indicated with an 'startAuthorisation' hyperlink in the preceding Payment Cancellation
          |Response that an explicit start of the authorisation process is needed by the TPP.
          |
          |The 'startAuthorisation' hyperlink can transport more information about data which needs to be uploaded by
          |using the extended forms as indicated above.
          |* The related payment cancellation request cannot be applied yet since a multilevel SCA is mandate for executing the cancellation.
          |* The signing basket needs to be authorised yet.
          |
        """.stripMargin,
       json.parse(""""""),
       json.parse("""{
                    |  "scaStatus": "psuAuthenticated",
                    |  "authorisationId": "123auth456",
                    |  "scaMethods": [
                    |    {
                    |      "authenticationType": "SMS_OTP",
                    |      "authenticationVersion": "string",
                    |      "authenticationMethodId": "myAuthenticationID",
                    |      "name": "SMS OTP on phone +49160 xxxxx 28",
                    |      "explanation": "Detailed information about the SCA method for the PSU."
                    |    }
                    |  ],
                    |  "chosenScaMethod": {
                    |    "authenticationType": "SMS_OTP",
                    |    "authenticationVersion": "string",
                    |    "authenticationMethodId": "myAuthenticationID",
                    |    "name": "SMS OTP on phone +49160 xxxxx 28",
                    |    "explanation": "Detailed information about the SCA method for the PSU."
                    |  },
                    |  "challengeData": {
                    |    "image": "string",
                    |    "data": [
                    |      "string"
                    |    ],
                    |    "imageLink": "string",
                    |    "otpMaxLength": 0,
                    |    "otpFormat": "characters",
                    |    "additionalInformation": "string"
                    |  },
                    |  "_links": {
                    |    "scaRedirect": {
                    |      "href": "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
                    |    },
                    |    "scaOAuth": {
                    |      "href": "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
                    |    },
                    |    "updatePsuIdentification": {
                    |      "href": "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
                    |    },
                    |    "startAuthorisationWithPsuAuthentication": {
                    |      "href": "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
                    |    },
                    |    "startAuthorisationWithEncryptedPsuAuthentication": {
                    |      "href": "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
                    |    },
                    |    "selectAuthenticationMethod": {
                    |      "href": "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
                    |    },
                    |    "authoriseTransaction": {
                    |      "href": "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
                    |    },
                    |    "scaStatus": {
                    |      "href": "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
                    |    },
                    |    "additionalProp1": {
                    |      "href": "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
                    |    },
                    |    "additionalProp2": {
                    |      "href": "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
                    |    },
                    |    "additionalProp3": {
                    |      "href": "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
                    |    }
                    |  },
                    |  "psuMessage": "string"
                    |}"""),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG),
       ApiTag("Account Information Service (AIS)")  :: apiTagMockedData :: apiTagBerlinGroupM :: Nil
     )

     lazy val startConsentAuthorisation : OBPEndpoint = {
       case "consents" :: consentId:: "authorisations" :: Nil JsonPost _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizedAccess(cc)
             _ <- passesPsd2Aisp(callContext)
             consent <- Future(Consents.consentProvider.vend.getConsentByConsentId(consentId)) map {
               unboxFullOrFail(_, callContext, ConsentNotFound)
              }
             } yield {
             (createStartConsentAuthorisationJson(consent), HttpCode.`201`(callContext))
           }
         }
       }


}



