/**
Open Bank Project - API
Copyright (C) 2011-2018, TESOBE Ltd.

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
TESOBE Ltd.
Osloer Strasse 16/17
Berlin 13359, Germany

This product includes software developed at
TESOBE (http://www.tesobe.com/)
*/

package com.openbankproject.commons.dto.rest

import java.util.Date

import com.openbankproject.commons.model._

import scala.collection.immutable.List


//--------generated


case class OutBoundGetObpApiLoopback (adapterCallContext: AdapterCallContext)
case class InBoundGetObpApiLoopback (adapterCallContext: AdapterCallContext, data: ObpApiLoopback)


case class OutBoundGetAdapterInfoFuture (adapterCallContext: AdapterCallContext)
case class InBoundGetAdapterInfoFuture (adapterCallContext: AdapterCallContext, data: InboundAdapterInfoInternal)


case class OutBoundGetChallengeThreshold (adapterCallContext: AdapterCallContext,
                                          bankId: String,
                                          accountId: String,
                                          viewId: String,
                                          transactionRequestType: String,
                                          currency: String,
                                          userId: String,
                                          userName: String)
case class InBoundGetChallengeThreshold (adapterCallContext: AdapterCallContext, data: AmountOfMoney)


case class OutBoundGetChargeLevel (adapterCallContext: AdapterCallContext,
                                   bankId: BankId,
                                   accountId: AccountId,
                                   viewId: ViewId,
                                   userId: String,
                                   userName: String,
                                   transactionRequestType: String,
                                   currency: String)
case class InBoundGetChargeLevel (adapterCallContext: AdapterCallContext, data: AmountOfMoney)


case class OutBoundGetBankFuture (adapterCallContext: AdapterCallContext,
                                  bankId: BankId)
case class InBoundGetBankFuture (adapterCallContext: AdapterCallContext, data: BankCommons)


case class OutBoundGetBanksFuture (adapterCallContext: AdapterCallContext)
case class InBoundGetBanksFuture (adapterCallContext: AdapterCallContext, data: List[BankCommons])

case class OutBoundGetBankAccountsByUsernameFuture (adapterCallContext: AdapterCallContext,
                                          username: String)
case class InBoundGetBankAccountsByUsernameFuture (adapterCallContext: AdapterCallContext, data: List[InboundAccountCommonCommons])


case class OutBoundGetBankAccountFuture (adapterCallContext: AdapterCallContext,
                                         bankId: BankId,
                                         accountId: AccountId)
case class InBoundGetBankAccountFuture (adapterCallContext: AdapterCallContext, data: BankAccountCommons)


case class OutBoundGetBankAccountsFuture (adapterCallContext: AdapterCallContext,
                                          bankIdAccountIds: List[BankIdAccountId])
case class InBoundGetBankAccountsFuture (adapterCallContext: AdapterCallContext, data: List[BankAccountCommons])


case class OutBoundGetCoreBankAccountsFuture (adapterCallContext: AdapterCallContext,
                                              bankIdAccountIds: List[BankIdAccountId])
case class InBoundGetCoreBankAccountsFuture (adapterCallContext: AdapterCallContext, data: List[CoreAccount])


case class OutBoundGetCoreBankAccountsHeldFuture (adapterCallContext: AdapterCallContext,
                                                  bankIdAccountIds: List[BankIdAccountId])
case class InBoundGetCoreBankAccountsHeldFuture (adapterCallContext: AdapterCallContext, data: List[AccountHeld])


case class OutBoundCheckBankAccountExistsFuture (adapterCallContext: AdapterCallContext,
                                                 bankId: BankId,
                                                 accountId: AccountId)
case class InBoundCheckBankAccountExistsFuture (adapterCallContext: AdapterCallContext, data: BankAccountCommons)


case class OutBoundGetCounterpartyTrait (adapterCallContext: AdapterCallContext,
                                         bankId: BankId,
                                         accountId: AccountId,
                                         couterpartyId: String)
case class InBoundGetCounterpartyTrait (adapterCallContext: AdapterCallContext, data: CounterpartyTraitCommons)


case class OutBoundGetCounterpartyByCounterpartyIdFuture (adapterCallContext: AdapterCallContext,
                                                          counterpartyId: CounterpartyId)
case class InBoundGetCounterpartyByCounterpartyIdFuture (adapterCallContext: AdapterCallContext, data: CounterpartyTraitCommons)


