package code.DynamicEndpoint

import java.util.UUID.randomUUID

import code.api.cache.Caching
import code.api.dynamic.endpoint.helper.DynamicEndpointHelper
import code.api.util.{APIUtil, DoobieUtil}
import com.tesobe.CacheKeyFromArguments
import doobie._
import doobie.implicits._
import net.liftweb.common.Box
import net.liftweb.util.Helpers.tryo
import net.liftweb.util.Props

import scala.concurrent.duration.DurationInt

object DoobieDynamicEndpointProvider extends DynamicEndpointProvider {

  val dynamicEndpointTTL: Int = {
    if (Props.testMode) 0
    else //Better set this to 0, we maybe create multiple endpoints, when we create new ones.
      APIUtil.getPropsValue(s"dynamicEndpoint.cache.ttl.seconds", "0").toInt
  }

  private case class DynamicEndpointRow(
    dynamicendpointid: Option[String],
    swaggerstring: Option[String],
    userid: Option[String],
    bankid: Option[String]
  )

  /**
   * Reproduce the Mapped entity accessors exactly:
   *   - dynamicEndpointId: Option(DynamicEndpointId.get) (always Some for a persisted row, MappedUUID is never null)
   *   - swaggerString: SwaggerString.get
   *   - userId: UserId.get
   *   - bankId: None when BankId is null or empty, otherwise Some(BankId)
   */
  private def toCommons(row: DynamicEndpointRow): DynamicEndpointT =
    DynamicEndpointCommons(
      dynamicEndpointId = Some(row.dynamicendpointid.getOrElse("")),
      swaggerString = row.swaggerstring.getOrElse(""),
      userId = row.userid.getOrElse(""),
      bankId = row.bankid match {
        case None                 => None
        case Some(b) if b.isEmpty => None
        case Some(b)              => Some(b)
      }
    )

  private def nn(s: String): String = if (s == null) "" else s

  private val selectCols: Fragment =
    fr"SELECT dynamicendpointid, swaggerstring, userid, bankid FROM dynamicendpoint"

  private def findOne(query: Fragment): Option[DynamicEndpointRow] =
    DoobieUtil.runQuery(query.query[DynamicEndpointRow].option)

  override def create(bankId: Option[String], userId: String, swaggerString: String): Box[DynamicEndpointT] = {
    tryo {
      val newId = randomUUID().toString
      // Mirror Lift's BankId(bankId.getOrElse(null)): a null bankId is stored as null.
      val bankIdValue: Option[String] = bankId
      val now = new java.sql.Timestamp(System.currentTimeMillis())
      DoobieUtil.runQuery(
        sql"""INSERT INTO dynamicendpoint
                (dynamicendpointid, swaggerstring, userid, bankid, createdat, updatedat)
              VALUES ($newId, ${nn(swaggerString)}, ${nn(userId)}, $bankIdValue, $now, $now)""".update.run)
      toCommons(DynamicEndpointRow(Some(newId), Some(nn(swaggerString)), Some(nn(userId)), bankIdValue))
    }
  }

  override def update(bankId: Option[String], dynamicEndpointId: String, swaggerString: String): Box[DynamicEndpointT] = {
    val whereFr =
      if (bankId.isEmpty)
        fr"WHERE dynamicendpointid = ${nn(dynamicEndpointId)}"
      else
        fr"WHERE dynamicendpointid = ${nn(dynamicEndpointId)} AND bankid = ${nn(bankId.getOrElse(""))}"
    net.liftweb.common.Box.option2Box(findOne(selectCols ++ whereFr)).map { row =>
      val now = new java.sql.Timestamp(System.currentTimeMillis())
      DoobieUtil.runQuery(
        (fr"UPDATE dynamicendpoint SET swaggerstring = ${nn(swaggerString)}, updatedat = $now " ++ whereFr).update.run)
      toCommons(row.copy(swaggerstring = Some(nn(swaggerString))))
    }
  }

  override def updateHost(bankId: Option[String], dynamicEndpointId: String, hostString: String): Box[DynamicEndpointT] = {
    val whereFr =
      if (bankId.isEmpty)
        fr"WHERE dynamicendpointid = ${nn(dynamicEndpointId)}"
      else
        fr"WHERE dynamicendpointid = ${nn(dynamicEndpointId)} AND bankid = ${nn(bankId.getOrElse(""))}"
    net.liftweb.common.Box.option2Box(findOne(selectCols ++ whereFr)).map { row =>
      val updatedHost = DynamicEndpointHelper.changeOpenApiVersionHost(row.swaggerstring.getOrElse(""), hostString)
      val now = new java.sql.Timestamp(System.currentTimeMillis())
      DoobieUtil.runQuery(
        (fr"UPDATE dynamicendpoint SET swaggerstring = ${nn(updatedHost)}, updatedat = $now " ++ whereFr).update.run)
      toCommons(row.copy(swaggerstring = Some(nn(updatedHost))))
    }
  }

  override def get(bankId: Option[String], dynamicEndpointId: String): Box[DynamicEndpointT] = {
    val whereFr =
      if (bankId.isEmpty)
        fr"WHERE dynamicendpointid = ${nn(dynamicEndpointId)}"
      else
        fr"WHERE dynamicendpointid = ${nn(dynamicEndpointId)} AND bankid = ${nn(bankId.getOrElse(""))}"
    net.liftweb.common.Box.option2Box(findOne(selectCols ++ whereFr).map(toCommons))
  }

  override def getAll(bankId: Option[String]): List[DynamicEndpointT] = {
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(dynamicEndpointTTL.second) {
        val whereFr =
          if (bankId.isEmpty)
            Fragment.empty
          else
            fr"WHERE bankid = ${nn(bankId.getOrElse(""))}"
        DoobieUtil.runQuery((selectCols ++ whereFr).query[DynamicEndpointRow].to[List]).map(toCommons)
      }
    }
  }

  override def getDynamicEndpointsByUserId(userId: String): List[DynamicEndpointT] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE userid = ${nn(userId)}").query[DynamicEndpointRow].to[List]).map(toCommons)

  override def delete(bankId: Option[String], dynamicEndpointId: String): Boolean = {
    val whereFr =
      if (bankId.isEmpty)
        fr"WHERE dynamicendpointid = ${nn(dynamicEndpointId)}"
      else
        fr"WHERE dynamicendpointid = ${nn(dynamicEndpointId)} AND bankid = ${nn(bankId.getOrElse(""))}"
    DoobieUtil.runQuery((fr"DELETE FROM dynamicendpoint " ++ whereFr).update.run) >= 0
  }
}
