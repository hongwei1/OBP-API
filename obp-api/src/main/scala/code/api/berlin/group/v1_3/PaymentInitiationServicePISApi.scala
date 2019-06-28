package code.api.berlin.group.v1_3

import code.api.berlin.group.v1_3.JSONFactory_BERLIN_GROUP_1_3.SepaCreditTransfersJson
import code.api.util.APIUtil._
import code.api.util.ApiTag._
import code.api.util.ErrorMessages._
import code.api.util.NewStyle.HttpCode
import code.api.util.{ApiRole, ApiTag, NewStyle}
import code.model._
import code.transactionrequests.TransactionRequests.TransactionRequestTypes
import code.util.Helper
import com.github.dwickern.macros.NameOf.nameOf
import com.openbankproject.commons.model._
import net.liftweb.common.Full
import net.liftweb.http.rest.RestHelper
import net.liftweb.json
import net.liftweb.json._

import scala.collection.immutable.Nil
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext.Implicits.global

object APIMethods_PaymentInitiationServicePISApi extends RestHelper {
    val apiVersion =  OBP_BERLIN_GROUP_1_3.apiVersion
    val resourceDocs = ArrayBuffer[ResourceDoc]()
    val apiRelations = ArrayBuffer[ApiRelation]()
    protected implicit def JvalueToSuper(what: JValue): JvalueCaseClass = JvalueCaseClass(what)

    val endpoints =
      initiatePayment ::
      Nil


  val additionalInstructions : String =
    """
      |Additional Instructions:
      |
      |for PAYMENT_SERVICE use payments
      |
      |for PAYMENT_PRODUCT use sepa-credit-transfers
      |
    """.stripMargin


  resourceDocs += ResourceDoc(
    initiatePayment,
    apiVersion,
    nameOf(initiatePayment),
    "POST",
    "/PAYMENT_SERVICE/PAYMENT_PRODUCT",
    "Payment initiation request",
    s"""${mockedDataText(true)}
            |This method is used to initiate a payment at the ASPSP.
            |## Variants of Payment Initiation Requests
            |This method to initiate a payment initiation at the ASPSP can be sent with either a JSON body or an pain.001
            | body depending on the payment product in the path. There are the following
            | **payment products**:
            |
            |- Payment products with payment information in *JSON* format:
            |  - ***sepa-credit-transfers***
            |  - ***instant-sepa-credit-transfers***
            |  - ***target-2-payments***
            |  - ***cross-border-credit-transfers***
            |- Payment products with payment information in *pain.001* XML format:
            |  - ***pain.001-sepa-credit-transfers***
            |  - ***pain.001-instant-sepa-credit-transfers***
            |  - ***pain.001-target-2-payments***
            |  - ***pain.001-cross-border-credit-transfers***
            |
            |Furthermore the request body depends on the **payment-service**
            |
            |* ***payments***: A single payment initiation request.
            |* ***bulk-payments***: A collection of several payment iniatiation requests.
            |
            | In case of a *pain.001* message there are more than one payments contained in the *pain.001 message.
            |  In case of a *JSON* there are several JSON payment blocks contained in a joining list.
            |
            |* ***periodic-payments***: Create a standing order initiation resource for recurrent i.e. periodic payments addressable under {paymentId} with all data relevant for the corresponding payment product and the execution of the standing order contained in a JSON body.
            |
            |This is the first step in the API to initiate the related recurring/periodic payment.
            |
            |## Single and mulitilevel SCA Processes
            |
            |The Payment Initiation Requests are independent from the need of one ore multilevel SCA processing, i.e. independent from the number of authorisations needed for the execution of payments.
            |
            |But the response messages are specific to either one SCA processing or multilevel SCA processing.
            |
            |For payment initiation with multilevel SCA, this specification requires an explicit start of the authorisation, i.e. links directly associated with SCA processing like 'scaRedirect' or 'scaOAuth' cannot be contained in the response message of a Payment Initation Request for a payment, where multiple authorisations are needed. Also if any data is needed for the next action, like selecting an SCA method is not supported in the response, since all starts of the multiple authorisations are fully equal. In these cases, first an authorisation sub-resource has to be generated following the 'startAuthorisation' link.

            """.stripMargin,
    json.parse(""""""),
    json.parse(
      """
        |{
        |  "transactionStatus": "ACCP",
        |  "paymentId": "1234-wertiq-983",
        |  "transactionFees": {
        |    "currency": "EUR",
        |    "amount": "123"
        |  },
        |  "transactionFeeIndicator": true,
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
        |      "href": "https://www.testbank.com/asdfasdfasdf"
        |    },
        |    "self": {
        |      "href": "/v1/payments/sepa-credit-transfers/1234-wertiq-983"
        |    }
        |  },
        |  "psuMessage": "string",
        |  "tppMessages": [
        |    {
        |      "category": "ERROR",
        |      "code": "WARNING",
        |      "path": "string",
        |      "text": "string"
        |    }
        |  ]
        |}
      """.stripMargin),
       List(UserNotLoggedIn, UnknownError),
       Catalogs(notCore, notPSD2, notOBWG),
       ApiTag("Payment Initiation Service (PIS)") :: apiTagMockedData :: apiTagBerlinGroupM :: Nil
     )