case class OutBoundGetCounterpartyByIban (adapterCallContext: AdapterCallContext,
                                          iban: String)
case class InBoundGetCounterpartyByIban (adapterCallContext: AdapterCallContext, data: CounterpartyTraitCommons)


case class OutBoundGetCounterpartiesFuture (adapterCallContext: AdapterCallContext,
                                            thisBankId: BankId,
                                            thisAccountId: AccountId,
                                            viewId: ViewId)
case class InBoundGetCounterpartiesFuture (adapterCallContext: AdapterCallContext, data: List[CounterpartyTraitCommons])


case class OutBoundGetTransactionsFuture (adapterCallContext: AdapterCallContext,
                                          bankId: BankId,
                                          accountID: AccountId)
case class InBoundGetTransactionsFuture (adapterCallContext: AdapterCallContext, data: List[TransactionCommons])


case class OutBoundGetTransactionFuture (adapterCallContext: AdapterCallContext,
                                         bankId: BankId,
                                         accountID: AccountId,
                                         transactionId: TransactionId)
case class InBoundGetTransactionFuture (adapterCallContext: AdapterCallContext, data: TransactionCommons)


case class OutBoundMakePaymentv210 (adapterCallContext: AdapterCallContext,
                                    fromAccount: BankAccount,
                                    toAccount: BankAccount,
                                    transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
                                    amount: BigDecimal,
                                    description: String,
                                    transactionRequestType: TransactionRequestType,
                                    chargePolicy: String)

case class InBoundMakePaymentv210 (adapterCallContext: AdapterCallContext, data: TransactionId)


case class OutBoundCreateTransactionRequestv210 (adapterCallContext: AdapterCallContext,
                                                 initiator: User, //TODO FIXME
                                                 viewId: ViewId,
                                                 fromAccount: BankAccountCommons,
                                                 toAccount: BankAccountCommons,
                                                 transactionRequestType: TransactionRequestType,
                                                 transactionRequestCommonBody: TransactionRequestCommonBodyJSONCommons,
                                                 detailsPlain: String,
                                                 chargePolicy: String)
case class InBoundCreateTransactionRequestv210 (adapterCallContext: AdapterCallContext, data: TransactionRequest)


case class OutBoundCreateTransactionAfterChallengeV210 (adapterCallContext: AdapterCallContext,
                                                        fromAccount: BankAccountCommons,
                                                        transactionRequest: TransactionRequest)
case class InBoundCreateTransactionAfterChallengeV210 (adapterCallContext: AdapterCallContext, data: TransactionRequest)


case class OutBoundGetBranchFuture (adapterCallContext: AdapterCallContext,
                                    bankId: BankId,
                                    branchId: BranchId)
case class InBoundGetBranchFuture (adapterCallContext: AdapterCallContext, data: BranchTCommons)


case class OutBoundGetBranchesFuture (adapterCallContext: AdapterCallContext,
                                      bankId: BankId)
case class InBoundGetBranchesFuture (adapterCallContext: AdapterCallContext, data: List[BranchTCommons])


case class OutBoundGetAtmFuture (adapterCallContext: AdapterCallContext,
                                 bankId: BankId,
                                 atmId: AtmId)
case class InBoundGetAtmFuture (adapterCallContext: AdapterCallContext, data: AtmTCommons)


case class OutBoundGetAtmsFuture (adapterCallContext: AdapterCallContext,
                                  bankId: BankId)
case class InBoundGetAtmsFuture (adapterCallContext: AdapterCallContext, data: List[AtmTCommons])


case class OutBoundCreateTransactionAfterChallengev300 (adapterCallContext: AdapterCallContext,
                                                        initiator: User,       //TODO fixme
                                                        fromAccount: BankAccountCommons,
                                                        transReqId: TransactionRequestId,
                                                        transactionRequestType: TransactionRequestType)
case class InBoundCreateTransactionAfterChallengev300 (adapterCallContext: AdapterCallContext, data: TransactionRequest)


case class OutBoundMakePaymentv300 (adapterCallContext: AdapterCallContext,
                                    initiator: User,      //TODO fixme
                                    fromAccount: BankAccountCommons,
                                    toAccount: BankAccountCommons,
                                    toCounterparty: CounterpartyTraitCommons,
                                    transactionRequestCommonBody: TransactionRequestCommonBodyJSON, //TODO FIXME
                                    transactionRequestType: TransactionRequestType,
                                    chargePolicy: String)
