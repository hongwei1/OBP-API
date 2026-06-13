package code.webhook

import code.api.util.{APIUtil, DoobieUtil, OBPLimit, OBPOffset, OBPQueryParam, OBPUserId}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}

import java.sql.Timestamp
import scala.collection.immutable.List
import scala.concurrent.Future

object DoobieSystemAccountNotificationWebhookProvider extends SystemAccountNotificationWebhookProvider {

  // Table "systemaccountnotificationwebhook": id, createdbyuserid, webhookid, triggername,
  // url, httpmethod, httpprotocol, createdat, updatedat (CreatedUpdated trait).
  private case class WebhookRow(
    webhookid: Option[String],
    triggername: Option[String],
    url: Option[String],
    httpmethod: Option[String],
    httpprotocol: Option[String],
    createdbyuserid: Option[String]
  )

  private case class DoobieSystemAccountNotificationWebhook(row: WebhookRow) extends SystemAccountNotificationWebhookTrait {
    override def webhookId: String = row.webhookid.getOrElse("")
    override def triggerName: String = row.triggername.getOrElse("")
    override def url: String = row.url.getOrElse("")
    override def httpMethod: String = row.httpmethod.getOrElse("")
    override def httpProtocol: String = row.httpprotocol.getOrElse("")
    override def createdByUserId: String = row.createdbyuserid.getOrElse("")
  }

  private def nn(s: String): String = if (s == null) "" else s

  private val selectCols: Fragment =
    fr"""SELECT webhookid, triggername, url, httpmethod, httpprotocol, createdbyuserid
         FROM systemaccountnotificationwebhook"""

  private def findOne(where: Fragment): Box[DoobieSystemAccountNotificationWebhook] =
    DoobieUtil.runQuery((selectCols ++ where ++ fr"LIMIT 1").query[WebhookRow].option) match {
      case Some(r) => Full(DoobieSystemAccountNotificationWebhook(r))
      case None    => Empty
    }

  private def runList(where: Fragment): List[SystemAccountNotificationWebhookTrait] =
    DoobieUtil.runQuery((selectCols ++ where).query[WebhookRow].to[List]).map(DoobieSystemAccountNotificationWebhook(_))

  override def getSystemAccountNotificationWebhookByIdFuture(webhookId: String): Future[Box[SystemAccountNotificationWebhookTrait]] =
    Future { findOne(fr"WHERE webhookid = ${nn(webhookId)}") }

  override def getSystemAccountNotificationWebhooksByUserIdFuture(userId: String): Future[Box[List[SystemAccountNotificationWebhookTrait]]] =
    Future {
      Full(runList(fr"WHERE createdbyuserid = ${nn(userId)} ORDER BY updatedat DESC"))
    }

  // Mirrors the Mapper provider: only OBPLimit / OBPOffset / OBPUserId are honoured here.
  override def getSystemAccountNotificationWebhooksFuture(queryParams: List[OBPQueryParam]): Future[Box[List[SystemAccountNotificationWebhookTrait]]] =
    Future {
      val whereClause = queryParams.collectFirst {
        case OBPUserId(value) => fr"WHERE createdbyuserid = ${nn(value)}"
      }.getOrElse(Fragment.empty)
      val limitClause = queryParams.collectFirst { case OBPLimit(value) => fr"LIMIT $value" }.getOrElse(Fragment.empty)
      val offsetClause = queryParams.collectFirst { case OBPOffset(value) => fr"OFFSET $value" }.getOrElse(Fragment.empty)
      Full(runList(whereClause ++ limitClause ++ offsetClause))
    }

  override def createSystemAccountNotificationWebhookFuture(
    userId: String,
    triggerName: String,
    url: String,
    httpMethod: String,
    httpProtocol: String
  ): Future[Box[SystemAccountNotificationWebhookTrait]] =
    Future {
      val newId = APIUtil.generateUUID()
      val now = new Timestamp(System.currentTimeMillis())
      DoobieUtil.runQuery(
        sql"""INSERT INTO systemaccountnotificationwebhook
                (webhookid, createdbyuserid, triggername, url, httpmethod, httpprotocol, createdat, updatedat)
              VALUES ($newId, ${nn(userId)}, ${nn(triggerName)}, ${nn(url)}, ${nn(httpMethod)}, ${nn(httpProtocol)}, $now, $now)""".update.run)
      Full(DoobieSystemAccountNotificationWebhook(WebhookRow(
        Some(newId), Some(nn(triggerName)), Some(nn(url)), Some(nn(httpMethod)), Some(nn(httpProtocol)), Some(nn(userId)))))
    }

  override def deleteSystemAccountNotificationWebhookFuture(webhookId: String): Future[Box[Boolean]] =
    Future {
      findOne(fr"WHERE webhookid = ${nn(webhookId)}") match {
        case Full(_) =>
          Full(DoobieUtil.runQuery(
            sql"DELETE FROM systemaccountnotificationwebhook WHERE webhookid = ${nn(webhookId)}".update.run) > 0)
        case _ => Empty
      }
    }

}
