/**
Open Bank Project - API
Copyright (C) 2011-2019, TESOBE GmbH.

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE GmbH.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)

  */
package com.openbankproject.commons.model

import java.util.Date

import com.openbankproject.commons.model.enums._
import com.openbankproject.commons.util.{JsonAble, ReflectUtils, ignore}
import net.liftweb.json
import net.liftweb.json.{Formats, JInt, JString}
import net.liftweb.json.JsonAST.{JObject, JValue}

import scala.collection.immutable.List
import scala.reflect.runtime.universe._
//import code.customeraddress.CustomerAddress
//import code.bankconnectors.InboundAccountCommon
//import code.branches.Branches.BranchT
//import code.context.UserAuthContext
//import code.meetings.Meeting
//import code.taxresidence.TaxResidence
//import code.productcollectionitem.ProductCollectionItem
//import code.productcollection.ProductCollection
//import code.atms.Atms.AtmT
//import code.productattribute.ProductAttribute.ProductAttribute
//import code.accountattribute.AccountAttribute.AccountAttribute
//import code.accountapplication.AccountApplication

abstract class Converter[T, D <% T: TypeTag]{
  //this method declared as common method to avoid conflict with Predf#$confirms
  implicit def toCommons(t: T): D = ReflectUtils.toSibling[T, D].apply(t)

  implicit val toCommonsList = ReflectUtils.toSiblings[T, D]

  implicit val toCommonsBox = ReflectUtils.toSiblingBox[T, D]

  implicit val toCommonsBoxList = ReflectUtils.toSiblingsBox[T, D]

  implicit val toCommonsOption = ReflectUtils.toSiblingOption[T, D]

  implicit val toCommonsOptionList = ReflectUtils.toSiblingsOption[T, D]
}

case class ProductAttributeCommons(
                                    bankId :BankId,
                                    productCode :ProductCode,
                                    productAttributeId :String,
                                    name :String,
                                    attributeType : ProductAttributeType.Value,
                                    value :String) extends ProductAttribute
object ProductAttributeCommons extends Converter[ProductAttribute, ProductAttributeCommons]


case class ProductCollectionCommons(
                                     collectionCode :String,
                                     productCode :String) extends ProductCollection

object ProductCollectionCommons extends Converter[ProductCollection, ProductCollectionCommons]


case class AccountAttributeCommons(
                                    bankId :BankId,
                                    accountId :AccountId,
                                    productCode :ProductCode,
                                    accountAttributeId :String,
                                    name :String,
                                    attributeType : AccountAttributeType.Value,
                                    value :String) extends AccountAttribute

object AccountAttributeCommons extends Converter[AccountAttribute, AccountAttributeCommons]


case class AccountApplicationCommons(
                                      accountApplicationId :String,
                                      productCode :ProductCode,
                                      userId :String,
                                      customerId :String,
                                      dateOfApplication :Date,
                                      status :String) extends AccountApplication

object AccountApplicationCommons extends Converter[AccountApplication, AccountApplicationCommons]


case class UserAuthContextCommons(
                                   userAuthContextId :String,
                                   userId :String,
                                   key :String,
                                   value :String) extends UserAuthContext

object UserAuthContextCommons extends Converter[UserAuthContext, UserAuthContextCommons]


case class BankAccountCommons(
                               accountId :AccountId,
                               accountType :String,
                               balance :BigDecimal,
                               currency :String,
                               name :String,
                               @ignore   
                               label :String,
                               @ignore  
                               iban :Option[String],
                               number :String,
                               bankId :BankId,
                               lastUpdate :Date,
                               branchId :String,
                               @ignore   
                               accountRoutingScheme :String,
                               @ignore   
                               accountRoutingAddress :String,
                               accountRoutings :List[AccountRouting],
                               @ignore   
                               accountRules :List[AccountRule],
                               @ignore   
                               accountHolder :String) extends BankAccount

object BankAccountCommons extends Converter[BankAccount, BankAccountCommons]

case class ProductCollectionItemCommons(
                                         collectionCode :String,
                                         memberProductCode :String) extends ProductCollectionItem

object ProductCollectionItemCommons extends Converter[ProductCollectionItem, ProductCollectionItemCommons]