case class InBoundMakePaymentv300 (adapterCallContext: AdapterCallContext, data: TransactionId)


case class OutBoundCreateTransactionRequestv300 (adapterCallContext: AdapterCallContext,
                                                 initiator: User,      //TODO fixme
                                                 viewId: ViewId,
                                                 fromAccount: BankAccountCommons,
                                                 toAccount: BankAccountCommons,
                                                 toCounterparty: CounterpartyTraitCommons,
                                                 transactionRequestType: TransactionRequestType,
                                                 transactionRequestCommonBody: TransactionRequestCommonBodyJSON, //TODO FIXME
                                                 detailsPlain: String,
                                                 chargePolicy: String)
case class InBoundCreateTransactionRequestv300 (adapterCallContext: AdapterCallContext, data: TransactionRequest)


case class OutBoundCreateCustomerFuture (adapterCallContext: AdapterCallContext,
                                         bankId: BankId,
                                         legalName: String,
                                         mobileNumber: String,
                                         email: String,
                                         faceImage: CustomerFaceImage,
                                         dateOfBirth: Date,
                                         relationshipStatus: String,
                                         dependents: Int,
                                         dobOfDependents: List[Date],
                                         highestEducationAttained: String,
                                         employmentStatus: String,
                                         kycStatus: Boolean,
                                         lastOkDate: Date,
                                         creditRating: Option[CreditRating],
                                         creditLimit: Option[AmountOfMoney])
case class InBoundCreateCustomerFuture (adapterCallContext: AdapterCallContext, data: CustomerCommons)


case class OutBoundGetCustomersByUserIdFuture (adapterCallContext: AdapterCallContext,
                                               userId: String)
case class InBoundGetCustomersByUserIdFuture (adapterCallContext: AdapterCallContext, data: List[CustomerCommons])


case class OutBoundGetCustomerByCustomerIdFuture (adapterCallContext: AdapterCallContext,
                                                  customerId: String)
case class InBoundGetCustomerByCustomerIdFuture (adapterCallContext: AdapterCallContext, data: CustomerCommons)


case class OutBoundGetCustomerByCustomerNumberFuture (adapterCallContext: AdapterCallContext,
                                                      customerNumber: String,
                                                      bankId: BankId)
case class InBoundGetCustomerByCustomerNumberFuture (adapterCallContext: AdapterCallContext, data: CustomerCommons)


case class OutBoundGetCustomerAddress (adapterCallContext: AdapterCallContext,
                                       customerId: String)
case class InBoundGetCustomerAddress (adapterCallContext: AdapterCallContext, data: List[CustomerAddressCommons])


case class OutBoundCreateCustomerAddress (adapterCallContext: AdapterCallContext,
                                          customerId: String,
                                          line1: String,
                                          line2: String,
                                          line3: String,
                                          city: String,
                                          county: String,
                                          state: String,
                                          postcode: String,
                                          countryCode: String,
                                          tags: String,
                                          status: String)
case class InBoundCreateCustomerAddress (adapterCallContext: AdapterCallContext, data: CustomerAddressCommons)


case class OutBoundUpdateCustomerAddress (adapterCallContext: AdapterCallContext,
                                          customerAddressId: String,
                                          line1: String,
                                          line2: String,
                                          line3: String,
                                          city: String,
                                          county: String,
                                          state: String,
                                          postcode: String,
                                          countryCode: String,
                                          tags: String,
                                          status: String)
case class InBoundUpdateCustomerAddress (adapterCallContext: AdapterCallContext, data: CustomerAddressCommons)


case class OutBoundCreateTaxResidence (adapterCallContext: AdapterCallContext,
                                       customerId: String,
                                       domain: String,
                                       taxNumber: String)
case class InBoundCreateTaxResidence (adapterCallContext: AdapterCallContext, data: TaxResidenceCommons)


case class OutBoundGetTaxResidence (adapterCallContext: AdapterCallContext,
                                    customerId: String)
case class InBoundGetTaxResidence (adapterCallContext: AdapterCallContext, data: List[TaxResidenceCommons])


case class OutBoundGetCustomersFuture (adapterCallContext: AdapterCallContext,
                                       bankId: BankId)
case class InBoundGetCustomersFuture (adapterCallContext: AdapterCallContext, data: List[CustomerCommons])


case class OutBoundGetCheckbookOrdersFuture (adapterCallContext: AdapterCallContext,
                                             bankId: String,
                                             accountId: String)
