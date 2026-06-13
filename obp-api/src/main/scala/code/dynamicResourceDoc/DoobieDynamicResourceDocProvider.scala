package code.dynamicResourceDoc

import code.api.cache.Caching
import code.api.util.{APIUtil, DoobieUtil}
import com.openbankproject.commons.util.json
import com.tesobe.CacheKeyFromArguments
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo
import net.liftweb.util.Props
import org.apache.commons.lang3.StringUtils

import java.util.UUID.randomUUID
import scala.concurrent.duration.DurationInt

object DoobieDynamicResourceDocProvider extends DynamicResourceDocProvider {

  private val ttl: Int = {
    if (Props.testMode) 0
    else APIUtil.getPropsValue("dynamicResourceDoc.cache.ttl.seconds", "40").toInt
  }

  private def nn(s: String): String = if (s == null) "" else s

  private case class Row(
    bankid: Option[String],
    dynamicresourcedocid: String,
    partialfunctionname: String,
    requestverb: String,
    requesturl: String,
    summary: String,
    description: String,
    examplerequestbody: Option[String],
    successresponsebody: Option[String],
    errorresponsebodies: String,
    tags: String,
    roles: String,
    methodbody: String
  )

  private def toJson(row: Row): JsonDynamicResourceDoc = JsonDynamicResourceDoc(
    bankId               = row.bankid.filter(_.nonEmpty),
    dynamicResourceDocId = Some(row.dynamicresourcedocid),
    methodBody           = row.methodbody,
    partialFunctionName  = row.partialfunctionname,
    requestVerb          = row.requestverb,
    requestUrl           = row.requesturl,
    summary              = row.summary,
    description          = row.description,
    exampleRequestBody   = row.examplerequestbody.filter(StringUtils.isNotBlank(_)).flatMap(v => tryo(json.parse(v)).toOption),
    successResponseBody  = row.successresponsebody.filter(StringUtils.isNotBlank(_)).flatMap(v => tryo(json.parse(v)).toOption),
    errorResponseBodies  = row.errorresponsebodies,
    tags                 = row.tags,
    roles                = row.roles
  )

  private val selectCols: Fragment =
    fr"""SELECT bankid, dynamicresourcedocid, partialfunctionname, requestverb, requesturl,
               summary, description, examplerequestbody, successresponsebody,
               errorresponsebodies, tags, roles, methodbody
         FROM dynamicresourcedoc"""

  override def getById(bankId: Option[String], dynamicResourceDocId: String): Box[JsonDynamicResourceDoc] = {
    val q =
      if (bankId.isEmpty) selectCols ++ fr"WHERE dynamicresourcedocid = $dynamicResourceDocId LIMIT 1"
      else                selectCols ++ fr"WHERE dynamicresourcedocid = $dynamicResourceDocId AND bankid = ${bankId.get} LIMIT 1"
    DoobieUtil.runQuery(q.query[Row].option) match {
      case Some(row) => Full(toJson(row))
      case None      => Empty
    }
  }

  override def getByVerbAndUrl(bankId: Option[String], requestVerb: String, requestUrl: String): Box[JsonDynamicResourceDoc] = {
    val q =
      if (bankId.isEmpty) selectCols ++ fr"WHERE requestverb = $requestVerb AND requesturl = $requestUrl LIMIT 1"
      else                selectCols ++ fr"WHERE requestverb = $requestVerb AND requesturl = $requestUrl AND bankid = ${bankId.get} LIMIT 1"
    DoobieUtil.runQuery(q.query[Row].option) match {
      case Some(row) => Full(toJson(row))
      case None      => Empty
    }
  }

  override def getAllAndConvert[T: Manifest](bankId: Option[String], transform: JsonDynamicResourceDoc => T): List[T] = {
    val cacheKey = (bankId.toString + transform.toString()).intern()
    Caching.memoizeSyncWithImMemory(Some(cacheKey))(ttl.seconds) {
      val q =
        if (bankId.isEmpty) selectCols
        else                selectCols ++ fr"WHERE bankid = ${bankId.get}"
      DoobieUtil.runQuery(q.query[Row].to[List]).map(row => transform(toJson(row)))
    }
  }

  override def create(bankId: Option[String], entity: JsonDynamicResourceDoc): Box[JsonDynamicResourceDoc] =
    tryo {
      val newId        = APIUtil.generateUUID()
      val bankIdVal    = bankId.filter(_.nonEmpty)
      val requestBody  = entity.exampleRequestBody.map(json.compactRender)
      val responseBody = entity.successResponseBody.map(json.compactRender)
      DoobieUtil.runQuery(
        sql"""INSERT INTO dynamicresourcedoc
              (bankid, dynamicresourcedocid, partialfunctionname, requestverb, requesturl,
               summary, description, examplerequestbody, successresponsebody,
               errorresponsebodies, tags, roles, methodbody)
              VALUES
              ($bankIdVal, $newId, ${nn(entity.partialFunctionName)}, ${nn(entity.requestVerb)}, ${nn(entity.requestUrl)},
               ${nn(entity.summary)}, ${nn(entity.description)}, $requestBody, $responseBody,
               ${nn(entity.errorResponseBodies)}, ${nn(entity.tags)}, ${nn(entity.roles)}, ${nn(entity.methodBody)})
           """.update.run)
      entity.copy(dynamicResourceDocId = Some(newId), bankId = bankId)
    }

  override def update(bankId: Option[String], entity: JsonDynamicResourceDoc): Box[JsonDynamicResourceDoc] = {
    val id           = entity.dynamicResourceDocId.getOrElse("")
    val requestBody  = entity.exampleRequestBody.map(json.compactRender)
    val responseBody = entity.successResponseBody.map(json.compactRender)
    val rows = DoobieUtil.runQuery(
      sql"""UPDATE dynamicresourcedoc SET
              partialfunctionname = ${nn(entity.partialFunctionName)},
              requestverb = ${nn(entity.requestVerb)},
              requesturl = ${nn(entity.requestUrl)},
              summary = ${nn(entity.summary)},
              description = ${nn(entity.description)},
              examplerequestbody = $requestBody,
              successresponsebody = $responseBody,
              errorresponsebodies = ${nn(entity.errorResponseBodies)},
              tags = ${nn(entity.tags)},
              roles = ${nn(entity.roles)},
              methodbody = ${nn(entity.methodBody)}
            WHERE dynamicresourcedocid = $id
           """.update.run)
    if (rows > 0) Full(entity) else Empty
  }

  override def deleteById(bankId: Option[String], id: String): Box[Boolean] =
    tryo {
      val whereClause =
        if (bankId.isEmpty) fr"WHERE dynamicresourcedocid = $id"
        else                fr"WHERE dynamicresourcedocid = $id AND bankid = ${bankId.get}"
      DoobieUtil.runQuery((fr"DELETE FROM dynamicresourcedoc" ++ whereClause).update.run) >= 0
    }
}