case class CustomerCommons(
                            customerId :String,
                            bankId :String,
                            number :String,
                            legalName :String,
                            mobileNumber :String,
                            email :String,
                            faceImage :CustomerFaceImage,
                            dateOfBirth :Date,
                            relationshipStatus :String,
                            dependents :Integer,
                            dobOfDependents :List[Date],
                            highestEducationAttained :String,
                            employmentStatus :String,
                            creditRating :CreditRating,
                            creditLimit :CreditLimit,
                            kycStatus : java.lang.Boolean,
                            lastOkDate :Date,
                            title :String,
                            branchId :String,
                            nameSuffix :String) extends Customer

object CustomerCommons extends Converter[Customer, CustomerCommons]


case class CustomerAddressCommons(
                                   customerId :String,
                                   customerAddressId :String,
                                   line1 :String,
                                   line2 :String,
                                   line3 :String,
                                   city :String,
                                   county :String,
                                   state :String,
                                   postcode :String,
                                   countryCode :String,
                                   status :String,
                                   tags :String,
                                   insertDate :Date) extends CustomerAddress

object CustomerAddressCommons extends Converter[CustomerAddress, CustomerAddressCommons]

//This class is only used for connector.getBankAccountsForUser method. Not the used for accounts relevant endpoints.
//It will get the bankId, accountId and viewsToGenerate to create the OBP side data, such as views, accountHolder.
case class InboundAccountCommons(
                                        bankId :String,
                                        @ignore
                                        branchId :String,
                                        accountId :String,
                                        @ignore
                                        accountNumber :String,
                                        @ignore
                                        accountType :String,
                                        @ignore
                                        balanceAmount :String,
                                        @ignore
                                        balanceCurrency :String,
                                        @ignore
                                        owners :List[String],
                                        viewsToGenerate :List[String],
                                        @ignore
                                        bankRoutingScheme :String,
                                        @ignore
                                        bankRoutingAddress :String,
                                        @ignore
                                        branchRoutingScheme :String,
                                        @ignore
                                        branchRoutingAddress :String,
                                        @ignore
                                        accountRoutingScheme :String,
                                        @ignore
                                        accountRoutingAddress :String) extends InboundAccount

object InboundAccountCommons extends Converter[InboundAccount, InboundAccountCommons]


case class AtmTCommons(
                        atmId :AtmId,
                        bankId :BankId,
                        name :String,
                        address :Address,
                        location :Location,
                        meta :Meta,
                        OpeningTimeOnMonday : Option[String],
                        ClosingTimeOnMonday : Option[String],

                        OpeningTimeOnTuesday : Option[String],
                        ClosingTimeOnTuesday : Option[String],

                        OpeningTimeOnWednesday : Option[String],
                        ClosingTimeOnWednesday : Option[String],

                        OpeningTimeOnThursday : Option[String],
                        ClosingTimeOnThursday: Option[String],

                        OpeningTimeOnFriday : Option[String],
                        ClosingTimeOnFriday : Option[String],

                        OpeningTimeOnSaturday : Option[String],
                        ClosingTimeOnSaturday : Option[String],

                        OpeningTimeOnSunday: Option[String],
                        ClosingTimeOnSunday : Option[String],

                        isAccessible : Option[Boolean],

                        locatedAt : Option[String],
                        moreInfo : Option[String],
                        hasDepositCapability : Option[Boolean]) extends AtmT

object AtmTCommons extends Converter[AtmT, AtmTCommons]


case class BankCommons(
                        bankId :BankId,
                        shortName :String,
                        fullName :String,
                        logoUrl :String,
                        websiteUrl :String,
                        bankRoutingScheme :String,
                        bankRoutingAddress :String,
                        @ignore
                        swiftBic :String,
                        @ignore
                        nationalIdentifier :String) extends Bank {
  def this(bankId :BankId,
    shortName :String,
    fullName :String,
    logoUrl :String,
    websiteUrl :String,
    bankRoutingScheme :String,
    bankRoutingAddress :String) = this(bankId, shortName, fullName, logoUrl, websiteUrl, bankRoutingScheme, bankRoutingAddress, null, null)
}


object BankCommons extends Converter[Bank, BankCommons]

