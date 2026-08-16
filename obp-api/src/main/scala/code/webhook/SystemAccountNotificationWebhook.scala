package code.webhook

import code.api.util._
import code.util.{AccountIdString, MappedUUID, UUIDString}
import net.liftweb.common.{Box, Full}
import net.liftweb.mapper._
import doobie._
import doobie.implicits._

import java.sql.Timestamp
import scala.collection.immutable.List
import com.openbankproject.commons.ExecutionContext.Implicits.global
import scala.concurrent.Future

private case class SystemAccountNotificationWebhookRow(
  webhookId: String,
  triggerName: String,
  url: String,
  httpMethod: String,
  httpProtocol: String,
  createdByUserId: String
) extends SystemAccountNotificationWebhookTrait

object MappedSystemAccountNotificationWebhookProvider extends SystemAccountNotificationWebhookProvider {

  override def getSystemAccountNotificationWebhookByIdFuture(webhookId: String): Future[Box[SystemAccountNotificationWebhookTrait]] = Future {
    DoobieUtil.runQuery(
      fr"SELECT webhookid, triggername, url, httpmethod, httpprotocol, createdbyuserid FROM systemaccountnotificationwebhook WHERE webhookid = $webhookId"
        .query[SystemAccountNotificationWebhookRow].option
    ) match {
      case Some(row) => Full(row)
      case None      => net.liftweb.common.Empty
    }
  }

  override def getSystemAccountNotificationWebhooksByUserIdFuture(userId: String): Future[Box[List[SystemAccountNotificationWebhookTrait]]] = Future {
    Full(DoobieUtil.runQuery(
      fr"SELECT webhookid, triggername, url, httpmethod, httpprotocol, createdbyuserid FROM systemaccountnotificationwebhook WHERE createdbyuserid = $userId ORDER BY updatedat DESC"
        .query[SystemAccountNotificationWebhookRow].to[List]
    ))
  }

  override def getSystemAccountNotificationWebhooksFuture(queryParams: List[OBPQueryParam]): Future[Box[List[SystemAccountNotificationWebhookTrait]]] = Future {
    val limit  = queryParams.collectFirst { case OBPLimit(v)  => v }.getOrElse(100)
    val offset = queryParams.collectFirst { case OBPOffset(v) => v }.getOrElse(0)
    val userId = queryParams.collectFirst { case OBPUserId(v) => v }
    val baseQ  = fr"SELECT webhookid, triggername, url, httpmethod, httpprotocol, createdbyuserid FROM systemaccountnotificationwebhook"
    val whereQ = userId.map(u => fr"WHERE createdbyuserid = $u").getOrElse(fr"")
    Full(DoobieUtil.runQuery(
      (baseQ ++ whereQ ++ fr"LIMIT $limit OFFSET $offset")
        .query[SystemAccountNotificationWebhookRow].to[List]
    ))
  }

  override def createSystemAccountNotificationWebhookFuture(
    userId: String,
    triggerName: String,
    url: String,
    httpMethod: String,
    httpProtocol: String,
  ): Future[Box[SystemAccountNotificationWebhookTrait]] = Future {
    val webhookId = APIUtil.generateUUID()
    val now       = new Timestamp(System.currentTimeMillis())
    DoobieUtil.runQuery(
      sql"""INSERT INTO systemaccountnotificationwebhook
              (webhookid, createdbyuserid, triggername, url, httpmethod, httpprotocol, createdat, updatedat)
            VALUES
              ($webhookId, $userId, $triggerName, $url, $httpMethod, $httpProtocol, $now, $now)"""
        .update.run
    )
    Full(SystemAccountNotificationWebhookRow(webhookId, triggerName, url, httpMethod, httpProtocol, userId))
  }

  override def deleteSystemAccountNotificationWebhookFuture(webhookId: String): Future[Box[Boolean]] = Future {
    val count = DoobieUtil.runQuery(
      sql"DELETE FROM systemaccountnotificationwebhook WHERE webhookid = $webhookId".update.run
    )
    Full(count > 0)
  }

}

class SystemAccountNotificationWebhook extends SystemAccountNotificationWebhookTrait with LongKeyedMapper[SystemAccountNotificationWebhook] with IdPK with CreatedUpdated {
  def getSingleton = SystemAccountNotificationWebhook

  object WebhookId extends MappedUUID(this)
  object TriggerName extends MappedString(this, 64)
  object Url extends MappedString(this, 1024)
  object HttpMethod extends MappedString(this, 64)
  object HttpProtocol extends MappedString(this, 64)
  object CreatedByUserId extends UUIDString(this)

  def webhookId: String = WebhookId.get
  def triggerName: String = TriggerName.get
  def url: String = Url.get
  def httpMethod: String = HttpMethod.get
  def httpProtocol: String = HttpProtocol.get
  def createdByUserId: String = CreatedByUserId.get
}

object SystemAccountNotificationWebhook extends SystemAccountNotificationWebhook with LongKeyedMetaMapper[SystemAccountNotificationWebhook] {
  override def dbIndexes = UniqueIndex(WebhookId) :: super.dbIndexes
}
