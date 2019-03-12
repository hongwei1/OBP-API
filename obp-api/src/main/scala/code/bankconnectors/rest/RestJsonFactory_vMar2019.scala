package code.bankconnectors.rest

import code.bankconnectors.vMar2017._
import code.kafka.Topics._
import scala.collection.immutable.List

/**
  * case classes used to define topics, these are outbound kafka messages
  */

case class OutboundGetAdapterInfo(date: String) extends TopicTrait
case class OutboundGetBanks(authInfo: AuthInfo) extends TopicTrait
case class OutboundGetBank(authInfo: AuthInfo, bankId: String) extends TopicTrait


/**
  * case classes used in Kafka message, these are InBound Kafka messages
  */

//AdapterInfo has no AuthInfo, because it just get data from Adapter, no need for AuthInfo
case class InboundAdapterInfo(data: InboundAdapterInfoInternal)
case class InboundGetBanks(authInfo: AuthInfo, status: Status,data: List[InboundBank])
case class InboundGetBank(authInfo: AuthInfo, status: Status, data: InboundBank)


////////////////////////////////////////////////////////////////////////////////////////////////////////////
////////////////////////////////////////////////////////////////////////////////////////////////////////////
// These are case classes, used in internal message mapping

case class Status(
  errorCode: String,
  backendMessages: List[InboundStatusMessage]
)

case class AuthInfo(
  userId: String = "",
  username: String = "",
  cbsToken: String = "",
  isFirst: Boolean = true,
  correlationId: String = ""
)


