package code.validation

import code.api.cache.Caching
import code.api.util.{APIUtil, DoobieUtil}
import com.tesobe.CacheKeyFromArguments
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo
import net.liftweb.util.Props

import java.util.UUID.randomUUID
import scala.concurrent.duration.DurationInt

object DoobieJsonSchemaValidationProvider extends JsonSchemaValidationProvider {

  private val ttl: Int = {
    if (Props.testMode) 0
    else APIUtil.getPropsValue("validation.cache.ttl.seconds", "34").toInt
  }

  private def nn(s: String): String = if (s == null) "" else s

  override def getByOperationId(operationId: String): Box[JsonValidation] = {
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(ttl.second) {
        DoobieUtil.runQuery(
          fr"SELECT operationid, jsonschema FROM jsonschemavalidation WHERE operationid = ${nn(operationId)} LIMIT 1"
            .query[(String, String)].option) match {
          case Some((opId, schema)) => Full(JsonValidation(opId, schema))
          case None                 => Empty
        }
      }
    }
  }

  override def getAll(): List[JsonValidation] =
    DoobieUtil.runQuery(
      fr"SELECT operationid, jsonschema FROM jsonschemavalidation".query[(String, String)].to[List])
      .map { case (opId, schema) => JsonValidation(opId, schema) }

  override def create(jsonValidation: JsonValidation): Box[JsonValidation] =
    tryo {
      DoobieUtil.runQuery(
        sql"""INSERT INTO jsonschemavalidation (operationid, jsonschema)
              VALUES (${nn(jsonValidation.operationId)}, ${nn(jsonValidation.jsonSchema)})""".update.run)
      jsonValidation
    }

  override def update(jsonValidation: JsonValidation): Box[JsonValidation] = {
    val rows = DoobieUtil.runQuery(
      sql"""UPDATE jsonschemavalidation SET jsonschema = ${nn(jsonValidation.jsonSchema)}
            WHERE operationid = ${nn(jsonValidation.operationId)}""".update.run)
    if (rows > 0) Full(jsonValidation) else Empty
  }

  override def deleteByOperationId(operationId: String): Box[Boolean] =
    tryo {
      DoobieUtil.runQuery(
        sql"DELETE FROM jsonschemavalidation WHERE operationid = ${nn(operationId)}".update.run) >= 0
    }
}
