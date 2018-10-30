package code.bankconnectors.vARZ

/*
Open Bank Project - API
Copyright (C) 2011-2017, TESOBE Ltd

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program. If not, see http://www.gnu.org/licenses/.

Email: contact@tesobe.com
TESOBE Ltd
Osloerstrasse 16/17
Berlin 13359, Germany
*/

import java.util.{Date, UUID}
import java.util.UUID.randomUUID

import code.api.cache.Caching
import code.api.util.APIUtil.{MessageDoc, saveConnectorMetric}
import code.api.util.{APIUtil, CallContext}
import code.api.util.ErrorMessages._
import code.api.v2_1_0.TransactionRequestCommonBodyJSON
import code.bankconnectors.LocalMappedConnector.{getBankAccount, getTransactionsTTL, saveTransaction, updateAccountTransactions}
import code.bankconnectors.{OBPQueryParam, _}
import code.bankconnectors.vARZ.mf_calls._
import code.bankconnectors.vJune2017.AuthInfo
import code.bankconnectors.vMar2017._
import code.customer._
import code.customer.internalMapping.CustomerIDMappingProvider
import code.fx.fx
import code.kafka.KafkaHelper
import code.model._
import code.model.dataAccess.MappedBankAccountData
import code.transaction.MappedTransaction
import code.usercustomerlinks.{MappedUserCustomerLinkProvider, UserCustomerLink}
import code.users.Users
import code.util.Helper
import code.util.Helper.MdcLoggable
import com.tesobe.CacheKeyFromArguments
import net.liftweb.common.{Box, _}
import net.liftweb.json.Extraction._
import net.liftweb.json.JsonAST.JValue
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo

import scala.collection.immutable.{List, Nil}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

trait Connector_vARZ extends Connector with KafkaHelper with MdcLoggable {
  
  implicit override val nameOfConnector = Connector_vARZ.toString

  // "Versioning" of the messages sent by this or similar connector works like this:
  // Use Case Classes (e.g. KafkaInbound... KafkaOutbound... as below to describe the message structures.
  // Each connector has a separate file like this one.
  // Once the message format is STABLE, freeze the key/value pair names there. For now, new keys may be added but none modified.
  // If we want to add a new message format, create a new file e.g. March2017_messages.scala
  // Then add a suffix to the connector value i.e. instead of kafka we might have kafka_march_2017.
  // Then in this file, populate the different case classes depending on the connector name and send to Kafka
  val messageFormat: String = "ARZ"

  implicit val formats = net.liftweb.json.DefaultFormats
  override val messageDocs = ArrayBuffer[MessageDoc]()
  val emptyObjectJson: JValue = decompose(Nil)
  
  def getAuthInfo (callContext: Option[CallContext]): Box[AuthInfo]=
    for{
      cc <- tryo {callContext.get} ?~! NoCallContext
      user <- cc.user
      username <- Full(user.name)
      currentResourceUserId <- Some(user.userId)
      gatewayLoginPayLoad <- cc.gatewayLoginRequestPayload
      cbs_token <- gatewayLoginPayLoad.cbs_token.orElse(Full(""))
      isFirst <- Full(gatewayLoginPayLoad.is_first)
      correlationId <- Full(cc.correlationId)
    } yield{
      AuthInfo(currentResourceUserId,username, cbs_token, isFirst,correlationId)
    }

  val authInfoExample = AuthInfo(userId = "userId", username = "username", cbsToken = "cbsToken")
  val inboundStatusMessagesExample = List(InboundStatusMessage("ESB", "Success", "0", "OK"))
  val errorCodeExample = "INTERNAL-OBP-ADAPTER-6001: ..."
  val statusExample = Status(errorCodeExample, inboundStatusMessagesExample)
  
  def createArzCustomerId(customerNumber: Int) = UUID.nameUUIDFromBytes(Array(customerNumber.toByte)).toString
  
