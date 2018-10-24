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
import code.api.util.CallContext
import code.api.util.ErrorMessages._
import code.bankconnectors._
import code.bankconnectors.vARZ.mf_calls.{MfUtil, PostDisposers, PostPrivatkundenkontakte}
import code.bankconnectors.vJune2017.AuthInfo
import code.bankconnectors.vMar2017._
import code.customer._
import code.kafka.KafkaHelper
import code.model._
import code.util.Helper.MdcLoggable
import com.tesobe.CacheKeyFromArguments
import net.liftweb.common.{Box, _}
import net.liftweb.json.Extraction._
import net.liftweb.json.JsonAST.JValue
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
        Full(List(Bank2(InboundBank("bankId","name","logo","url"))), callContext)
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
        Full(Bank2(InboundBank("bankId","name","logo","url")), callContext)
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
            // 1 Build ARZ Json from OBP Json 
            val postkundenkontakteRequestJson = MfUtil.gerernatePostKundeRequest(legalName,mobileNumber, email)
            // 2 Call ARZ create `postPrivatenkundenkontakte` service
            val kundenResult =  PostPrivatkundenkontakte.postPrivatenkundenkontakte(postkundenkontakteRequestJson)
            
            // 3 Call ARZ `postDisposers` service
            val postDisposersRequestJson = MfUtil.gerernatePostDisposerRequest(kundenResult.kundennummer)
          // 2 Call ARZ create `postDisposers` service
            val disposerResult = PostDisposers.postDisposers(postDisposersRequestJson)
          Full(
            //4 Prepare the OBP response 
            InternalCustomer(
              customerId = createArzCustomerId(kundenResult.kundennummer),
              bankId =bankId.value,
              number =kundenResult.kundennummer.toString,
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

}


object Connector_vARZ extends Connector_vARZ{
  
}