case class InBoundGetCheckbookOrdersFuture (adapterCallContext: AdapterCallContext, data: CheckbookOrdersJson)


case class OutBoundGetStatusOfCreditCardOrderFuture (adapterCallContext: AdapterCallContext,
                                                     bankId: String,
                                                     accountId: String)
case class InBoundGetStatusOfCreditCardOrderFuture (adapterCallContext: AdapterCallContext, data: List[CardObjectJson])


case class OutBoundCreateUserAuthContext (adapterCallContext: AdapterCallContext,
                                          userId: String,
                                          key: String,
                                          value: String)
case class InBoundCreateUserAuthContext (adapterCallContext: AdapterCallContext, data: UserAuthContextCommons)


case class OutBoundGetUserAuthContexts (adapterCallContext: AdapterCallContext,
                                        userId: String)
case class InBoundGetUserAuthContexts (adapterCallContext: AdapterCallContext, data: List[UserAuthContextCommons])


case class OutBoundCreateOrUpdateProductAttribute (adapterCallContext: AdapterCallContext,
                                                   bankId: BankId,
                                                   productCode: ProductCode,
                                                   productAttributeId: Option[String],
                                                   name: String,
                                                   attributType: ProductAttributeType.Value,
                                                   value: String)
case class InBoundCreateOrUpdateProductAttribute (adapterCallContext: AdapterCallContext, data: ProductAttributeCommons)


case class OutBoundGetProductAttributeById (adapterCallContext: AdapterCallContext,
                                            productAttributeId: String)
case class InBoundGetProductAttributeById (adapterCallContext: AdapterCallContext, data: ProductAttributeCommons)


case class OutBoundGetProductAttributesByBankAndCode (adapterCallContext: AdapterCallContext,
                                                      bank: BankId,
                                                      productCode: ProductCode)
case class InBoundGetProductAttributesByBankAndCode (adapterCallContext: AdapterCallContext, data: List[ProductAttributeCommons])


case class OutBoundCreateOrUpdateAccountAttribute (adapterCallContext: AdapterCallContext,
                                                   bankId: BankId,
                                                   accountId: AccountId,
                                                   productCode: ProductCode,
                                                   productAttributeId: Option[String],
                                                   name: String,
                                                   attributType: AccountAttributeType.Value,
                                                   value: String)
case class InBoundCreateOrUpdateAccountAttribute (adapterCallContext: AdapterCallContext, data: AccountAttributeCommons)


case class OutBoundCreateAccountApplication (adapterCallContext: AdapterCallContext,
                                             productCode: ProductCode,
                                             userId: Option[String],
                                             customerId: Option[String])
case class InBoundCreateAccountApplication (adapterCallContext: AdapterCallContext, data: AccountApplicationCommons)


case class OutBoundGetAllAccountApplication (adapterCallContext: AdapterCallContext)
case class InBoundGetAllAccountApplication (adapterCallContext: AdapterCallContext, data: List[AccountApplicationCommons])


case class OutBoundGetAccountApplicationById (adapterCallContext: AdapterCallContext,
                                              accountApplicationId: String)
case class InBoundGetAccountApplicationById (adapterCallContext: AdapterCallContext, data: AccountApplicationCommons)


case class OutBoundUpdateAccountApplicationStatus (adapterCallContext: AdapterCallContext,
                                                   accountApplicationId: String,
                                                   status: String)
case class InBoundUpdateAccountApplicationStatus (adapterCallContext: AdapterCallContext, data: AccountApplicationCommons)


case class OutBoundGetOrCreateProductCollection (adapterCallContext: AdapterCallContext,
                                                 collectionCode: String,
                                                 productCodes: List[String])
case class InBoundGetOrCreateProductCollection (adapterCallContext: AdapterCallContext, data: List[ProductCollectionCommons])


case class OutBoundGetProductCollection (adapterCallContext: AdapterCallContext,
                                         collectionCode: String)
case class InBoundGetProductCollection (adapterCallContext: AdapterCallContext, data: List[ProductCollectionCommons])


case class OutBoundGetOrCreateProductCollectionItem (adapterCallContext: AdapterCallContext,
                                                     collectionCode: String,
                                                     memberProductCodes: List[String])
case class InBoundGetOrCreateProductCollectionItem (adapterCallContext: AdapterCallContext, data: List[ProductCollectionItemCommons])


