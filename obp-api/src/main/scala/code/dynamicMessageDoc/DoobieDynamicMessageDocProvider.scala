package code.dynamicMessageDoc

import code.api.cache.Caching
import code.api.util.{APIUtil, DoobieUtil}
import com.openbankproject.commons.util.json
import com.tesobe.CacheKeyFromArguments
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo
import net.liftweb.util.Props
import org.json4s.JsonAST.JNothing

import java.util.UUID.randomUUID
import scala.concurrent.duration.DurationInt

object DoobieDynamicMessageDocProvider extends DynamicMessageDocProvider {

  private val ttl: Int = {
    if (Props.testMode) 0
    else APIUtil.getPropsValue("dynamicMessageDoc.cache.ttl.seconds", "40").toInt
  }

  private def nn(s: String): String = if (s == null) "" else s

  private def safeParseJson(s: String) =
    Option(s).filter(_.nonEmpty).flatMap(v => tryo(json.parse(v)).toOption).getOrElse(JNothing)

  private case class Row(
    bankid: Option[String],
    dynamicmessagedocid: String,
    process: String,
    messageformat: String,
    description: String,
    outboundtopic: String,
    inboundtopic: String,
    exampleoutboundmessage: Option[String],
    exampleinboundmessage: Option[String],
    outboundavroschema: Option[String],
    inboundavroschema: Option[String],
    adapterimplementation: Option[String],
    methodbody: Option[String],
    lang: Option[String]
  )

  private def toJson(row: Row): JsonDynamicMessageDoc = JsonDynamicMessageDoc(
    bankId               = row.bankid.filter(_.nonEmpty),
    dynamicMessageDocId  = Some(row.dynamicmessagedocid),
    process              = row.process,
    messageFormat        = row.messageformat,
    description          = row.description,
    outboundTopic        = row.outboundtopic,
    inboundTopic         = row.inboundtopic,
    exampleOutboundMessage = safeParseJson(row.exampleoutboundmessage.getOrElse("")),
    exampleInboundMessage  = safeParseJson(row.exampleinboundmessage.getOrElse("")),
    outboundAvroSchema   = row.outboundavroschema.getOrElse(""),
    inboundAvroSchema    = row.inboundavroschema.getOrElse(""),
    adapterImplementation = row.adapterimplementation.getOrElse(""),
    methodBody           = row.methodbody.getOrElse(""),
    programmingLang      = row.lang.getOrElse("")
  )

  private val selectCols: Fragment =
    fr"""SELECT bankid, dynamicmessagedocid, process, messageformat, description,
               outboundtopic, inboundtopic, exampleoutboundmessage, exampleinboundmessage,
               outboundavroschema, inboundavroschema, adapterimplementation, methodbody, lang
         FROM dynamicmessagedoc"""

  override def getById(bankId: Option[String], dynamicMessageDocId: String): Box[JsonDynamicMessageDoc] = {
    val q =
      if (bankId.isEmpty) selectCols ++ fr"WHERE dynamicmessagedocid = $dynamicMessageDocId LIMIT 1"
      else                selectCols ++ fr"WHERE dynamicmessagedocid = $dynamicMessageDocId AND bankid = ${bankId.get} LIMIT 1"
    DoobieUtil.runQuery(q.query[Row].option) match {
      case Some(row) => Full(toJson(row))
      case None      => Empty
    }
  }

  override def getByProcess(bankId: Option[String], process: String): Box[JsonDynamicMessageDoc] = {
    val q =
      if (bankId.isEmpty) selectCols ++ fr"WHERE process = $process LIMIT 1"
      else                selectCols ++ fr"WHERE process = $process AND bankid = ${bankId.get} LIMIT 1"
    DoobieUtil.runQuery(q.query[Row].option) match {
      case Some(row) => Full(toJson(row))
      case None      => Empty
    }
  }

  override def getAll(bankId: Option[String]): List[JsonDynamicMessageDoc] = {
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(ttl.second) {
        val q =
          if (bankId.isEmpty) selectCols
          else                selectCols ++ fr"WHERE bankid = ${bankId.get}"
        DoobieUtil.runQuery(q.query[Row].to[List]).map(toJson)
      }
    }
  }

  override def create(bankId: Option[String], entity: JsonDynamicMessageDoc): Box[JsonDynamicMessageDoc] =
    tryo {
      val newId       = APIUtil.generateUUID()
      val bankIdVal   = bankId.filter(_.nonEmpty)
      val outbound    = json.compactRender(entity.exampleOutboundMessage)
      val inbound     = json.compactRender(entity.exampleInboundMessage)
      DoobieUtil.runQuery(
        sql"""INSERT INTO dynamicmessagedoc
              (bankid, dynamicmessagedocid, process, messageformat, description,
               outboundtopic, inboundtopic, exampleoutboundmessage, exampleinboundmessage,
               outboundavroschema, inboundavroschema, adapterimplementation, methodbody, lang)
              VALUES
              ($bankIdVal, $newId, ${nn(entity.process)}, ${nn(entity.messageFormat)}, ${nn(entity.description)},
               ${nn(entity.outboundTopic)}, ${nn(entity.inboundTopic)}, $outbound, $inbound,
               ${nn(entity.outboundAvroSchema)}, ${nn(entity.inboundAvroSchema)},
               ${nn(entity.adapterImplementation)}, ${nn(entity.methodBody)}, ${nn(entity.programmingLang)})
           """.update.run)
      entity.copy(dynamicMessageDocId = Some(newId), bankId = bankId)
    }

  override def update(bankId: Option[String], entity: JsonDynamicMessageDoc): Box[JsonDynamicMessageDoc] = {
    val id          = entity.dynamicMessageDocId.getOrElse("")
    val outbound    = json.compactRender(entity.exampleOutboundMessage)
    val inbound     = json.compactRender(entity.exampleInboundMessage)
    val whereClause =
      if (bankId.isEmpty) fr"WHERE dynamicmessagedocid = $id"
      else                fr"WHERE dynamicmessagedocid = $id AND bankid = ${bankId.get}"
    val rows = DoobieUtil.runQuery(
      (fr"""UPDATE dynamicmessagedoc SET
              process = ${nn(entity.process)},
              messageformat = ${nn(entity.messageFormat)},
              description = ${nn(entity.description)},
              outboundtopic = ${nn(entity.outboundTopic)},
              inboundtopic = ${nn(entity.inboundTopic)},
              exampleoutboundmessage = $outbound,
              exampleinboundmessage = $inbound,
              outboundavroschema = ${nn(entity.outboundAvroSchema)},
              inboundavroschema = ${nn(entity.inboundAvroSchema)},
              adapterimplementation = ${nn(entity.adapterImplementation)},
              methodbody = ${nn(entity.methodBody)},
              lang = ${nn(entity.programmingLang)}""" ++ whereClause).update.run)
    if (rows > 0) Full(entity) else Empty
  }

  override def deleteById(bankId: Option[String], id: String): Box[Boolean] =
    tryo {
      val whereClause =
        if (bankId.isEmpty) fr"WHERE dynamicmessagedocid = $id"
        else                fr"WHERE dynamicmessagedocid = $id AND bankid = ${bankId.get}"
      DoobieUtil.runQuery((fr"DELETE FROM dynamicmessagedoc" ++ whereClause).update.run) >= 0
    }
}
