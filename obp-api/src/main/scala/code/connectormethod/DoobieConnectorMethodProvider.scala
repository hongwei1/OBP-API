package code.connectormethod

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

object DoobieConnectorMethodProvider extends ConnectorMethodProvider {

  private val ttl: Int = {
    if (Props.testMode) 0
    else APIUtil.getPropsValue("connectorMethod.cache.ttl.seconds", "40").toInt
  }

  private def nn(s: String): String = if (s == null) "" else s

  private def row2json(id: String, name: String, body: String, lang: String): JsonConnectorMethod =
    JsonConnectorMethod(Some(id), name, body, Option(lang).filter(_.nonEmpty).getOrElse("Scala"))

  private val selectCols: Fragment =
    fr"SELECT connectormethodid, methodname, methodbody, lang FROM connectormethod"

  override def getById(connectorMethodId: String): Box[JsonConnectorMethod] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE connectormethodid = ${nn(connectorMethodId)} LIMIT 1")
        .query[(String, String, String, String)].option) match {
      case Some((id, name, body, lang)) => Full(row2json(id, name, body, lang))
      case None                         => Empty
    }

  override def getByMethodNameWithoutCache(methodName: String): Box[JsonConnectorMethod] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE methodname = ${nn(methodName)} LIMIT 1")
        .query[(String, String, String, String)].option) match {
      case Some((id, name, body, lang)) => Full(row2json(id, name, body, lang))
      case None                         => Empty
    }

  override def getByMethodNameWithCache(methodName: String): Box[JsonConnectorMethod] = {
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(ttl.second) {
        getByMethodNameWithoutCache(methodName)
      }
    }
  }

  override def getAll(): List[JsonConnectorMethod] = {
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(ttl.second) {
        DoobieUtil.runQuery(selectCols.query[(String, String, String, String)].to[List])
          .map { case (id, name, body, lang) => row2json(id, name, body, lang) }
      }
    }
  }

  override def create(entity: JsonConnectorMethod): Box[JsonConnectorMethod] =
    tryo {
      val newId = APIUtil.generateUUID()
      val lang = Option(entity.programmingLang).filter(_.nonEmpty).getOrElse("Scala")
      DoobieUtil.runQuery(
        sql"""INSERT INTO connectormethod (connectormethodid, methodname, methodbody, lang)
              VALUES ($newId, ${nn(entity.methodName)}, ${nn(entity.methodBody)}, $lang)""".update.run)
      JsonConnectorMethod(Some(newId), entity.methodName, entity.methodBody, lang)
    }

  override def update(connectorMethodId: String, connectorMethodBody: String,
                      programmingLang: String): Box[JsonConnectorMethod] = {
    val lang = Option(programmingLang).filter(_.nonEmpty).getOrElse("Scala")
    val rows = DoobieUtil.runQuery(
      sql"""UPDATE connectormethod SET methodbody = ${nn(connectorMethodBody)}, lang = $lang
            WHERE connectormethodid = ${nn(connectorMethodId)}""".update.run)
    if (rows > 0) getById(connectorMethodId) else Empty
  }

  override def deleteById(connectorMethodId: String): Box[Boolean] =
    tryo {
      DoobieUtil.runQuery(
        sql"DELETE FROM connectormethod WHERE connectormethodid = ${nn(connectorMethodId)}".update.run) >= 0
    }
}