case class CounterpartyTraitCommons(
                                     createdByUserId :String,
                                     name :String,
                                     description :String,
                                     thisBankId :String,
                                     thisAccountId :String,
                                     thisViewId :String,
                                     counterpartyId :String,
                                     otherAccountRoutingScheme :String,
                                     otherAccountRoutingAddress :String,
                                     otherAccountSecondaryRoutingScheme :String,
                                     otherAccountSecondaryRoutingAddress :String,
                                     otherBankRoutingScheme :String,
                                     otherBankRoutingAddress :String,
                                     otherBranchRoutingScheme :String,
                                     otherBranchRoutingAddress :String,
                                     isBeneficiary :Boolean,
                                     bespoke :List[CounterpartyBespoke]) extends CounterpartyTrait

object CounterpartyTraitCommons extends Converter[CounterpartyTrait, CounterpartyTraitCommons]


case class TaxResidenceCommons(
                                customerId :String,
                                taxResidenceId :String,
                                domain :String,
                                taxNumber :String) extends TaxResidence

object TaxResidenceCommons extends Converter[TaxResidence, TaxResidenceCommons]


case class BranchTCommons(
                           branchId: BranchId,
                           bankId: BankId,
                           name: String,
                           address: Address,
                           location: Location,
                           lobbyString: Option[LobbyString],
                           driveUpString: Option[DriveUpString],
                           meta: Meta,
                           branchRouting: Option[Routing],
                           lobby: Option[Lobby],
                           driveUp: Option[DriveUp],
                           isAccessible : Option[Boolean],
                           accessibleFeatures: Option[String],
                           branchType : Option[String],
                           moreInfo : Option[String],
                           phoneNumber : Option[String],
                           isDeleted : Option[Boolean]) extends BranchT

object BranchTCommons extends Converter[BranchT, BranchTCommons]


case class MeetingCommons(
                           meetingId :String,
                           providerId :String,
                           purposeId :String,
                           bankId :String,
                           present :MeetingPresent,
                           keys :MeetingKeys,
                           when :Date,
                           creator :ContactDetails,
                           invitees :List[Invitee]) extends Meeting

object MeetingCommons extends Converter[Meeting, MeetingCommons]

case class ProductCommons(bankId: BankId,
                       code : ProductCode,
                       parentProductCode : ProductCode,
                       name : String,
                       category: String,
                       family : String,
                       superFamily : String,
                       moreInfoUrl: String,
                       details: String,
                       description: String,
                       meta: Meta) extends Product

object ProductCommons extends Converter[Product, ProductCommons]

case class TransactionRequestCommonBodyJSONCommons(
                        value : AmountOfMoneyJsonV121,
                        description: String) extends TransactionRequestCommonBodyJSON

object TransactionRequestCommonBodyJSONCommons extends Converter[TransactionRequestCommonBodyJSON, TransactionRequestCommonBodyJSONCommons]

case class TransactionRequestStatusCommons(
                                            transactionRequestId: String,
                                            bulkTransactionsStatus: List[TransactionStatus]
                                          ) extends TransactionRequestStatus

object TransactionRequestStatusCommons extends Converter[TransactionRequestStatus, TransactionRequestStatusCommons]



case class KycCheckCommons(
                            override val bankId: String,
                            override val customerId: String,
                            override val idKycCheck : String,
                            override val customerNumber : String,
                            override val date : Date,
                            override val how : String,
                            override val staffUserId : String,
                            override val staffName : String,
                            override val satisfied: Boolean,
                            override val comments : String
                          ) extends KycCheck

object KycCheckCommons extends Converter[KycCheck, KycCheckCommons]

case class KycDocumentCommons(
  override val bankId: String,
  override val customerId: String,
  override val idKycDocument : String,
  override val customerNumber : String,
  override val `type` : String,
  override val number : String,
  override val issueDate : Date,
  override val issuePlace : String,
  override val expiryDate : Date
) extends KycDocument

object KycDocumentCommons extends Converter[KycDocument, KycDocumentCommons]

case class KycMediaCommons (
  override val bankId: String,
  override val customerId: String,
  override val idKycMedia : String,
  override val customerNumber : String,
  override val `type` : String,
  override val url : String,
  override val date : Date,
  override val relatesToKycDocumentId : String,
  override val relatesToKycCheckId : String
) extends KycMedia

