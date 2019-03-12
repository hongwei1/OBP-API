package code.bankconnectors.rest

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

import java.util.UUID
import java.util.UUID.randomUUID

import code.api.cache.Caching
import code.api.util.APIUtil.{MessageDoc, saveConnectorMetric}
import code.api.util.CallContext
import code.api.util.ErrorMessages._
import code.bankconnectors._
import code.bankconnectors.vJune2017.AuthInfo
import code.bankconnectors.vMar2017._
import code.kafka.KafkaHelper
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

trait RestConnector_vMar2019 extends Connector with KafkaHelper with MdcLoggable {
  
  implicit override val nameOfConnector = RestConnector_vMar2019.toString

  // "Versioning" of the messages sent by this or similar connector works like this:
  // Use Case Classes (e.g. KafkaInbound... KafkaOutbound... as below to describe the message structures.
  // Each connector has a separate file like this one.
  // Once the message format is STABLE, freeze the key/value pair names there. For now, new keys may be added but none modified.
  // If we want to add a new message format, create a new file e.g. March2017_messages.scala
  // Then add a suffix to the connector value i.e. instead of kafka we might have kafka_march_2017.
  // Then in this file, populate the different case classes depending on the connector name and send to Kafka
  val messageFormat: String = "Mar2019"

  implicit val formats = net.liftweb.json.DefaultFormats
  override val messageDocs = ArrayBuffer[MessageDoc]()
  val emptyObjectJson: JValue = decompose(Nil)
  
  val authInfoExample = AuthInfo(userId = "userId", username = "username", cbsToken = "cbsToken")
  val inboundStatusMessagesExample = List(InboundStatusMessage("ESB", "Success", "0", "OK"))
  val errorCodeExample = "INTERNAL-OBP-ADAPTER-6001: ..."
  
  def createArzCustomerId(customerNumber: Int) = UUID.nameUUIDFromBytes(Array(customerNumber.toByte)).toString
  
  override def getAdapterInfo(callContext: Option[CallContext]) = saveConnectorMetric {
    tryo{(
      InboundAdapterInfoInternal(
        errorCode = "",
        backendMessages = Nil,
        name = "Connector_vREST",
        version= "REST",
        git_commit="",
        date=""), 
        callContext
         )}
  }("getAdapterInfo")
  
//  messageDocs += MessageDoc(
//    process = "obp.get.Banks",
//    messageFormat = messageFormat,
//    description = "Gets the banks list on this OBP installation.",
//    outboundTopic = Some(Topics.createTopicByClassName(OutboundGetBanks.getClass.getSimpleName).request),
//    inboundTopic = Some(Topics.createTopicByClassName(OutboundGetBanks.getClass.getSimpleName).response),
//    exampleOutboundMessage = decompose(
//      OutboundGetBanks(authInfoExample)
//    ),
//    exampleInboundMessage = decompose(
//      InboundGetBanks(
//        inboundAuthInfoExample,
//        Status(
//          errorCode = errorCodeExample,
//          inboundStatusMessagesExample),
//        InboundBank(
//          bankId = bankIdExample.value,
//          name = "sushan",
//          logo = "TESOBE",
//          url = "https://tesobe.com/"
//        )  :: Nil
//      )
//    ),
//    outboundAvroSchema = Some(parse(SchemaFor[OutboundGetBanks]().toString(true))),
//    inboundAvroSchema = Some(parse(SchemaFor[InboundGetBanks]().toString(true))),
//    adapterImplementation = Some(AdapterImplementation("- Core", 2))
//  )
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
        Future
        {
          tryo{(List(Bank2(InboundBank("rest","name","logo","url"))), callContext)}
        }
      }
    }
  }("getBanks")
  
}


object RestConnector_vMar2019 extends RestConnector_vMar2019{
   
}