case class OutBoundGetProductCollectionItem (adapterCallContext: AdapterCallContext,
                                             collectionCode: String)
case class InBoundGetProductCollectionItem (adapterCallContext: AdapterCallContext, data: List[ProductCollectionItemCommons])


case class OutBoundGetProductCollectionItemsTree (adapterCallContext: AdapterCallContext,
                                                  collectionCode: String,
                                                  bankId: String)
case class InBoundGetProductCollectionItemsTree (adapterCallContext: AdapterCallContext, data: List[(ProductCollectionItemCommons, ProductCommons, List[ProductAttributeCommons])])


case class OutBoundCreateMeeting (adapterCallContext: AdapterCallContext,
                                  bankId: BankId,
                                  staffUser: User,      //TODO fixme
                                  customerUser: User,      //TODO fixme
                                  providerId: String,
                                  purposeId: String,
                                  when: Date,
                                  sessionId: String,
                                  customerToken: String,
                                  staffToken: String,
                                  creator: ContactDetails,
                                  invitees: List[Invitee])
case class InBoundCreateMeeting (adapterCallContext: AdapterCallContext, data: MeetingCommons)


case class OutBoundGetMeetings (adapterCallContext: AdapterCallContext,
                                bankId: BankId,
                                user: User) //TODO fixme
case class InBoundGetMeetings (adapterCallContext: AdapterCallContext, data: List[MeetingCommons])


case class OutBoundGetMeeting (adapterCallContext: AdapterCallContext,
                               bankId: BankId,
                               user: User,      //TODO fixme
                               meetingId: String)
case class InBoundGetMeeting (adapterCallContext: AdapterCallContext, data: MeetingCommons)

case class OutBoundGetUser(adapterCallContext: AdapterCallContext, name: String, password: String)

case class InBoundGetUser(adapterCallContext: AdapterCallContext, data: InboundUser)


//create bound case classes
case class OutBoundCreateChallenge(adapterCallContext: AdapterCallContext, bankId: BankId, accountId: AccountId, userId: String, transactionRequestType: TransactionRequestType, transactionRequestId: String)

case class InBoundCreateChallenge(adapterCallContext: AdapterCallContext, data: String)

case class OutBoundCreateCounterparty(adapterCallContext: AdapterCallContext, name: String, description: String, createdByUserId: String, thisBankId: String, thisAccountId: String, thisViewId: String, otherAccountRoutingScheme: String, otherAccountRoutingAddress: String, otherAccountSecondaryRoutingScheme: String, otherAccountSecondaryRoutingAddress: String, otherBankRoutingScheme: String, otherBankRoutingAddress: String, otherBranchRoutingScheme: String, otherBranchRoutingAddress: String, isBeneficiary: Boolean, bespoke: List[CounterpartyBespoke])

case class InBoundCreateCounterparty(adapterCallContext: AdapterCallContext, data: CounterpartyTraitCommons)

case class OutBoundGetTransactionRequests210(adapterCallContext: AdapterCallContext, initiator : User, fromAccount : BankAccount)

case class InBoundGetTransactionRequests210(adapterCallContext: AdapterCallContext, data: List[TransactionRequest])

case class OutBoundGetTransactionsCore(bankId: BankId, accountID: AccountId) //, queryParams: OBPQueryParam*
case class InBoundGetTransactionsCore(adapterCallContext: AdapterCallContext, data: List[TransactionCore])


//-------- return type are not Future

case class OutBoundGetAdapterInfo (adapterCallContext: AdapterCallContext)
case class InBoundGetAdapterInfo (adapterCallContext: AdapterCallContext, data: InboundAdapterInfoInternal)


case class OutBoundGetBank (adapterCallContext: AdapterCallContext,
                            bankId: BankId)
case class InBoundGetBank (adapterCallContext: AdapterCallContext, data: BankCommons)


case class OutBoundGetBanks (adapterCallContext: AdapterCallContext)
case class InBoundGetBanks (adapterCallContext: AdapterCallContext, data: List[BankCommons])


case class OutBoundGetBankAccounts (adapterCallContext: AdapterCallContext,
                                    accounts: List[(BankId, AccountId)])
case class InBoundGetBankAccounts (adapterCallContext: AdapterCallContext, data: List[BankAccountCommons])


case class OutBoundGetBankAccountsByUsername (adapterCallContext: AdapterCallContext,
                                              username: String)
case class InBoundGetBankAccountsByUsername (adapterCallContext: AdapterCallContext, data: List[InboundAccountCommonCommons])


