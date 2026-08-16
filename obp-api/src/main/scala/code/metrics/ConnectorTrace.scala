package code.metrics

import java.sql.Timestamp
import java.util.Date

import code.api.util._
import code.api.util.DoobieUtil
import doobie._
import doobie.implicits._
import net.liftweb.mapper._

/**
 * Result row read from the `connector_trace` table via Doobie.
 *
 * Column order matches the SELECT in [[ConnectorTraceProvider.getAllConnectorTraces]].
 */
case class ConnectorTraceRow(
  id: Long,
  correlationId: String,
  connectorName: String,
  functionName: String,
  bankId: String,
  outboundMessage: String,
  inboundMessage: String,
  date: Timestamp,
  duration: Long,
  isSuccessful: Boolean,
  userId: String,
  httpVerb: String,
  url: String
)

class ConnectorTrace extends LongKeyedMapper[ConnectorTrace] with IdPK {
  override def getSingleton = ConnectorTrace

  object correlationId extends MappedString(this, 256)
  object connectorName extends MappedString(this, 64)
  object functionName extends MappedString(this, 128)
  object bankId extends MappedString(this, 256)
  object outboundMessage extends MappedText(this)
  object inboundMessage extends MappedText(this)
  object date extends MappedDateTime(this)
  object duration extends MappedLong(this)
  object isSuccessful extends MappedBoolean(this)
  object userId extends MappedString(this, 256)
  object httpVerb extends MappedString(this, 16)
  object url extends MappedString(this, 2000)
}

object ConnectorTrace extends ConnectorTrace with LongKeyedMetaMapper[ConnectorTrace] {
  override def dbTableName = "connector_trace"
  override def dbIndexes = Index(correlationId) :: Index(connectorName) :: Index(functionName) ::
    Index(date) :: Index(userId) :: Index(bankId) :: super.dbIndexes
}

object ConnectorTraceProvider {

  def saveConnectorTrace(
    correlationId: String,
    connectorName: String,
    functionName: String,
    bankId: String,
    outboundMessage: String,
    inboundMessage: String,
    date: Date,
    duration: Long,
    isSuccessful: Boolean,
    userId: String,
    httpVerb: String,
    url: String
  ): Unit = {
    val dateTs = new Timestamp(date.getTime)
    DoobieUtil.runQuery(
      sql"""INSERT INTO connector_trace
              (correlationid, connectorname, functionname, bankid, outboundmessage, inboundmessage,
               date_c, duration, issuccessful, userid, httpverb, url)
            VALUES
              ($correlationId, $connectorName, $functionName, $bankId, $outboundMessage, $inboundMessage,
               $dateTs, $duration, $isSuccessful, $userId, $httpVerb, $url)"""
        .update.run
    )
  }

  def getAllConnectorTraces(queryParams: List[OBPQueryParam]): List[ConnectorTraceRow] = {
    val limit         = queryParams.collectFirst { case OBPLimit(v)        => v }
    val offset        = queryParams.collectFirst { case OBPOffset(v)       => v }
    val fromDate      = queryParams.collectFirst { case OBPFromDate(v)     => new Timestamp(v.getTime) }
    val toDate        = queryParams.collectFirst { case OBPToDate(v)       => new Timestamp(v.getTime) }
    val correlationId = queryParams.collectFirst { case OBPCorrelationId(v) => v }
    val functionName  = queryParams.collectFirst { case OBPFunctionName(v) => v }
    val connectorName = queryParams.collectFirst { case OBPConnectorName(v) => v }
    val userId        = queryParams.collectFirst { case OBPUserId(v)       => v }
    val bankId        = queryParams.collectFirst { case OBPBankId(v)       => v }
    val orderDir      = queryParams.collectFirst { case OBPOrdering(_, dir) => dir }

    val conditions = List(
      fromDate     .map(d => fr"date_c >= $d"),
      toDate       .map(d => fr"date_c <= $d"),
      correlationId.map(v => fr"correlationid = $v"),
      functionName .map(v => fr"functionname = $v"),
      connectorName.map(v => fr"connectorname = $v"),
      userId       .map(v => fr"userid = $v"),
      bankId       .map(v => fr"bankid = $v")
    ).flatten
    val whereQ = if (conditions.isEmpty) fr"" else fr"WHERE" ++ conditions.reduce(_ ++ fr" AND " ++ _)
    val orderQ = orderDir.map {
      case OBPAscending  => fr"ORDER BY date_c ASC"
      case OBPDescending => fr"ORDER BY date_c DESC"
    }.getOrElse(fr"")
    val limitQ  = limit .map(l => fr"LIMIT $l") .getOrElse(fr"")
    val offsetQ = offset.map(o => fr"OFFSET $o").getOrElse(fr"")

    DoobieUtil.runQuery(
      (fr"""SELECT id, correlationid, connectorname, functionname, bankid, outboundmessage, inboundmessage,
                   date_c, duration, issuccessful, userid, httpverb, url
            FROM connector_trace""" ++ whereQ ++ orderQ ++ limitQ ++ offsetQ)
        .query[ConnectorTraceRow].to[List]
    )
  }
}