     lazy val initiatePayment : OBPEndpoint = {
       case paymentService :: paymentProduct :: Nil JsonPost json -> _ => {
         cc =>
           for {
             (Full(u), callContext) <- authorizedAccess(cc)
             
             transDetailsJson <- NewStyle.function.tryons(s"$InvalidJsonFormat The Json body should be the $SepaCreditTransfersJson ", 400, callContext) {
               json.extract[SepaCreditTransfersJson]
             }
             isValidAmountNumber <- NewStyle.function.tryons(s"$InvalidNumber Current input is  ${transDetailsJson.instructedAmount.amount} ", 400, callContext) {
               BigDecimal(transDetailsJson.instructedAmount.amount)
             }

             paymentProductType <- NewStyle.function.tryons(s"${InvalidTransactionRequestType.replaceAll("TRANSACTION_REQUEST_TYPE","payment-product")}: '${paymentProduct}'. Only Support `sepa_credit_transfers` now.",400, callContext) {
               TransactionRequestTypes.withName(paymentProduct)
             }

             _ <- Helper.booleanToFuture(s"${NotPositiveAmount} Current input is: '${isValidAmountNumber}'") {
               isValidAmountNumber > BigDecimal("0")
             }

             _ <- Helper.booleanToFuture(s"${InvalidISOCurrencyCode} Current input is: '${transDetailsJson.instructedAmount.currency}'") {
               isValidCurrencyISOCode(transDetailsJson.instructedAmount.currency)
             }

             // Prevent default value for transaction request type (at least).
             _ <- Helper.booleanToFuture(s"${InvalidISOCurrencyCode} Current input is: '${transDetailsJson.instructedAmount.currency}'") {
               isValidCurrencyISOCode(transDetailsJson.instructedAmount.currency)
             }

             _ <- NewStyle.function.isEnabledTransactionRequests()
             fromAccountIban = transDetailsJson.debtorAccount.iban
             toAccountIban = transDetailsJson.creditorAccount.iban
             
             (fromAccount, callContext) <- NewStyle.function.getBankAccountByIban(fromAccountIban, callContext)
             (toAccount, callContext) <- NewStyle.function.getBankAccountByIban(toAccountIban, callContext)

             _ <- Helper.booleanToFuture(InsufficientAuthorisationToCreateTransactionRequest) {
               
               u.hasOwnerViewAccess(BankIdAccountId(fromAccount.bankId,fromAccount.accountId)) == true ||
                 hasEntitlement(fromAccount.bankId.value, u.userId, ApiRole.canCreateAnyTransactionRequest) == true
             }
            
             // Prevent default value for transaction request type (at least).
             _ <- Helper.booleanToFuture(s"From Account Currency is ${fromAccount.currency}, but Requested Transaction Currency is: ${transDetailsJson.instructedAmount.currency}") {
               transDetailsJson.instructedAmount.currency == fromAccount.currency
             }

             amountOfMoneyJSON = transDetailsJson.instructedAmount

             (createdTransactionRequest,callContext) <- paymentProductType match {
               case TransactionRequestTypes.sepa_credit_transfers => {
                 for {
                   (createdTransactionRequest, callContext) <- NewStyle.function.createTransactionRequestv210(
                     u,
                     ViewId("Owner"),//This is the default 
                     fromAccount,
                     toAccount,
                     TransactionRequestType(paymentProductType.toString),
                     TransactionRequestCommonBodyJSONCommons(
                       amountOfMoneyJSON,
                      ""
                     ),
                     "",
                     "",
                     callContext) //in SANDBOX_TAN, ChargePolicy set default "SHARED"
                 } yield (createdTransactionRequest, callContext)
               }
             }
           } yield {
             (JSONFactory_BERLIN_GROUP_1_3.createTransactionRequestJson(createdTransactionRequest), HttpCode.`201`(callContext))
           }
       }
     }

}



