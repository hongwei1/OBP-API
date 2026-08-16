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

private case class AccountWebhookRow(
  accountWebhookId: String,
  bankId: String,
  accountId: String,
  triggerName: String,
  url: String,
  httpMethod: String,
  httpProtocol: String,
  createdByUserId: String,
  isActiveValue: Boolean
) extends AccountWebhook {
  def isActive(): Boolean = isActiveValue
}

object MappedAccountWebhookProvider extends AccountWebhookProvider {

  override def getAccountWebhookByIdFuture(accountWebhookId: String): Future[Box[AccountWebhook]] = Future {
    DoobieUtil.runQuery(
      fr"""SELECT maccountwebhookid, mbankid, maccountid, mtriggername, murl, mhttpmethod, mhttpprotocol, mcreatedbyuserid, misactive
           FROM mappedaccountwebhook WHERE maccountwebhookid = $accountWebhookId"""
        .query[AccountWebhookRow].option
    ) match {
      case Some(row) => Full(row)
      case None      => net.liftweb.common.Empty
    }
  }

  override def getAccountWebhooksByUserIdFuture(userId: String): Future[Box[List[AccountWebhook]]] = Future {
    Full(DoobieUtil.runQuery(
      fr"""SELECT maccountwebhookid, mbankid, maccountid, mtriggername, murl, mhttpmethod, mhttpprotocol, mcreatedbyuserid, misactive
           FROM mappedaccountwebhook WHERE mcreatedbyuserid = $userId ORDER BY updatedat DESC"""
        .query[AccountWebhookRow].to[List]
    ))
  }

  override def getAccountWebhooksFuture(queryParams: List[OBPQueryParam]): Future[Box[List[AccountWebhook]]] = Future {
    val limit     = queryParams.collectFirst { case OBPLimit(v)     => v }.getOrElse(100)
    val offset    = queryParams.collectFirst { case OBPOffset(v)    => v }.getOrElse(0)
    val userId    = queryParams.collectFirst { case OBPUserId(v)    => v }
    val bankId    = queryParams.collectFirst { case OBPBankId(v)    => v }
    val accountId = queryParams.collectFirst { case OBPAccountId(v) => v }
    val conditions = List(
      userId   .map(u => fr"mcreatedbyuserid = $u"),
      bankId   .map(b => fr"mbankid = $b"),
      accountId.map(a => fr"maccountid = $a")
    ).flatten
    val baseQ  = fr"SELECT maccountwebhookid, mbankid, maccountid, mtriggername, murl, mhttpmethod, mhttpprotocol, mcreatedbyuserid, misactive FROM mappedaccountwebhook"
    val whereQ = if (conditions.isEmpty) fr"" else fr"WHERE" ++ conditions.reduce(_ ++ fr" AND " ++ _)
    Full(DoobieUtil.runQuery(
      (baseQ ++ whereQ ++ fr"LIMIT $limit OFFSET $offset")
        .query[AccountWebhookRow].to[List]
    ))
  }

  override def createAccountWebhookFuture(bankId: String,
                                          accountId: String,
                                          userId: String,
                                          triggerName: String,
                                          url: String,
                                          httpMethod: String,
                                          httpProtocol: String,
                                          isActive: Boolean
                                         ): Future[Box[AccountWebhook]] = Future {
    val webhookId = APIUtil.generateUUID()
    val now       = new Timestamp(System.currentTimeMillis())
    DoobieUtil.runQuery(
      sql"""INSERT INTO mappedaccountwebhook
              (maccountwebhookid, mbankid, maccountid, mcreatedbyuserid, mtriggername, murl, mhttpmethod, mhttpprotocol, misactive, createdat, updatedat)
            VALUES
              ($webhookId, $bankId, $accountId, $userId, $triggerName, $url, $httpMethod, $httpProtocol, $isActive, $now, $now)"""
        .update.run
    )
    Full(AccountWebhookRow(webhookId, bankId, accountId, triggerName, url, httpMethod, httpProtocol, userId, isActive))
  }

  override def updateAccountWebhookFuture(accountWebhookId: String,
                                          isActive: Boolean
                                         ): Future[Box[AccountWebhook]] = Future {
    val count = DoobieUtil.runQuery(
      sql"UPDATE mappedaccountwebhook SET misactive = $isActive WHERE maccountwebhookid = $accountWebhookId"
        .update.run
    )
    if (count > 0) {
      DoobieUtil.runQuery(
        fr"""SELECT maccountwebhookid, mbankid, maccountid, mtriggername, murl, mhttpmethod, mhttpprotocol, mcreatedbyuserid, misactive
             FROM mappedaccountwebhook WHERE maccountwebhookid = $accountWebhookId"""
          .query[AccountWebhookRow].option
      ) match {
        case Some(row) => Full(row)
        case None      => net.liftweb.common.Empty
      }
    } else {
      net.liftweb.common.Empty
    }
  }

}

class MappedAccountWebhook extends AccountWebhook with LongKeyedMapper[MappedAccountWebhook] with IdPK with CreatedUpdated {
  def getSingleton = MappedAccountWebhook

  object mAccountWebhookId extends MappedUUID(this)
  object mBankId extends UUIDString(this)
  object mAccountId extends AccountIdString(this)
  object mTriggerName extends MappedString(this, 64)
  object mUrl extends MappedString(this, 1024)
  object mHttpMethod extends MappedString(this, 64)
  object mHttpProtocol extends MappedString(this, 64)
  object mCreatedByUserId extends UUIDString(this)
  object mIsActive extends MappedBoolean(this)

  def accountWebhookId: String = mAccountWebhookId.get
  def bankId: String = mBankId.get
  def accountId: String = mAccountId.get
  def triggerName: String = mTriggerName.get
  def url: String = mUrl.get
  def httpMethod: String = mHttpMethod.get
  def httpProtocol: String = mHttpProtocol.get
  def createdByUserId: String = mCreatedByUserId.get
  def isActive(): Boolean = mIsActive.get
}

object MappedAccountWebhook extends MappedAccountWebhook with LongKeyedMetaMapper[MappedAccountWebhook] {
  override def dbIndexes = UniqueIndex(mAccountWebhookId) :: super.dbIndexes
}