case class OutBoundGetCoreBankAccounts (adapterCallContext: AdapterCallContext,
                                        bankIdAccountIds: List[BankIdAccountId])
case class InBoundGetCoreBankAccounts (adapterCallContext: AdapterCallContext, data: List[CoreAccount])


case class OutBoundGetBankAccountsHeld (adapterCallContext: AdapterCallContext,
                                        bankIdAccountIds: List[BankIdAccountId])
case class InBoundGetBankAccountsHeld (adapterCallContext: AdapterCallContext, data: List[AccountHeld])


case class OutBoundCheckBankAccountExists (adapterCallContext: AdapterCallContext,
                                           bankId: BankId,
                                           accountId: AccountId)
case class InBoundCheckBankAccountExists (adapterCallContext: AdapterCallContext, data: BankAccountCommons)


case class OutBoundGetEmptyBankAccount (adapterCallContext: AdapterCallContext)
case class InBoundGetEmptyBankAccount (adapterCallContext: AdapterCallContext, data: BankAccountCommons)


case class OutBoundGetCounterpartyFromTransaction (adapterCallContext: AdapterCallContext,
                                                   bankId: BankId,
                                                   accountId: AccountId,
                                                   counterpartyId: String)
case class InBoundGetCounterpartyFromTransaction (adapterCallContext: AdapterCallContext, data: Counterparty)


case class OutBoundGetCounterpartiesFromTransaction (adapterCallContext: AdapterCallContext,
                                                     bankId: BankId,
                                                     accountId: AccountId)
case class InBoundGetCounterpartiesFromTransaction (adapterCallContext: AdapterCallContext, data: List[Counterparty])


case class OutBoundGetCounterparty (adapterCallContext: AdapterCallContext,
                                    thisBankId: BankId,
                                    thisAccountId: AccountId,
                                    couterpartyId: String)
case class InBoundGetCounterparty (adapterCallContext: AdapterCallContext, data: Counterparty)


case class OutBoundGetCounterpartyByCounterpartyId (adapterCallContext: AdapterCallContext,
                                                    counterpartyId: CounterpartyId)
case class InBoundGetCounterpartyByCounterpartyId (adapterCallContext: AdapterCallContext, data: CounterpartyTraitCommons)


case class OutBoundGetCounterparties (adapterCallContext: AdapterCallContext,
                                      thisBankId: BankId,
                                      thisAccountId: AccountId,
                                      viewId: ViewId)
case class InBoundGetCounterparties (adapterCallContext: AdapterCallContext, data: List[CounterpartyTraitCommons])


case class OutBoundGetTransaction (adapterCallContext: AdapterCallContext,
                                   bankId: BankId,
                                   accountID: AccountId,
                                   transactionId: TransactionId)
case class InBoundGetTransaction (adapterCallContext: AdapterCallContext, data: TransactionCommons)


case class OutBoundGetPhysicalCards (adapterCallContext: AdapterCallContext,
                                     user: User)
case class InBoundGetPhysicalCards (adapterCallContext: AdapterCallContext, data: List[PhysicalCard])


case class OutBoundGetPhysicalCardsForBank (adapterCallContext: AdapterCallContext,
                                            bank: Bank,
                                            user: User)
case class InBoundGetPhysicalCardsForBank (adapterCallContext: AdapterCallContext, data: List[PhysicalCard])


case class OutBoundCreateOrUpdatePhysicalCard (adapterCallContext: AdapterCallContext,
                                               bankCardNumber: String,
                                               nameOnCard: String,
                                               issueNumber: String,
                                               serialNumber: String,
                                               validFrom: Date,
                                               expires: Date,
                                               enabled: Boolean,
                                               cancelled: Boolean,
                                               onHotList: Boolean,
                                               technology: String,
                                               networks: List[String],
                                               allows: List[String],
                                               accountId: String,
                                               bankId: String,
                                               replacement: Option[CardReplacementInfo],
                                               pinResets: List[PinResetInfo],
                                               collected: Option[CardCollectionInfo],
                                               posted: Option[CardPostedInfo])
case class InBoundCreateOrUpdatePhysicalCard (adapterCallContext: AdapterCallContext, data: PhysicalCard)


case class OutBoundMakePayment (adapterCallContext: AdapterCallContext,
                                initiator: User,
                                fromAccountUID: BankIdAccountId,
                                toAccountUID: BankIdAccountId,
                                amt: BigDecimal,
                                description: String,
                                transactionRequestType: TransactionRequestType)
