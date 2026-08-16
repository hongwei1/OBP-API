package code.authtypevalidation

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

object DoobieAuthTypeValidationProvider extends AuthenticationTypeValidationProvider {

  private val ttl: Int = {
    if (Props.testMode) 0
    else APIUtil.getPropsValue("authTypeValidation.cache.ttl.seconds", "36").toInt
  }

  private def nn(s: String): String = if (s == null) "" else s

  private def row2json(opId: String, authTypesStr: String): JsonAuthTypeValidation =
    JsonAuthTypeValidation(opId, authTypesStr)

  override def getByOperationId(operationId: String): Box[JsonAuthTypeValidation] = {
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(ttl.second) {
        DoobieUtil.runQuery(
          fr"SELECT operationid, allowedauthtypes FROM authenticationtypevalidation WHERE operationid = ${nn(operationId)} LIMIT 1"
            .query[(String, String)].option) match {
          case Some((opId, authTypes)) => Full(row2json(opId, authTypes))
          case None                    => Empty
        }
      }
    }
  }

  override def getAll(): List[JsonAuthTypeValidation] =
    DoobieUtil.runQuery(
      fr"SELECT operationid, allowedauthtypes FROM authenticationtypevalidation"
        .query[(String, String)].to[List])
      .map { case (opId, authTypes) => row2json(opId, authTypes) }

  override def create(jsonValidation: JsonAuthTypeValidation): Box[JsonAuthTypeValidation] =
    tryo {
      DoobieUtil.runQuery(
        sql"""INSERT INTO authenticationtypevalidation (operationid, allowedauthtypes)
              VALUES (${nn(jsonValidation.operationId)}, ${jsonValidation.authTypes.mkString(",")})""".update.run)
      jsonValidation
    }

  override def update(jsonValidation: JsonAuthTypeValidation): Box[JsonAuthTypeValidation] = {
    val rows = DoobieUtil.runQuery(
      sql"""UPDATE authenticationtypevalidation SET allowedauthtypes = ${jsonValidation.authTypes.mkString(",")}
            WHERE operationid = ${nn(jsonValidation.operationId)}""".update.run)
    if (rows > 0) Full(jsonValidation) else Empty
  }

  override def deleteByOperationId(operationId: String): Box[Boolean] =
    tryo {
      DoobieUtil.runQuery(
        sql"DELETE FROM authenticationtypevalidation WHERE operationid = ${nn(operationId)}".update.run) >= 0
    }
}