  override def getAdapterInfo(callContext: Option[CallContext]) = {
    Full((
      InboundAdapterInfoInternal(
        errorCode = "",
        backendMessages = Nil,
        name = "Connector_vARZ",
        version= "ARZ",
        git_commit="",
        date=""), 
        callContext
         ))
  }
  override def getBanks(callContext: Option[CallContext])= saveConnectorMetric {
    /**
      * Please noe that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(banksTTL second){
        Full(List(Bank2(InboundBank("arz","name","logo","url"))), callContext)
      }
    }
  }("getBanks")
  override def getBanksFuture(callContext: Option[CallContext]) = saveConnectorMetric {
    /**
      * Please noe that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(banksTTL second){
        Future{getBanks(callContext: Option[CallContext])}
      }
    }
  }("getBanks")
  
  
  override def getBank(bankId: BankId, callContext: Option[CallContext]) =  saveConnectorMetric {
    /**
      * Please noe that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
      * is just a temporary value filed with UUID values in order to prevent any ambiguity.
      * The real value will be assigned by Macro during compile time at this line of a code:
      * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
      */
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(bankTTL second) {
        if (bankId.value =="arz")
          Full(Bank2(InboundBank(bankId.value,"name","logo","url")), callContext)
        else 
          Failure(BankNotFound)
      }
    }
  }("getBank")
  override def getBankFuture(bankId: BankId, callContext: Option[CallContext]) = saveConnectorMetric {
     /**
        * Please noe that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
        * is just a temporary value filed with UUID values in order to prevent any ambiguity.
        * The real value will be assigned by Macro during compile time at this line of a code:
        * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
        */
      var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeWithProvider(Some(cacheKey.toString()))(banksTTL second){
        Future{getBank(bankId: BankId, callContext: Option[CallContext])}
      }
    }
  }("getBank")
  

  override def createCustomerFuture(
    bankId: BankId,
    legalName: String,
    mobileNumber: String,
    email: String,
    faceImage: CustomerFaceImageTrait,
    dateOfBirth: Date,
    relationshipStatus: String,
    dependents: Int,
    dobOfDependents: List[Date],
    highestEducationAttained: String,
    employmentStatus: String,
    kycStatus: Boolean,
    lastOkDate: Date,
    creditRating: Option[CreditRatingTrait],
    creditLimit: Option[AmountOfMoneyTrait],
    callContext: Option[CallContext] = None,
    title: String,
    branchId: String,
    nameSuffix: String
  ) = saveConnectorMetric{
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(createCustomerFutureTTL second){
        Future
        {
            // 1 Prepare the `postkundenkontakteRequestJson`
            val postkundenkontakteRequestJson = MfUtil.generatePostKundeRequest(legalName, mobileNumber, email)
          
            // 2 Call ARZ create `postPrivatenkundenkontakte` service
            val kundenResult =  KundeservicesV3.postPrivatenkundenkontakte(postkundenkontakteRequestJson)
            
            // 3 Prepare the `postDisposersRequestJson`
            val postDisposersRequestJson = MfUtil.generatePostDisposerRequest(kundenResult.kundennummer)
          
            // 4 Call ARZ create `postDisposers` service
            val disposerResult = PostDisposers.postDisposers(postDisposersRequestJson)
          
            //5 Map CustomerNumber to OBP CustomerId (UUID)
            val customeNumber =  kundenResult.kundennummer.toString
            //TODO, need enhance the error handling here:
            val customerMapping = CustomerIDMappingProvider.customerIDMappingProvider.vend.getOrCreateCustomerIDMapping(bankId, customeNumber)
              .openOrThrowException("getOrCreateCustomerIDMapping Error")
            val customerId = customerMapping.customerId.value
          
            //6 Prepare the OBP response 
          Full(
            InternalCustomer(
              customerId = customerId,
              bankId =bankId.value,
              number = customeNumber,
              legalName = legalName,
              mobileNumber =mobileNumber,
              email = email,
              faceImage = CustomerFaceImage(faceImage.date, faceImage.url),
              dateOfBirth = dateOfBirth,
              relationshipStatus = relationshipStatus,
              dependents= dependents,
              dobOfDependents = dobOfDependents,
              highestEducationAttained = highestEducationAttained,
              employmentStatus = employmentStatus,
              creditRating = CreditRating(creditRating.get.rating, creditRating.get.source),
              creditLimit = CreditLimit(creditLimit.get.currency, creditLimit.get.amount ),
              kycStatus = kycStatus,
              lastOkDate = lastOkDate,
              title = title,
              branchId = branchId,
              nameSuffix= nameSuffix
            )
          )
        }
      }
  }}("createCustomerFuture")
  
  // We do not check CustomerNumber in ARZ
  override def checkCustomerNumberAvailableFuture(
    bankId : BankId, 
    customerNumber : String
  ) : Future[Box[Boolean]] = Future{Full(true)}
  
  override def getCustomerByCustomerIdFuture(customerId: String, callContext: Option[CallContext]): Future[Box[(Customer,Option[CallContext])]] = Future
  {
    getCustomerByCustomerId(customerId: String, callContext: Option[CallContext])
  }

  override def getCustomerByCustomerId(customerId: String, callContext: Option[CallContext]) = {
    val customerMapping = CustomerIDMappingProvider.customerIDMappingProvider.vend.getCustomerIDMapping(CustomerId(customerId))
    .openOrThrowException("getCustomerByCustomerId Error")
    
    val customerNumber = customerMapping.customerNumber
    val cbsCustomer = KundeservicesV3.getKunten(customerNumber)
    
    Full(
      InternalCustomer(
        customerId = customerId,
        bankId =customerMapping.bankId.value,
        number = customerMapping.customerNumber,
        legalName = cbsCustomer.name1,
        mobileNumber ="",
        email = cbsCustomer.emailadresse.map(_.value).getOrElse(""),
        faceImage = CustomerFaceImage(new Date(),"" ),
        dateOfBirth = new Date(),
        relationshipStatus = "",
        dependents = 1,
        dobOfDependents = List(new Date()),
        highestEducationAttained = "",
        employmentStatus = "",
        creditRating = CreditRating("",""), 
        creditLimit  = CreditLimit("",""), 
        kycStatus = cbsCustomer.active,    
        lastOkDate = new Date(),   
        title = "",        
        branchId = "",     
        nameSuffix= ""
      ), callContext)
  }
  
  override def getCustomerByCustomerNumberFuture(customerNumber : String, bankId : BankId, callContext: Option[CallContext]): Future[Box[(Customer, Option[CallContext])]] = Future{

    val customerMapping = CustomerIDMappingProvider.customerIDMappingProvider.vend.getOrCreateCustomerIDMapping(bankId, customerNumber)
    .openOrThrowException("getCustomerByCustomerId Error")
    
    val cbsCustomer = KundeservicesV3.getKunten(customerNumber)
    
    Full(
      //TODO, here need call CBS-getCustomerByCustomerNumber, return some real CBS data.
      InternalCustomer(
        customerId = customerMapping.customerId.value,
        bankId =customerMapping.bankId.value,
        number = customerMapping.customerNumber,
        legalName = cbsCustomer.name1,
        mobileNumber ="",
        email = cbsCustomer.emailadresse.map(_.value).getOrElse(""),
        faceImage = CustomerFaceImage(new Date(),"" ),
        dateOfBirth = new Date(),
        relationshipStatus = "",
        dependents = 1,
        dobOfDependents = List(new Date()),
        highestEducationAttained = "",
        employmentStatus = "",
        creditRating = CreditRating("",""), 
        creditLimit  = CreditLimit("",""), 
        kycStatus = cbsCustomer.active,    
        lastOkDate = new Date(),   
        title = "",        
        branchId = "",     
        nameSuffix= ""
      ), callContext)
    
  }
    

  //This one will map Bank Customer data with obp user and accounts data. 
  override def getBankAccounts(username: String, callContext: Option[CallContext]) : Box[(List[InboundAccountCommon], Option[CallContext])] = {
    
    val user = Users.users.vend.getUserByUserName(username).openOrThrowException("Can not find the user! ")
    
    val cbsAccountsList= for{
      customerLink <- UserCustomerLink.userCustomerLink.vend.getUserCustomerLinksByUserId(user.userId)
      customerIDMapping <- CustomerIDMappingProvider.customerIDMappingProvider.vend.getCustomerIDMapping(CustomerId(customerLink.customerId))
      customerNumber = customerIDMapping.customerNumber
      cbsAccounts <- Full(KundeServicesV4.getKonten(customerNumber))
      obpAccounts <- Full(
        for{
          cbsAccount <- cbsAccounts
          //TODO, this need to be moved into a mapped table!
          accountId = UUID.nameUUIDFromBytes(cbsAccount.kontonummer.toString.getBytes).toString
          obpAccount <- List(InboundAccountArz(
            errorCode = "",
            cbsToken = "",
            bankId ="arz",
            branchId ="",
            accountId = accountId,
            accountNumber = cbsAccount.kontonummer.toString,
            accountType =cbsAccount.geschaeftsart,
            balanceAmount = cbsAccount.saldo.toString,
            balanceCurrency = cbsAccount.waehrung,
            owners = List(cbsAccount.kontobezeichnung),
            viewsToGenerate = List("owner"),
            bankRoutingScheme = "",
            bankRoutingAddress = "",
            branchRoutingScheme = "",
            branchRoutingAddress = "",
            accountRoutingScheme ="",
            accountRoutingAddress=cbsAccount.iban,
            accountRoutings = List(AccountRouting("IBAN", cbsAccount.iban), AccountRouting("AccountNumber", cbsAccount.kontonummer.toString)),
            accountRules = Nil))
        } yield{
          obpAccount
        })
    } yield 
      obpAccounts
    
    Full((cbsAccountsList.flatten, callContext))
    
  }
  
  override def getCoreBankAccountsFuture(BankIdAccountIds: List[BankIdAccountId], callContext: Option[CallContext]) : Future[Box[(List[CoreAccount], Option[CallContext])]] = Future
  {
    
    val user = callContext.get.user.openOrThrowException("Can not find the user! ")
    
    val coreAccounts= for{
      customerLink <- UserCustomerLink.userCustomerLink.vend.getUserCustomerLinksByUserId(user.userId)
      customerIDMapping <- CustomerIDMappingProvider.customerIDMappingProvider.vend.getCustomerIDMapping(CustomerId(customerLink.customerId))
      customerNumber = customerIDMapping.customerNumber
      cbsAccounts <- Full(KundeServicesV4.getKonten(customerNumber))
      obpAccounts <- Full(
        for{
          cbsAccount <- cbsAccounts
          //TODO, this need to be moved into a mapped table!
          accountId = UUID.nameUUIDFromBytes(cbsAccount.kontonummer.toString.getBytes).toString
          obpAccount <- List(CoreAccount(
            id = accountId,
            label = cbsAccount.kontobezeichnung, 
            bankId = "arz",
            accountType = cbsAccount.geschaeftsart,
            accountRoutings = List(AccountRouting("IBAN",cbsAccount.iban), AccountRouting("AccountNumber",cbsAccount.kontonummer.toString))))
        } yield{
          obpAccount
        })
    } yield 
      obpAccounts
    
    Full((coreAccounts.flatten, callContext))
    
  }
  
  override def getCustomersByUserIdFuture(userId: String, callContext: Option[CallContext]): Future[Box[(List[Customer],Option[CallContext])]]= Future{
    val customers = for{
      customerId <- MappedUserCustomerLinkProvider.getUserCustomerLinksByUserId(userId).map(_.customerId)
      (customer, callContext) <- getCustomerByCustomerId(customerId, callContext)      
    } yield 
      customer
    
    Full((customers, callContext))
  }
  
  
  override def getBankAccount(bankId: BankId, accountId: AccountId, callContext: Option[CallContext])=  {
    val account = InboundAccountArz(
            errorCode = "",
            cbsToken = "",
            bankId =bankId.value,
            branchId ="",
            accountId = accountId.value,
            accountNumber = "123",
            accountType = "P",
            balanceAmount = "123",
            balanceCurrency = "EUR",
            owners = List(""),
            viewsToGenerate = List("owner"),
            bankRoutingScheme = "",
            bankRoutingAddress = "",
            branchRoutingScheme = "",
            branchRoutingAddress = "",
            accountRoutingScheme = "",
            accountRoutingAddress = "",
            accountRoutings = Nil,
            accountRules = Nil)
    
    Connector_vARZ.createMappedAccountDataIfNotExisting(bankId.value, accountId.value, "1234")
    
    Full(BankAccountArz(account), callContext)
  }
  
  override def updateAccountLabel(bankId: BankId, accountId: AccountId, label: String) = {
    //this will be Full(true) if everything went well
    val result = for {
      acc <- getBankAccount(bankId, accountId)
      (bank, _)<- getBank(bankId, None)
      d <- MappedBankAccountData.find(By(MappedBankAccountData.accountId, accountId.value), By(MappedBankAccountData.bankId, bank.bankId.value))
    } yield {
      d.setLabel(label)
      d.save()
    }
    Full(result.getOrElse(false))
  }
  
      