case class InBoundMakePayment (adapterCallContext: AdapterCallContext, data: TransactionId)


case class OutBoundMakePaymentv200 (adapterCallContext: AdapterCallContext,
                                    fromAccount: BankAccount,
                                    toAccount: BankAccount,
                                    transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
                                    amount: BigDecimal,
                                    description: String,
                                    transactionRequestType: TransactionRequestType,
                                    chargePolicy: String)
case class InBoundMakePaymentv200 (adapterCallContext: AdapterCallContext, data: TransactionId)


case class OutBoundMakePaymentImpl (adapterCallContext: AdapterCallContext,
                                    fromAccount: BankAccount,
                                    toAccount: BankAccount,
                                    transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
                                    amt: BigDecimal,
                                    description: String,
                                    transactionRequestType: TransactionRequestType,
                                    chargePolicy: String)
case class InBoundMakePaymentImpl (adapterCallContext: AdapterCallContext, data: TransactionId)


case class OutBoundCreateTransactionRequest (adapterCallContext: AdapterCallContext,
                                             initiator: User,
                                             fromAccount: BankAccount,
                                             toAccount: BankAccount,
                                             transactionRequestType: TransactionRequestType,
                                             body: TransactionRequestBody)
case class InBoundCreateTransactionRequest (adapterCallContext: AdapterCallContext, data: TransactionRequest)


case class OutBoundCreateTransactionRequestv200 (adapterCallContext: AdapterCallContext,
                                                 initiator: User,
                                                 fromAccount: BankAccount,
                                                 toAccount: BankAccount,
                                                 transactionRequestType: TransactionRequestType,
                                                 body: TransactionRequestBody)
case class InBoundCreateTransactionRequestv200 (adapterCallContext: AdapterCallContext, data: TransactionRequest)


case class OutBoundCreateTransactionRequestImpl (adapterCallContext: AdapterCallContext,
                                                 transactionRequestId: TransactionRequestId,
                                                 transactionRequestType: TransactionRequestType,
                                                 fromAccount: BankAccount,
                                                 counterparty: BankAccount,
                                                 body: TransactionRequestBody,
                                                 status: String,
                                                 charge: TransactionRequestCharge)
case class InBoundCreateTransactionRequestImpl (adapterCallContext: AdapterCallContext, data: TransactionRequest)


case class OutBoundCreateTransactionRequestImpl210 (adapterCallContext: AdapterCallContext,
                                                    transactionRequestId: TransactionRequestId,
                                                    transactionRequestType: TransactionRequestType,
                                                    fromAccount: BankAccount,
                                                    toAccount: BankAccount,
                                                    transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
                                                    details: String,
                                                    status: String,
                                                    charge: TransactionRequestCharge,
                                                    chargePolicy: String)
case class InBoundCreateTransactionRequestImpl210 (adapterCallContext: AdapterCallContext, data: TransactionRequest)


case class OutBoundGetTransactionRequests (adapterCallContext: AdapterCallContext,
                                           initiator: User,
                                           fromAccount: BankAccount)
case class InBoundGetTransactionRequests (adapterCallContext: AdapterCallContext, data: List[TransactionRequest])


case class OutBoundGetTransactionRequestStatuses (adapterCallContext: AdapterCallContext,
                                                 )
case class InBoundGetTransactionRequestStatuses (adapterCallContext: AdapterCallContext, data: TransactionRequestStatusCommons)


case class OutBoundGetTransactionRequestStatusesImpl (adapterCallContext: AdapterCallContext,
                                                     )
case class InBoundGetTransactionRequestStatusesImpl (adapterCallContext: AdapterCallContext, data: TransactionRequestStatusCommons)


case class OutBoundGetTransactionRequestsImpl (adapterCallContext: AdapterCallContext,
                                               fromAccount: BankAccount)
case class InBoundGetTransactionRequestsImpl (adapterCallContext: AdapterCallContext, data: List[TransactionRequest])


case class OutBoundGetTransactionRequestsImpl210 (adapterCallContext: AdapterCallContext,
                                                  fromAccount: BankAccount)
case class InBoundGetTransactionRequestsImpl210 (adapterCallContext: AdapterCallContext, data: List[TransactionRequest])


