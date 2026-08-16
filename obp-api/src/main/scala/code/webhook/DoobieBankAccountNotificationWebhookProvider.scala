package code.webhook

import code.api.util.{APIUtil, DoobieUtil, OBPLimit, OBPOffset, OBPQueryParam, OBPUserId}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Full}

import java.sql.Timestamp
import scala.collection.immutable.List
import scala.concurrent.Future

object DoobieBankAccountNotificationWebhookProvider extends BankAccountNotificationWebhookProvider {

  // Table "bankaccountnotificationwebhook" (Lift class lowercased, no dbTableName override).
  // Columns: id, bankid, createdbyuserid, webhookid, triggername, url, httpmethod, httpprotocol, createdat, updatedat.
  private case class WebhookRow(
    webhookid: Option[String],
    bankid: Option[String],
    createdbyuserid: Option[String],
    triggername: Option[String],
    url: Option[String],
    httpmethod: Option[String],
    httpprotocol: Option[String]
  )

  private case class DoobieBankAccountNotificationWebhook(row: WebhookRow) extends BankAccountNotificationWebhookTrait {
    override def webhookId: String = row.webhookid.getOrElse("")
    override def bankId: String = row.bankid.getOrElse("")
    override def triggerName: String = row.triggername.getOrElse("")
    override def url: String = row.url.getOrElse("")
    override def httpMethod: String = row.httpmethod.getOrElse("")
    override def httpProtocol: String = row.httpprotocol.getOrElse("")
    override def createdByUserId: String = row.createdbyuserid.getOrElse("")
  }

  private def nn(s: String): String = if (s == null) "" else s

  private val selectCols: Fragment =
    fr"""SELECT webhookid, bankid, createdbyuserid, triggername, url, httpmethod, httpprotocol
         FROM bankaccountnotificationwebhook"""

  private def findOne(where: Fragment): Box[DoobieBankAccountNotificationWebhook] =
    DoobieUtil.runQuery((selectCols ++ where ++ fr"LIMIT 1").query[WebhookRow].option) match {
      case Some(r) => Full(DoobieBankAccountNotificationWebhook(r))
      case None    => net.liftweb.common.Empty
    }

  private def runList(where: Fragment): List[BankAccountNotificationWebhookTrait] =
    DoobieUtil.runQuery((selectCols ++ where).query[WebhookRow].to[List]).map(DoobieBankAccountNotificationWebhook(_))

  override def getBankAccountNotificationWebhookByIdFuture(webhookId: String): Future[Box[BankAccountNotificationWebhookTrait]] =
    Future { findOne(fr"WHERE webhookid = ${nn(webhookId)}") }

  override def getBankAccountNotificationWebhooksByUserIdFuture(userId: String): Future[Box[List[BankAccountNotificationWebhookTrait]]] =
    Future {
      Full(runList(fr"WHERE createdbyuserid = ${nn(userId)} ORDER BY updatedat DESC"))
    }

  override def getBankAccountNotificationWebhooksFuture(queryParams: List[OBPQueryParam]): Future[Box[List[BankAccountNotificationWebhookTrait]]] =
    Future {
      val userFrag = queryParams.collectFirst { case OBPUserId(value) => fr"WHERE createdbyuserid = ${nn(value)}" }
        .getOrElse(Fragment.empty)
      val limitFrag = queryParams.collectFirst { case OBPLimit(value) => fr"LIMIT $value" }.getOrElse(Fragment.empty)
      val offsetFrag = queryParams.collectFirst { case OBPOffset(value) => fr"OFFSET $value" }.getOrElse(Fragment.empty)
      Full(runList(userFrag ++ limitFrag ++ offsetFrag))
    }

  override def createBankAccountNotificationWebhookFuture(
    bankId: String,
    userId: String,
    triggerName: String,
    url: String,
    httpMethod: String,
    httpProtocol: String
  ): Future[Box[BankAccountNotificationWebhookTrait]] =
    Future {
      val newId = APIUtil.generateUUID()
      val now = new Timestamp(System.currentTimeMillis())
      DoobieUtil.runQuery(
        sql"""INSERT INTO bankaccountnotificationwebhook
                (webhookid, bankid, createdbyuserid, triggername, url, httpmethod, httpprotocol, createdat, updatedat)
              VALUES ($newId, ${nn(bankId)}, ${nn(userId)}, ${nn(triggerName)}, ${nn(url)}, ${nn(httpMethod)}, ${nn(httpProtocol)}, $now, $now)""".update.run)
      Full(DoobieBankAccountNotificationWebhook(WebhookRow(
        Some(newId), Some(nn(bankId)), Some(nn(userId)), Some(nn(triggerName)),
        Some(nn(url)), Some(nn(httpMethod)), Some(nn(httpProtocol)))))
    }

  override def deleteBankAccountNotificationWebhookFuture(webhookId: String): Future[Box[Boolean]] =
    Future {
      // Mirrors the Mapper: find(...).map(_.delete_!) — returns Empty (not an error Box) when the webhook is not found.
      findOne(fr"WHERE webhookid = ${nn(webhookId)}").map { _ =>
        DoobieUtil.runQuery(
          sql"DELETE FROM bankaccountnotificationwebhook WHERE webhookid = ${nn(webhookId)}".update.run) > 0
      }
    }

}