object KycMediaCommons extends Converter[KycMedia, KycMediaCommons]

case class KycStatusCommons (
  override val bankId: String,
  override val customerId: String,
  override val customerNumber : String,
  override val ok : Boolean,
  override val date : Date
) extends KycStatus

object KycStatusCommons extends Converter[KycStatus, KycStatusCommons]

case class CustomerMessageCommons(
  override val messageId: String,
  override val date: Date,
  override val message: String,
  override val fromDepartment: String,
  override val fromPerson: String
) extends CustomerMessage
object CustomerMessageCommons extends Converter[CustomerMessage, CustomerMessageCommons]

case class CustomerAttributeCommons (
  override val bankId: BankId,
  override val customerId: CustomerId,
  override val customerAttributeId: String,
  override val attributeType: CustomerAttributeType.Value,
  override val name: String,
  override val value: String,
) extends CustomerAttribute
object CustomerAttributeCommons extends Converter[CustomerAttribute, CustomerAttributeCommons]

case class TransactionAttributeCommons (
    override val  bankId: BankId,
    override val  transactionId: TransactionId,
    override val  transactionAttributeId: String,
    override val  attributeType: TransactionAttributeType.Value,
    override val  name: String,
    override val  value: String,
) extends TransactionAttribute
object TransactionAttributeCommons extends Converter[TransactionAttribute, TransactionAttributeCommons]

case class FXRateCommons (
  override val bankId : BankId,
  override val fromCurrencyCode: String,
  override val toCurrencyCode: String,
  override val conversionValue: Double,
  override val inverseConversionValue: Double,
  override val effectiveDate: Date
) extends FXRate
object FXRateCommons extends Converter[FXRate, FXRateCommons]


case class TransactionRequestTypeChargeCommons (
   override val transactionRequestTypeId: String,
   override val bankId: String,
   override val chargeCurrency: String,
   override val chargeAmount: String,
   override val chargeSummary: String
) extends TransactionRequestTypeCharge
object TransactionRequestTypeChargeCommons extends Converter[TransactionRequestTypeCharge, TransactionRequestTypeChargeCommons]

case class DirectDebitTraitCommons (
    override val directDebitId: String,
    override val bankId: String,
    override val accountId: String,
    override val customerId: String,
    override val userId: String,
    override val counterpartyId: String,
    override val dateSigned: Date,
    override val dateCancelled: Date,
    override val dateStarts: Date,
    override val dateExpires: Date,
    override val active: Boolean
) extends DirectDebitTrait
object DirectDebitTraitCommons extends Converter[DirectDebitTrait, DirectDebitTraitCommons]

case class TransactionStatusCommons(
   override val transactionId : String,
   override val transactionStatus: String,
   override val transactionTimestamp: String
) extends TransactionStatus
object TransactionStatusCommons extends Converter[TransactionStatus, TransactionStatusCommons]

//----------------obp-api moved to here case classes

case class BranchRoutingJsonV141(
                                  scheme: String,
                                  address: String
                                )

case class AccountRoutingJsonV121(
                                   scheme: String,
                                   address: String
                                 )

case class AccountV310Json(
                            bank_id: String ,
                            account_id: String ,
                            account_type : String,
                            account_routings: List[AccountRoutingJsonV121],
                            branch_routings: List[BranchRoutingJsonV141]
                          )

case class CheckbookOrdersJson(
                                account: AccountV310Json ,
                                orders: List[OrderJson]
                              )

case class OrderJson(order: OrderObjectJson)

case class OrderObjectJson(
                            order_id: String,
                            order_date: String,
                            number_of_checkbooks: String,
                            distribution_channel: String,
                            status: String,
                            first_check_number: String,
                            shipping_code: String
                          )

case class ObpApiLoopback(
                          connectorVersion: String,
                          gitCommit: String,
                          durationTime: String
                         ) extends TopicTrait

case class CardObjectJson(
                           card_type: String,
                           card_description: String,
                           use_type: String
                         )

case class TransactionRequestAccount (
                                       val bank_id: String,
                                       val account_id : String
                                     )