case class OutBoundGetTransactionRequestImpl (adapterCallContext: AdapterCallContext,
                                              transactionRequestId: TransactionRequestId)
case class InBoundGetTransactionRequestImpl (adapterCallContext: AdapterCallContext, data: TransactionRequest)


case class OutBoundGetTransactionRequestTypes (adapterCallContext: AdapterCallContext,
                                               initiator: User,
                                               fromAccount: BankAccount)
case class InBoundGetTransactionRequestTypes (adapterCallContext: AdapterCallContext, data: List[TransactionRequestType])


case class OutBoundGetTransactionRequestTypesImpl (adapterCallContext: AdapterCallContext,
                                                   fromAccount: BankAccount)
case class InBoundGetTransactionRequestTypesImpl (adapterCallContext: AdapterCallContext, data: List[TransactionRequestType])


case class OutBoundCreateTransactionAfterChallenge (adapterCallContext: AdapterCallContext,
                                                    initiator: User,
                                                    transReqId: TransactionRequestId)
case class InBoundCreateTransactionAfterChallenge (adapterCallContext: AdapterCallContext, data: TransactionRequest)


case class OutBoundCreateTransactionAfterChallengev200 (adapterCallContext: AdapterCallContext,
                                                        fromAccount: BankAccount,
                                                        toAccount: BankAccount,
                                                        transactionRequest: TransactionRequest)
case class InBoundCreateTransactionAfterChallengev200 (adapterCallContext: AdapterCallContext, data: TransactionRequest)


case class OutBoundCreateBankAndAccount (adapterCallContext: AdapterCallContext,
                                         bankName: String,
                                         bankNationalIdentifier: String,
                                         accountNumber: String,
                                         accountType: String,
                                         accountLabel: String,
                                         currency: String,
                                         accountHolderName: String,
                                         branchId: String,
                                         accountRoutingScheme: String,
                                         accountRoutingAddress: String)
case class InBoundCreateBankAndAccount (adapterCallContext: AdapterCallContext, data: (BankCommons, BankAccountCommons))


case class OutBoundGetProducts (adapterCallContext: AdapterCallContext,
                                bankId: BankId)
case class InBoundGetProducts (adapterCallContext: AdapterCallContext, data: List[ProductCommons])


case class OutBoundGetProduct (adapterCallContext: AdapterCallContext,
                               bankId: BankId,
                               productCode: ProductCode)
case class InBoundGetProduct (adapterCallContext: AdapterCallContext, data: ProductCommons)


case class OutBoundCreateOrUpdateBank (adapterCallContext: AdapterCallContext,
                                       bankId: String,
                                       fullBankName: String,
                                       shortBankName: String,
                                       logoURL: String,
                                       websiteURL: String,
                                       swiftBIC: String,
                                       national_identifier: String,
                                       bankRoutingScheme: String,
                                       bankRoutingAddress: String)
case class InBoundCreateOrUpdateBank (adapterCallContext: AdapterCallContext, data: BankCommons)


case class OutBoundCreateOrUpdateProduct (adapterCallContext: AdapterCallContext,
                                          bankId: String,
                                          code: String,
                                          parentProductCode: Option[String],
                                          name: String,
                                          category: String,
                                          family: String,
                                          superFamily: String,
                                          moreInfoUrl: String,
                                          details: String,
                                          description: String,
                                          metaLicenceId: String,
                                          metaLicenceName: String)
case class InBoundCreateOrUpdateProduct (adapterCallContext: AdapterCallContext, data: ProductCommons)


case class OutBoundGetBranch (adapterCallContext: AdapterCallContext,
                              bankId: BankId,
                              branchId: BranchId)
case class InBoundGetBranch (adapterCallContext: AdapterCallContext, data: BranchTCommons)


case class OutBoundGetAtm (adapterCallContext: AdapterCallContext,
                           bankId: BankId,
                           atmId: AtmId)
case class InBoundGetAtm (adapterCallContext: AdapterCallContext, data: AtmTCommons)


case class OutBoundGetTransactionRequestTypeCharge (adapterCallContext: AdapterCallContext,
                                                    bankId: BankId,
                                                    accountId: AccountId,
                                                    viewId: ViewId,
                                                    transactionRequestType: TransactionRequestType)


case class OutBoundGetCustomerByCustomerId (adapterCallContext: AdapterCallContext,
                                            customerId: String)
case class InBoundGetCustomerByCustomerId (adapterCallContext: AdapterCallContext, data: CustomerCommons)