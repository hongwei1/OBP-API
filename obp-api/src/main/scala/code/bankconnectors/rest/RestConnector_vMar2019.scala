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

import java.net.URLEncoder
import java.util.UUID.randomUUID
import java.util.Date

import akka.http.scaladsl.model.{HttpProtocol, _}
import akka.http.scaladsl.model.headers.RawHeader
import akka.util.ByteString
import code.api.APIFailureNewStyle
import code.api.cache.Caching
import code.api.util.APIUtil.{AdapterImplementation, MessageDoc, OBPReturnType, saveConnectorMetric}
import code.api.util.ErrorMessages._
import code.api.util.{CallContext, NewStyle, OBPQueryParam}
import code.bankconnectors._
import code.bankconnectors.vJune2017.AuthInfo
import code.kafka.{KafkaHelper, Topics}
import code.util.AkkaHttpClient._
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.dto.{InBoundTrait, _}
import com.openbankproject.commons.model.{TopicTrait, _}
import com.tesobe.{CacheKeyFromArguments, CacheKeyOmit}
import net.liftweb.common.{Box, Empty, _}
import net.liftweb.util.Helpers.tryo

import scala.collection.immutable.{List, Nil}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{Await, Future}
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.reflect.runtime.universe._
import code.api.util.ExampleValue._
import code.api.util.APIUtil._
import com.openbankproject.commons.model.enums.StrongCustomerAuthentication.SCA
import code.customer.internalMapping.MappedCustomerIdMappingProvider
import code.model.dataAccess.internalMapping.MappedAccountIdMappingProvider
import com.openbankproject.commons.model.enums.{AccountAttributeType, CardAttributeType, ProductAttributeType}
import com.openbankproject.commons.util.ReflectUtils
import net.liftweb.json._

trait RestConnector_vMar2019 extends Connector with KafkaHelper with MdcLoggable {
  //this one import is for implicit convert, don't delete
  import com.openbankproject.commons.model.{CustomerFaceImage, CreditLimit, CreditRating, AmountOfMoney}

  implicit override val nameOfConnector = RestConnector_vMar2019.toString

  // "Versioning" of the messages sent by this or similar connector works like this:
  // Use Case Classes (e.g. KafkaInbound... KafkaOutbound... as below to describe the message structures.
  // Each connector has a separate file like this one.
  // Once the message format is STABLE, freeze the key/value pair names there. For now, new keys may be added but none modified.
  // If we want to add a new message format, create a new file e.g. March2017_messages.scala
  // Then add a suffix to the connector value i.e. instead of kafka we might have kafka_march_2017.
  // Then in this file, populate the different case classes depending on the connector name and send to Kafka
  val messageFormat: String = "March2019"

  override val messageDocs = ArrayBuffer[MessageDoc]()

  val authInfoExample = AuthInfo(userId = "userId", username = "username", cbsToken = "cbsToken")
  val errorCodeExample = "INTERNAL-OBP-ADAPTER-6001: ..."

  val connectorName = "rest_vMar2019"

//---------------- dynamic start -------------------please don't modify this line

//---------------- dynamic end ---------------------please don't modify this line
    

  //In RestConnector, we use the headers to propagate the parameters to Adapter. The parameters come from the CallContext.outboundAdapterAuthInfo.userAuthContext
  //We can set them from UserOauthContext or the http request headers.
  private[this] implicit def buildHeaders(callContext: Option[CallContext]): List[HttpHeader] = {
    val generalContext = callContext.flatMap(_.toOutboundAdapterCallContext.generalContext).getOrElse(List.empty[BasicGeneralContext])
    generalContext.map(generalContext => RawHeader(generalContext.key,generalContext.value))
  }

  private[this] def buildAdapterCallContext(callContext: Option[CallContext]): OutboundAdapterCallContext = callContext.map(_.toOutboundAdapterCallContext).orNull