//For SEPA, it need the iban to find the toCounterpaty--> toBankAccount
case class TransactionRequestIban (iban : String)

case class AmountOfMoneyJsonV121(
                                  currency : String,
                                  amount : String
                                )

case class ToAccountTransferToAccountAccount(
                                              number: String,
                                              iban: String
                                            )

case class FromAccountTransfer(
                                mobile_phone_number: String,
                                nickname: String
                              )

case class ToAccountTransferToAtmKycDocument(
                                              `type`: String,
                                              number: String
                                            )

case class ToAccountTransferToAccount(
                                       name: String,
                                       bank_code: String,
                                       branch_number: String,
                                       account: ToAccountTransferToAccountAccount
                                     )

case class ToAccountTransferToPhone(
                                     mobile_phone_number: String
                                   )

case class TransactionRequestTransferToPhone(
                                              value: AmountOfMoneyJsonV121,
                                              description: String,
                                              message: String,
                                              from: FromAccountTransfer,
                                              to: ToAccountTransferToPhone
                                            ) extends TransactionRequestCommonBodyJSON

case class ToAccountTransferToAtm(
                                   legal_name: String,
                                   date_of_birth: String,
                                   mobile_phone_number: String,
                                   kyc_document: ToAccountTransferToAtmKycDocument
                                 )

case class TransactionRequestTransferToAtm(
                                            value: AmountOfMoneyJsonV121,
                                            description: String,
                                            message: String,
                                            from: FromAccountTransfer,
                                            to: ToAccountTransferToAtm
                                          ) extends TransactionRequestCommonBodyJSON

//For COUNTERPATY, it need the counterparty_id to find the toCounterpaty--> toBankAccount
case class TransactionRequestCounterpartyId (counterparty_id : String)

case class TransactionRequestTransferToAccount(
                                                value: AmountOfMoneyJsonV121,
                                                description: String,
                                                transfer_type: String,
                                                future_date: String,
                                                to: ToAccountTransferToAccount
                                              ) extends TransactionRequestCommonBodyJSON
case class PaymentAccount( //This is from berlinGroup
  iban: String
)

case class SepaCreditTransfers( //This is from berlinGroup
  debtorAccount: PaymentAccount,
  instructedAmount: AmountOfMoneyJsonV121,
  creditorAccount: PaymentAccount,
  creditorName: String
)

case class TransactionRequestBodyAllTypes (
                                            @ignore
                                            to_sandbox_tan: Option[TransactionRequestAccount],
                                            @ignore   
                                            to_sepa: Option[TransactionRequestIban],
                                            @ignore  
                                            to_counterparty: Option[TransactionRequestCounterpartyId],
                                            @ignore  
                                            to_transfer_to_phone: Option[TransactionRequestTransferToPhone] = None, //TODO not stable
                                            @ignore  
                                            to_transfer_to_atm: Option[TransactionRequestTransferToAtm]= None,//TODO not stable
                                            @ignore  
                                            to_transfer_to_account: Option[TransactionRequestTransferToAccount]= None,//TODO not stable
                                            @ignore  
                                            to_sepa_credit_transfers: Option[SepaCreditTransfers]= None,//TODO not stable, from berlin Group
  
                                            value: AmountOfMoney,
                                            description: String
                                          )

case class TransactionRequestCharge(
                                     val summary: String,
                                     val value : AmountOfMoney
                                   )

case class TransactionRequestChallenge (
                                         val id: String,
                                         val allowed_attempts : Int,
                                         val challenge_type: String
                                       )
case class TransactionRequest (
                                val id: TransactionRequestId,
                                val `type` : String,
                                val from: TransactionRequestAccount,
                                val body: TransactionRequestBodyAllTypes,
                                val transaction_ids: String,
                                val status: String,
                                val start_date: Date,
                                val end_date: Date,
                                val challenge: TransactionRequestChallenge,
                                val charge: TransactionRequestCharge,
                                @ignore  
                                val charge_policy: String,
                                @ignore  
                                val counterparty_id :CounterpartyId,
                                @ignore  
                                val name :String,
                                @ignore  
                                val this_bank_id : BankId,
                                @ignore  
                                val this_account_id : AccountId,
                                @ignore  
                                val this_view_id :ViewId,
                                @ignore  
                                val other_account_routing_scheme : String,
                                @ignore  
                                val other_account_routing_address : String,
                                @ignore  
                                val other_bank_routing_scheme : String,
                                @ignore  
                                val other_bank_routing_address : String,
                                @ignore  
                                val is_beneficiary :Boolean,
                                @ignore  
                                val future_date :Option[String] = None
                              )
