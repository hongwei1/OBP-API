package code.bankconnectors.grpc

/*
Open Bank Project - API
Copyright (C) 2011-2017, TESOBE GmbH

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
TESOBE GmbH
Osloerstrasse 16/17
Berlin 13359, Germany
*/

import code.api.ResourceDocs1_4_0.MessageDocsSwaggerDefinitions
import code.api.util.APIUtil.{AdapterImplementation, MessageDoc}
import code.api.util.CallContext
import code.api.util.ExampleValue._
import code.bankconnectors._
import code.bankconnectors.grpc.api._
import code.util.Helper.MdcLoggable
import com.google.protobuf.empty.Empty
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.dto._
import com.openbankproject.commons.model._
import io.grpc.{ManagedChannel, ManagedChannelBuilder}
import net.liftweb.common._

import scala.collection.mutable.ArrayBuffer
import scala.concurrent.Future
import scala.language.postfixOps

trait GrpcConnector_vFeb2024 extends Connector with MdcLoggable {

  implicit override val nameOfConnector = GrpcConnector_vFeb2024.toString

  // "Versioning" of the messages sent by this or similar connector works like this:
  // Use Case Classes (e.g. KafkaInbound... KafkaOutbound... as below to describe the message structures.
  // Each connector has a separate file like this one.
  // Once the message format is STABLE, freeze the key/value pair names there. For now, new keys may be added but none modified.
  // If we want to add a new message format, create a new file e.g. March2017_messages.scala
  // Then add a suffix to the connector value i.e. instead of kafka we might have kafka_march_2017.
  // Then in this file, populate the different case classes depending on the connector name and send to Kafka
  val messageFormat: String = "Feb2024"

  override val messageDocs = ArrayBuffer[MessageDoc]()

  val authInfoExample = AuthInfo(userId = "userId", username = "username", cbsToken = "cbsToken")
  val errorCodeExample = "INTERNAL-OBP-ADAPTER-6001: ..."

  private val channelBuilder = ManagedChannelBuilder.forAddress("localhost", 8085)
    .usePlaintext()
    .asInstanceOf[ManagedChannelBuilder[_]]
  val channel: ManagedChannel = channelBuilder.build()

  private val obpService: ObpServiceGrpc.ObpServiceBlockingStub = ObpServiceGrpc.blockingStub(channel)


  messageDocs += getBanksDoc

  def getBanksDoc = MessageDoc(
    process = "obp.getBanks",
    messageFormat = messageFormat,
    description = "Get Banks",
    outboundTopic = None,
    inboundTopic = None,
    exampleOutboundMessage = (
      OutBoundGetBanks(MessageDocsSwaggerDefinitions.outboundAdapterCallContext)
      ),
    exampleInboundMessage = (
      InBoundGetBanks(inboundAdapterCallContext = MessageDocsSwaggerDefinitions.inboundAdapterCallContext,
        status = MessageDocsSwaggerDefinitions.inboundStatus,
        data = List(BankCommons(bankId = BankId(bankIdExample.value),
          shortName = bankShortNameExample.value,
          fullName = bankFullNameExample.value,
          logoUrl = bankLogoUrlExample.value,
          websiteUrl = bankWebsiteUrlExample.value,
          bankRoutingScheme = bankRoutingSchemeExample.value,
          bankRoutingAddress = bankRoutingAddressExample.value,
          swiftBic = bankSwiftBicExample.value,
          nationalIdentifier = bankNationalIdentifierExample.value)))
      ),
    adapterImplementation = Some(AdapterImplementation("- Core", 1))
  )

  override def getBanks(callContext: Option[CallContext]): Future[Box[(List[Bank], Option[CallContext])]] = {
    import com.openbankproject.commons.dto.{OutBoundGetBanks => OutBound}
    val req = OutBound(callContext.map(_.toOutboundAdapterCallContext).orNull)
    val banksResponse: BanksJson400Grpc = obpService.getBanks(Empty.defaultInstance)
    val banks: List[BankJson400Grpc] = banksResponse.banks.toList
    
    val bankCommons = banks.map(bank =>
      BankCommons(
        bankId = BankId(bank.id),
        shortName = bank.shortName,
        fullName = bank.fullName,
        logoUrl = bank.logo,
        websiteUrl = bank.website,
        bankRoutingScheme = bank.bankRoutings.map(_.scheme).headOption.getOrElse(""),
        bankRoutingAddress = bank.bankRoutings.map(_.address).headOption.getOrElse(""),
        swiftBic = "",
        nationalIdentifier = ""
      )
    )
    Future{Full(bankCommons,callContext)}
  }

}
object GrpcConnector_vFeb2024 extends GrpcConnector_vFeb2024