//  override def getTransactionsCore(bankId: BankId, accountID: AccountId, callContext: Option[CallContext], queryParams: OBPQueryParam*): Box[(List[TransactionCore], Option[CallContext])]= {
//    val account =  getBankAccount(bankId: BankId, accountID: AccountId, callContext: Option[CallContext])
//    val counterparty = CounterpartyCore(
//      kind ="",
//      counterpartyId ="",
//      counterpartyName ="",
//      thisBankId =BankId(""), 
//      thisAccountId =AccountId(""),
//      otherBankRoutingScheme ="", 
//      otherBankRoutingAddress = None,
//      otherAccountRoutingScheme ="", 
//      otherAccountRoutingAddress = None, 
//      otherAccountProvider ="", 
//      isBeneficiary= true 
//    )
//
//    Full((List(
//      TransactionCore(
//        id = TransactionId("1231"), 
//        thisAccount = account.openOrThrowException("No Accounts")._1,
//        otherAccount = counterparty,
//        transactionType = "",
//        amount = BigDecimal("123"), 
//        currency = "", 
//        description = Some("house"), 
//        startDate = new Date(), 
//        finishDate = new Date(), 
//        balance = BigDecimal("120"))
//    ), 
//         callContext
//    ))  
//  }
//  
//  
//  def getTransactions(bankId: BankId, accountID: AccountId, callContext: Option[CallContext], queryParams: OBPQueryParam*): Box[(List[Transaction], Option[CallContext])]=
  
  override def getTransactionsCore(bankId: BankId, accountId: AccountId, callContext: Option[CallContext], queryParams: OBPQueryParam*) =
    {

      // TODO Refactor this. No need for database lookups etc.
      val limit = queryParams.collect { case OBPLimit(value) => MaxRows[MappedTransaction](value) }.headOption
      val offset = queryParams.collect { case OBPOffset(value) => StartAt[MappedTransaction](value) }.headOption
      val fromDate = queryParams.collect { case OBPFromDate(date) => By_>=(MappedTransaction.tFinishDate, date) }.headOption
      val toDate = queryParams.collect { case OBPToDate(date) => By_<=(MappedTransaction.tFinishDate, date) }.headOption
      val ordering = queryParams.collect {
        //we don't care about the intended sort field and only sort on finish date for now
        case OBPOrdering(_, direction) =>
          direction match {
            case OBPAscending => OrderBy(MappedTransaction.tFinishDate, Ascending)
            case OBPDescending => OrderBy(MappedTransaction.tFinishDate, Descending)
          }
      }

      val optionalParams: Seq[QueryParam[MappedTransaction]] = Seq(limit.toSeq, offset.toSeq, fromDate.toSeq, toDate.toSeq, ordering.toSeq).flatten
      val mapperParams = Seq(By(MappedTransaction.bank, bankId.value), By(MappedTransaction.account, accountId.value)) ++ optionalParams

      def getTransactionsCached(bankId: BankId, accountId: AccountId, optionalParams: Seq[QueryParam[MappedTransaction]]): Box[List[TransactionCore]]
      = {
        /**
          * Please noe that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
          * is just a temporary value filed with UUID values in order to prevent any ambiguity.
          * The real value will be assigned by Macro during compile time at this line of a code:
          * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
          */
        var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
        CacheKeyFromArguments.buildCacheKey {
          Caching.memoizeSyncWithProvider (Some(cacheKey.toString()))(getTransactionsTTL millisecond) {

          //logger.info("Cache miss getTransactionsCached")

          val mappedTransactions = MappedTransaction.findAll(mapperParams: _*)

          for (account <- getBankAccount(bankId, accountId))
            yield mappedTransactions.flatMap(_.toTransactionCore(account)) //each transaction will be modified by account, here we return the `class Transaction` not a trait.
        }
      }
    }

    getTransactionsCached(bankId: BankId, accountId: AccountId, optionalParams).map(transactions =>(transactions,callContext))
  }

  override def getTransactions(bankId: BankId, accountId: AccountId, callContext: Option[CallContext], queryParams: OBPQueryParam*) = {

    // TODO Refactor this. No need for database lookups etc.
    val limit = queryParams.collect { case OBPLimit(value) => MaxRows[MappedTransaction](value) }.headOption
    val offset = queryParams.collect { case OBPOffset(value) => StartAt[MappedTransaction](value) }.headOption
    val fromDate = queryParams.collect { case OBPFromDate(date) => By_>=(MappedTransaction.tFinishDate, date) }.headOption
    val toDate = queryParams.collect { case OBPToDate(date) => By_<=(MappedTransaction.tFinishDate, date) }.headOption
    val ordering = queryParams.collect {
      //we don't care about the intended sort field and only sort on finish date for now
      case OBPOrdering(_, direction) =>
        direction match {
          case OBPAscending => OrderBy(MappedTransaction.tFinishDate, Ascending)
          case OBPDescending => OrderBy(MappedTransaction.tFinishDate, Descending)
        }
    }

    val optionalParams : Seq[QueryParam[MappedTransaction]] = Seq(limit.toSeq, offset.toSeq, fromDate.toSeq, toDate.toSeq, ordering.toSeq).flatten
    val mapperParams = Seq(By(MappedTransaction.bank, bankId.value), By(MappedTransaction.account, accountId.value)) ++ optionalParams

    def getTransactionsCached(bankId: BankId, accountId: AccountId, optionalParams : Seq[QueryParam[MappedTransaction]]) : Box[List[Transaction]]
    = {
      /**
        * Please noe that "var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)"
        * is just a temporary value filed with UUID values in order to prevent any ambiguity.
        * The real value will be assigned by Macro during compile time at this line of a code:
        * https://github.com/OpenBankProject/scala-macros/blob/master/macros/src/main/scala/com/tesobe/CacheKeyFromArgumentsMacro.scala#L49
        */
      var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
      CacheKeyFromArguments.buildCacheKey {
        Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(getTransactionsTTL millisecond) {

          //logger.info("Cache miss getTransactionsCached")

          val mappedTransactions = MappedTransaction.findAll(mapperParams: _*)

          for (account <- getBankAccount(bankId, accountId))
            yield mappedTransactions.flatMap(_.toTransaction(account)) //each transaction will be modified by account, here we return the `class Transaction` not a trait.
        }
      }
    }
    getTransactionsCached(bankId: BankId, accountId: AccountId, optionalParams).map(transactions => (transactions, callContext))
  }
  
  
  override def makePaymentImpl(fromAccount: BankAccount,
                               toAccount: BankAccount,
                               transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
                               amount: BigDecimal,
                               description: String,
                               transactionRequestType: TransactionRequestType,
                               chargePolicy: String): Box[TransactionId] = {
    for{
       rate <- tryo {fx.exchangeRate(fromAccount.currency, toAccount.currency)} ?~! s"$InvalidCurrency The requested currency conversion (${fromAccount.currency} to ${fromAccount.currency}) is not supported."
       fromTransAmt = -amount//from fromAccount balance should decrease
       toTransAmt = fx.convert(amount, rate)
       sentTransactionId <- saveTransaction(fromAccount, toAccount,transactionRequestCommonBody, fromTransAmt, description, transactionRequestType, chargePolicy)
       _sentTransactionId <- saveTransaction(toAccount, fromAccount,transactionRequestCommonBody, toTransAmt, description, transactionRequestType, chargePolicy)
    } yield{
      sentTransactionId
    }
  }
  private def saveTransaction(fromAccount: BankAccount,
                              toAccount: BankAccount,
                              transactionRequestCommonBody: TransactionRequestCommonBodyJSON,
                              amount: BigDecimal,
                              description: String,
                              transactionRequestType: TransactionRequestType,
                              chargePolicy: String): Box[TransactionId] = {
    for{

      currency <- Full(fromAccount.currency)
    //update the balance of the fromAccount for which a transaction is being created


      mappedTransaction <- tryo(MappedTransaction.create
      .bank(fromAccount.bankId.value)
      .account(fromAccount.accountId.value)
      .transactionType(transactionRequestType.value)
      .amount(Helper.convertToSmallestCurrencyUnits(amount, "EUR"))
      .newAccountBalance(1000000)
      .currency(currency)
      .tStartDate(new Date())
      .tFinishDate(new Date())
      .description(description) 
      .counterpartyAccountHolder(toAccount.accountHolder)
      .counterpartyAccountNumber(toAccount.number)
      .counterpartyAccountKind(toAccount.accountType)
      .counterpartyBankName(toAccount.bankName)
      .counterpartyIban(toAccount.iban.getOrElse(""))
      .counterpartyNationalId(toAccount.nationalIdentifier)
      .CPOtherAccountRoutingScheme(toAccount.accountRoutingScheme)
      .CPOtherAccountRoutingAddress(toAccount.accountRoutingAddress)
      .CPOtherBankRoutingScheme(toAccount.bankRoutingScheme)
      .CPOtherBankRoutingAddress(toAccount.bankRoutingAddress)
      .chargePolicy(chargePolicy)
      .saveMe) ?~! s"$CreateTransactionsException, exception happened when create new mappedTransaction"
    } yield{
      mappedTransaction.theTransactionId
    }
  }
  
  
}


object Connector_vARZ extends Connector_vARZ{
  private def createMappedAccountDataIfNotExisting(bankId: String, accountId: String, label: String) : Boolean = {
    MappedBankAccountData.find(By(MappedBankAccountData.accountId, accountId),
                                    By(MappedBankAccountData.bankId, bankId)) match {
      case Empty =>
        val data = new MappedBankAccountData
        data.setAccountId(accountId)
        data.setBankId(bankId)
        data.setLabel(label)
        data.save()
        true
      case _ =>
        logger.info(s"account data with id $accountId at bank with id $bankId already exists. No need to create a new one.")
        false
    }
  }
}