case class TransactionRequestBody (
                                    val to: TransactionRequestAccount,
                                    val value : AmountOfMoney,
                                    val description : String
                                  )

case class Transaction(
                   //A universally unique id
                   @ignore
                   val uuid: String,
                   //id is unique for transactions of @thisAccount
                   val id : TransactionId,
                   val thisAccount : BankAccountCommons,
                   val otherAccount : Counterparty,
                   //E.g. cash withdrawal, electronic payment, etc.
                   val transactionType : String,
                   val amount : BigDecimal,
                   //ISO 4217, e.g. EUR, GBP, USD, etc.
                   val currency : String,
                   // Bank provided label
                   val description : Option[String],
                   // The date the transaction was initiated
                   val startDate : Date,
                   // The date when the money finished changing hands
                   val finishDate : Date,
                   //the new balance for the bank account
                   val balance :  BigDecimal
                 ) {

  val bankId = thisAccount.bankId
  val accountId = thisAccount.accountId
}

case class UserCommons(userPrimaryKey : UserPrimaryKey, userId: String,idGivenByProvider: String, provider : String, emailAddress : String, name : String) extends User

case class InternalBasicUser(
  userId:String,
  emailAddress: String,
  name: String
)


case class BasicUserAuthContext(
  key: String,
  value: String
)

case class ViewBasic(
  id: String,
  name: String,
  description: String,
)
case class BasicLinkedCustomer(
  customerId: String,
  customerNumber: String,
  legalName: String,
)
case class InternalBasicCustomer(
  bankId:String,
  customerId: String,
  customerNumber: String,
  legalName: String,
  dateOfBirth: Date
)
case class InternalBasicCustomers(customers: List[InternalBasicCustomer])

case class InternalBasicUsers(users: List[InternalBasicUser])

case class AccountBasic(
  id: String,
  accountRoutings: List[AccountRouting],
  customerOwners: List[InternalBasicCustomer],
  userOwners: List[InternalBasicUser]
)

case class AuthView(
  view: ViewBasic,
  account:AccountBasic,
)

case class OutboundAdapterCallContext(
  correlationId: String = "",
  sessionId: Option[String] = None, //Only this value must be used for cache key !!!
  consumerId: Option[String] = None,
  generalContext: Option[List[BasicGeneralContext]]= None,
  outboundAdapterAuthInfo: Option[OutboundAdapterAuthInfo] = None,
)

case class BasicGeneralContext(
  key: String,
  value: String
)

case class OutboundAdapterAuthInfo(
  userId: Option[String]= None, 
  username: Option[String]= None, 
  linkedCustomers: Option[List[BasicLinkedCustomer]] = None,
  userAuthContext: Option[List[BasicUserAuthContext]]= None,//be set by obp from some endpoints. 
  authViews: Option[List[AuthView]] = None,
)

case class InboundAdapterCallContext(
  correlationId: String = "",
  sessionId: Option[String] = None,
  generalContext: Option[List[BasicGeneralContext]]= None,  //be set by backend, send it back to the header? not finish yet.
)


//Note: this is used for connector method: 'def getUser(name: String, password: String): Box[InboundUser]'
case class InboundUser(
                        email: String,
                        password: String,
                        displayName: String
                      )

case class InboundExternalUser(
                                aud: String,
                                exp: String,
                                iat: String,
                                iss: String,
                                sub: String,
                                azp: Option[String],
                                email: Option[String],
                                emailVerified: Option[String],
                                name: Option[String]
                              )


case class ErrorMessage(code: Int, message: String)

object ErrorMessage {

  def isErrorMessage(jValue: JValue) = jValue match {
    case jObj @JObject(fields) =>
      fields.size == 2 &&
        (jObj \ "code").isInstanceOf[JInt] &&
        (jObj \ "message").isInstanceOf[JString]
    case _ => false
  }
}