  /**
    * some methods return type is not future, this implicit method make these method have the same body, it facilitate to generate code.
    *
    * @param future
    * @tparam T
    * @return result of future
    */
  private[this] implicit def convertFuture[T](future: Future[T]): T = Await.result(future, 1.minute)

  /**
   * convert return value of OBPReturnType[Box[T]] to Box[(T, Option[CallContext])], this can let all method have the same body even though return type is not match
   * @param future
   * @tparam T
   * @return
   */
  private[this] implicit def convertFutureToBoxTuple[T](future: OBPReturnType[Box[T]]): Box[(T, Option[CallContext])] = {
    val (boxT, cc) = convertFuture(future)
    boxT.map((_, cc))
  }
  /**
   * convert return value of OBPReturnType[Box[T]] to Box[T], this can let all method have the same body even though return type is not match
   * @param future
   * @tparam T
   * @return
   */
  private[this] implicit def convertFutureToBox[T](future: OBPReturnType[Box[T]]): Box[T] = convertFuture(future)._1
  /**
   * convert return value of OBPReturnType[Box[T]] to Future[T], this can let all method have the same body even though return type is not match
   * @param future
   * @tparam T
   * @return
   */
  private[this] implicit def convertToIgnoreCC[T](future: OBPReturnType[T]): Future[T] = future.map(it => it._1)

  //TODO please modify this baseUrl to your remote api server base url of this connector
  private[this] val baseUrl = "http://localhost:8080/restConnector"

  private[this] def getUrl(callContext: Option[CallContext], methodName: String, variables: (String, Any)*): String = {
    // rest connector can have url value in the parameters, key is url
     
    //Temporary solution:
    val basicUserAuthContext: List[BasicUserAuthContext] = callContext.map(_.toOutboundAdapterCallContext.outboundAdapterAuthInfo.map(_.userAuthContext)).flatten.flatten.getOrElse(List.empty[BasicUserAuthContext])
    val bankId = basicUserAuthContext.find(_.key=="bank-id").map(_.value)
    val accountId = basicUserAuthContext.find(_.key=="account-id").map(_.value)
    val parameterUrl = if (bankId.isDefined &&accountId.isDefined)  s"/${bankId.get},${accountId.get}" else ""
    
     //http://127.0.0.1:8080/restConnector/getBankAccountsBalances/bankIdAccountIds
     val urlInMethodRouting = NewStyle.function.getMethodRoutings(Some(methodName))
       .flatMap(_.parameters)
       .find(_.key == "url")
       .map(_.value)

    // http://127.0.0.1:8080/restConnector/getBankAccountsBalances/bankIdAccountIds/dmo.02.de.de,60e65f3f-0743-41f5-9efd-3c6f0438aa42
    if(urlInMethodRouting.isDefined) {
      return urlInMethodRouting.get + parameterUrl
    }

    // convert any type value to string, to fill in the url
    def urlValueConverter(obj: Any):String = {
      val value = obj match {
        case null => ""
        case seq: Seq[_] => seq.map(_.toString.replaceFirst("^\\w+\\((.*)\\)$", "$1")).mkString(";")
        case seq: Array[_] => seq.map(_.toString.replaceFirst("^\\w+\\((.*)\\)$", "$1")).mkString(";")
        case other => other.toString
      }
      URLEncoder.encode(value, "UTF-8")
    }
    //build queryParams: List[OBPQueryParam] as query parameters
    val queryParams: Option[String] = variables.lastOption
      .filter(it => it._1 == "queryParams" && it._2.isInstanceOf[Seq[_]])
      .map(_._2.asInstanceOf[List[OBPQueryParam]])
      .map { queryParams =>
        val limit = OBPQueryParam.getLimit(queryParams)
        val offset = OBPQueryParam.getOffset(queryParams)
        val fromDate = OBPQueryParam.getFromDate(queryParams)
        val toDate = OBPQueryParam.getToDate(queryParams)
        s"?${OBPQueryParam.LIMIT}=${limit}&${OBPQueryParam.OFFSET}=${offset}&${OBPQueryParam.FROM_DATE}=${fromDate}&${OBPQueryParam.TO_DATE}=${toDate}"
      }
    variables.dropRight(queryParams.size)
      .foldLeft(s"$baseUrl/$methodName")((url, pair) => url.concat(s"/${pair._1}/${urlValueConverter(pair._2)}")) + queryParams.getOrElse("")
  }

