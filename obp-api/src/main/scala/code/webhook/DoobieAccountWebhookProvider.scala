package code.webhook

import code.api.util.{APIUtil, DoobieUtil, OBPAccountId, OBPBankId, OBPLimit, OBPOffset, OBPQueryParam, OBPUserId}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import java.sql.Timestamp
import scala.collection.immutable.List
import scala.concurrent.Future

object DoobieAccountWebhookProvider extends AccountWebhookProvider {

  // Table "mappedaccountwebhook". The trait returns the concrete AccountWebhook (Lift class), so the
  // Lift class is reused as an in-memory return holder (no second AccountWebhook impl → no equality regression).
  private case class AwRow(
    maccountwebhookid: Option[String],
    mbankid: Option[String],
    maccountid: Option[String],
    mtriggername: Option[String],
    murl: Option[String],
    mhttpmethod: Option[String],
    mhttpprotocol: Option[String],
    mcreatedbyuserid: Option[String],
    misactive: Option[Boolean]
  )

  private def nn(s: String): String = if (s == null) "" else s

  private def toEntity(row: AwRow): AccountWebhook =
    MappedAccountWebhook.create
      .mAccountWebhookId(row.maccountwebhookid.getOrElse(""))
      .mBankId(row.mbankid.getOrElse(""))
      .mAccountId(row.maccountid.getOrElse(""))
      .mTriggerName(row.mtriggername.getOrElse(""))
      .mUrl(row.murl.getOrElse(""))
      .mHttpMethod(row.mhttpmethod.getOrElse(""))
      .mHttpProtocol(row.mhttpprotocol.getOrElse(""))
      .mCreatedByUserId(row.mcreatedbyuserid.getOrElse(""))
      .mIsActive(row.misactive.getOrElse(false))

  private val selectCols: Fragment =
    fr"""SELECT maccountwebhookid, mbankid, maccountid, mtriggername, murl, mhttpmethod, mhttpprotocol, mcreatedbyuserid, misactive
         FROM mappedaccountwebhook"""

  private def findRow(where: Fragment): Box[AwRow] =
    DoobieUtil.runQuery((selectCols ++ where ++ fr"LIMIT 1").query[AwRow].option) match {
      case Some(r) => Full(r)
      case None    => Empty
    }

  private def runList(where: Fragment): List[AwRow] =
    DoobieUtil.runQuery((selectCols ++ where).query[AwRow].to[List])

  override def getAccountWebhookByIdFuture(accountWebhookId: String): Future[Box[AccountWebhook]] =
    Future {
      findRow(fr"WHERE maccountwebhookid = ${nn(accountWebhookId)}").map(toEntity)
    }

  override def getAccountWebhooksByUserIdFuture(userId: String): Future[Box[List[AccountWebhook]]] =
    Future {
      Full(runList(fr"WHERE mcreatedbyuserid = ${nn(userId)} ORDER BY updatedat DESC").map(toEntity))
    }

  // Mirrors the Mapper getOptionalParams: limit / offset plus optional userId / bankId / accountId filters (no ORDER BY).
  override def getAccountWebhooksFuture(queryParams: List[OBPQueryParam]): Future[Box[List[AccountWebhook]]] =
    Future {
      val whereFrags: List[Fragment] =
        queryParams.collect { case OBPUserId(value) => fr"mcreatedbyuserid = ${nn(value)}" } :::
        queryParams.collect { case OBPBankId(value) => fr"mbankid = ${nn(value)}" } :::
        queryParams.collect { case OBPAccountId(value) => fr"maccountid = ${nn(value)}" }
      val whereClause =
        if (whereFrags.isEmpty) Fragment.empty
        else fr"WHERE" ++ whereFrags.reduceLeft((a, b) => a ++ fr"AND" ++ b)
      val limitClause = queryParams.collectFirst { case OBPLimit(value) => fr"LIMIT $value" }.getOrElse(Fragment.empty)
      val offsetClause = queryParams.collectFirst { case OBPOffset(value) => fr"OFFSET $value" }.getOrElse(Fragment.empty)
      Full(runList(whereClause ++ limitClause ++ offsetClause).map(toEntity))
    }

  override def createAccountWebhookFuture(bankId: String,
                                          accountId: String,
                                          userId: String,
                                          triggerName: String,
                                          url: String,
                                          httpMethod: String,
                                          httpProtocol: String,
                                          isActive: Boolean
                                         ): Future[Box[AccountWebhook]] =
    Future {
      tryo {
        val newId = APIUtil.generateUUID()
        val now = new Timestamp(System.currentTimeMillis())
        DoobieUtil.runQuery(
          sql"""INSERT INTO mappedaccountwebhook
                  (maccountwebhookid, mbankid, maccountid, mtriggername, murl, mhttpmethod, mhttpprotocol,
                   mcreatedbyuserid, misactive, createdat, updatedat)
                VALUES ($newId, ${nn(bankId)}, ${nn(accountId)}, ${nn(triggerName)}, ${nn(url)}, ${nn(httpMethod)},
                        ${nn(httpProtocol)}, ${nn(userId)}, $isActive, $now, $now)""".update.run)
        toEntity(AwRow(Some(newId), Some(nn(bankId)), Some(nn(accountId)), Some(nn(triggerName)), Some(nn(url)),
          Some(nn(httpMethod)), Some(nn(httpProtocol)), Some(nn(userId)), Some(isActive)))
      }
    }

  override def updateAccountWebhookFuture(accountWebhookId: String,
                                          isActive: Boolean
                                         ): Future[Box[AccountWebhook]] =
    Future {
      findRow(fr"WHERE maccountwebhookid = ${nn(accountWebhookId)}") match {
        case Full(row) =>
          tryo {
            val now = new Timestamp(System.currentTimeMillis())
            DoobieUtil.runQuery(
              sql"""UPDATE mappedaccountwebhook SET misactive = $isActive, updatedat = $now
                    WHERE maccountwebhookid = ${nn(accountWebhookId)}""".update.run)
            toEntity(row.copy(misactive = Some(isActive)))
          }
        case other => other.map(toEntity)
      }
    }

}
