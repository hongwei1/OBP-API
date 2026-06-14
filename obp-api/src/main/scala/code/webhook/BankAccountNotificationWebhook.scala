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

private case class BankAccountNotificationWebhookRow(
  webhookId: String,
  bankId: String,
  triggerName: String,
  url: String,
  httpMethod: String,
  httpProtocol: String,
  createdByUserId: String
) extends BankAccountNotificationWebhookTrait

object MappedBankAccountNotificationWebhookProvider extends BankAccountNotificationWebhookProvider {

  override def getBankAccountNotificationWebhookByIdFuture(webhookId: String): Future[Box[BankAccountNotificationWebhookTrait]] = Future {
    DoobieUtil.runQuery(
      fr"SELECT webhookid, bankid, triggername, url, httpmethod, httpprotocol, createdbyuserid FROM bankaccountnotificationwebhook WHERE webhookid = $webhookId"
        .query[BankAccountNotificationWebhookRow].option
    ) match {
      case Some(row) => Full(row)
      case None      => net.liftweb.common.Empty
    }
  }

  override def getBankAccountNotificationWebhooksByUserIdFuture(userId: String): Future[Box[List[BankAccountNotificationWebhookTrait]]] = Future {
    Full(DoobieUtil.runQuery(
      fr"SELECT webhookid, bankid, triggername, url, httpmethod, httpprotocol, createdbyuserid FROM bankaccountnotificationwebhook WHERE createdbyuserid = $userId ORDER BY updatedat DESC"
        .query[BankAccountNotificationWebhookRow].to[List]
    ))
  }

  override def getBankAccountNotificationWebhooksFuture(queryParams: List[OBPQueryParam]): Future[Box[List[BankAccountNotificationWebhookTrait]]] = Future {
    val limit  = queryParams.collectFirst { case OBPLimit(v)  => v }.getOrElse(100)
    val offset = queryParams.collectFirst { case OBPOffset(v) => v }.getOrElse(0)
    val userId = queryParams.collectFirst { case OBPUserId(v) => v }
    val baseQ  = fr"SELECT webhookid, bankid, triggername, url, httpmethod, httpprotocol, createdbyuserid FROM bankaccountnotificationwebhook"
    val whereQ = userId.map(u => fr"WHERE createdbyuserid = $u").getOrElse(fr"")
    Full(DoobieUtil.runQuery(
      (baseQ ++ whereQ ++ fr"LIMIT $limit OFFSET $offset")
        .query[BankAccountNotificationWebhookRow].to[List]
    ))
  }

  override def createBankAccountNotificationWebhookFuture(
    bankId: String,
    userId: String,
    triggerName: String,
    url: String,
    httpMethod: String,
    httpProtocol: String,
  ): Future[Box[BankAccountNotificationWebhookTrait]] = Future {
    val webhookId = APIUtil.generateUUID()
    val now       = new Timestamp(System.currentTimeMillis())
    DoobieUtil.runQuery(
      sql"""INSERT INTO bankaccountnotificationwebhook
              (webhookid, bankid, createdbyuserid, triggername, url, httpmethod, httpprotocol, createdat, updatedat)
            VALUES
              ($webhookId, $bankId, $userId, $triggerName, $url, $httpMethod, $httpProtocol, $now, $now)"""
        .update.run
    )
    Full(BankAccountNotificationWebhookRow(webhookId, bankId, triggerName, url, httpMethod, httpProtocol, userId))
  }

  override def deleteBankAccountNotificationWebhookFuture(webhookId: String): Future[Box[Boolean]] = Future {
    val count = DoobieUtil.runQuery(
      sql"DELETE FROM bankaccountnotificationwebhook WHERE webhookid = $webhookId".update.run
    )
    Full(count > 0)
  }

}

class BankAccountNotificationWebhook extends BankAccountNotificationWebhookTrait with LongKeyedMapper[BankAccountNotificationWebhook] with IdPK with CreatedUpdated {
  def getSingleton = BankAccountNotificationWebhook

  object WebhookId extends MappedUUID(this)
  object BankId extends UUIDString(this)
  object TriggerName extends MappedString(this, 64)
  object Url extends MappedString(this, 1024)
  object HttpMethod extends MappedString(this, 64)
  object HttpProtocol extends MappedString(this, 64)
  object CreatedByUserId extends UUIDString(this)

  def webhookId: String = WebhookId.get
  def bankId: String = BankId.get
  def triggerName: String = TriggerName.get
  def url: String = Url.get
  def httpMethod: String = HttpMethod.get
  def httpProtocol: String = HttpProtocol.get
  def createdByUserId: String = CreatedByUserId.get
}

object BankAccountNotificationWebhook extends BankAccountNotificationWebhook with LongKeyedMetaMapper[BankAccountNotificationWebhook] {
  override def dbIndexes = UniqueIndex(WebhookId) :: super.dbIndexes
}