  private[this] def sendRequest[T <: InBoundTrait[_]: TypeTag : Manifest](url: String, method: HttpMethod, outBound: TopicTrait, callContext: Option[CallContext]): Future[Box[T]] = {
    //transfer accountId to accountReference and customerId to customerReference in outBound
    this.convertToReference(outBound)
    val outBoundJson = net.liftweb.json.Serialization.write(outBound)
    val request = prepareHttpRequest(url, method, HttpProtocol("HTTP/1.1"), outBoundJson).withHeaders(callContext)
    logger.debug(s"RestConnector_vMar2019 request is : $request")
    val responseFuture = makeHttpRequest(request)
    val jsonType = typeOf[T]
    responseFuture.map {
      case response@HttpResponse(status, _, entity@_, _) => (status, entity)
    }.flatMap {
      case (status, entity) if status.isSuccess() => extractEntity[T](entity)
      case (status, entity) => extractBody(entity) map { msg => tryo {
          parse(msg).extract[T]
        } ~> APIFailureNewStyle(msg, status.intValue())
      }
    }.map(convertToId(_))
  }

  private[this] def extractBody(responseEntity: ResponseEntity): Future[String] = responseEntity.toStrict(2.seconds) flatMap { e =>
    e.dataBytes
      .runFold(ByteString.empty) { case (acc, b) => acc ++ b }
      .map(_.utf8String)
  }

  private[this] def extractEntity[T: Manifest](responseEntity: ResponseEntity): Future[Box[T]] = {
    this.extractBody(responseEntity)
      .map({
        case null => Empty
        case str => tryo {
          parse(str).extract[T]
        } ~> APIFailureNewStyle(s"$InvalidJsonFormat The Json body should be the ${manifest[T]} ", 400)
      })
  }

  /**
    * interpolate url, bind variable
    * e.g: interpolateUrl("http://127.0.0.1:9093/:id/bank/:bank_id", Map("bank_id" -> "myId", "id"-> 123)):
    * result: http://127.0.0.1:9093/123/bank/myId
    *
    * @param urlTemplate url template
    * @param variables   key values
    * @return bind key and value url
    */
  def interpolateUrl(urlTemplate: String, variables: Map[String, Any]) = {
    variables.foldLeft(urlTemplate)((url, pair) => {
      val (key, value) = pair
      url
        // fill this format variables: http://rootpath/banks/{bank-id}
        .replace(s"{$key}", String.valueOf(value))
      // fill this format variables: http://rootpath/banks/:bank-id
      // url.replace(s":${key}", String.valueOf(value))
      // fill this format variables: http://rootpath/banks/:{bank-id}
      //.replaceAll(s":\\{\\s*$key\\s*\\}", String.valueOf(value))
    })
  }


  //-----helper methods

  private[this] def convertToTuple[T](callContext: Option[CallContext]) (inbound: Box[InBoundTrait[T]]): (Box[T], Option[CallContext]) = {
    val boxedResult = inbound match {
      case Full(in) if (in.status.hasNoError) => Full(in.data)
      case Full(inbound) if (inbound.status.hasError) =>
        Failure("INTERNAL-"+ inbound.status.errorCode+". + CoreBank-Status:" + inbound.status.backendMessages)
      case failureOrEmpty: Failure => failureOrEmpty
    }
    (boxedResult, callContext)
  }

  //TODO hongwei confirm the third valu: OutboundAdapterCallContext#adapterAuthInfo
  private[this] def buildCallContext(inboundAdapterCallContext: InboundAdapterCallContext, callContext: Option[CallContext]): Option[CallContext] =
    for (cc <- callContext)
      yield cc.copy(correlationId = inboundAdapterCallContext.correlationId, sessionId = inboundAdapterCallContext.sessionId)

  private[this] def buildCallContext(boxedInboundAdapterCallContext: Box[InboundAdapterCallContext], callContext: Option[CallContext]): Option[CallContext] = boxedInboundAdapterCallContext match {
    case Full(inboundAdapterCallContext) => buildCallContext(inboundAdapterCallContext, callContext)
    case _ => callContext
  }

  /**
   * helper function to convert customerId and accountId in a given instance
   * @param obj
   * @param customerIdConverter customerId converter, to or from customerReference
   * @param accountIdConverter accountId converter, to or from accountReference
   * @tparam T type of instance
   * @return modified instance
   */
  private def convertId[T](obj: T, customerIdConverter: String=> String, accountIdConverter: String=> String): T = {

    def isCustomerId(fieldName: String, fieldType: Type, fieldValue: Any, ownerType: Type) = {
        ownerType <:< typeOf[CustomerId] ||
        (fieldName.equalsIgnoreCase("customerId") && fieldType =:= typeOf[String]) ||
        (ownerType <:< typeOf[Customer] && fieldName.equalsIgnoreCase("id") && fieldType =:= typeOf[String])
      }

    def isAccountId(fieldName: String, fieldType: Type, fieldValue: Any, ownerType: Type) = {
        ownerType <:< typeOf[AccountId] ||
        (fieldName.equalsIgnoreCase("accountId") && fieldType =:= typeOf[String])
      }

    ReflectUtils.resetNestedFields(obj){
      case (fieldName, fieldType, fieldValue: String, ownerType) if isCustomerId(fieldName, fieldType, fieldValue, ownerType) => customerIdConverter(fieldValue)
      case (fieldName, fieldType, fieldValue: String, ownerType) if isAccountId(fieldName, fieldType, fieldValue, ownerType) => accountIdConverter(fieldValue)
    }

    obj
  }

  /**
   * convert given instance nested CustomerId to customerReference, AccountId to accountReference
   * @param obj
   * @tparam T type of instance
   * @return modified instance
   */
  def convertToReference[T](obj: T): T = {
    import code.api.util.ErrorMessages.{CustomerNotFoundByCustomerId, InvalidAccountIdFormat}
    def customerIdConverter(customerId: String): String = MappedCustomerIdMappingProvider
      .getCustomerPlainTextReference(CustomerId(customerId))
      .openOrThrowException(s"$CustomerNotFoundByCustomerId the invalid customerId is $customerId")
    def accountIdConverter(accountId: String): String = MappedAccountIdMappingProvider
      .getAccountPlainTextReference(AccountId(accountId))
      .openOrThrowException(s"$InvalidAccountIdFormat the invalid accountId is $accountId")
    convertId[T](obj, customerIdConverter, accountIdConverter)
  }

  /**
   * convert given instance nested customerReference to CustomerId, accountReference to AccountId
   * @param obj
   * @tparam T type of instance
   * @return modified instance
   */
  def convertToId[T](obj: T): T = {
    import code.api.util.ErrorMessages.{CustomerNotFoundByCustomerId, InvalidAccountIdFormat}
    def customerIdConverter(customerReference: String): String = MappedCustomerIdMappingProvider
      .getOrCreateCustomerId(customerReference)
      .map(_.value)
      .openOrThrowException(s"$CustomerNotFoundByCustomerId the invalid customerReference is $customerReference")
    def accountIdConverter(accountReference: String): String = MappedAccountIdMappingProvider
      .getOrCreateAccountId(accountReference)
      .map(_.value).openOrThrowException(s"$InvalidAccountIdFormat the invalid accountReference is $accountReference")
    convertId[T](obj, customerIdConverter, accountIdConverter)
  }
}

object RestConnector_vMar2019 extends RestConnector_vMar2019
