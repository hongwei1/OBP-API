package code.chat

import code.api.util.{APIUtil, DoobieUtil}
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import java.sql.Timestamp
import java.util.Date

object DoobieChatMessageProvider extends ChatMessageProvider {

  // Table "ChatMessage" → postgres "chatmessage". mentionedUserIds stored comma-separated.
  private case class MsgRow(
    chatmessageid: Option[String],
    chatroomid: Option[String],
    senderuserid: Option[String],
    senderconsumerid: Option[String],
    content: Option[String],
    messagetype: Option[String],
    mentioneduserids: Option[String],
    replytomessageid: Option[String],
    threadid: Option[String],
    isdeleted: Option[Boolean],
    createdat: Option[Timestamp],
    updatedat: Option[Timestamp]
  )

  private case class DoobieChatMessage(row: MsgRow) extends ChatMessageTrait {
    override def chatMessageId: String = row.chatmessageid.getOrElse("")
    override def chatRoomId: String = row.chatroomid.getOrElse("")
    override def senderUserId: String = row.senderuserid.getOrElse("")
    override def senderConsumerId: String = row.senderconsumerid.getOrElse("")
    override def content: String = row.content.getOrElse("")
    override def messageType: String = row.messagetype.getOrElse("")
    override def mentionedUserIds: List[String] = {
      val s = row.mentioneduserids.getOrElse("")
      if (s.isEmpty) List.empty else s.split(",").map(_.trim).filter(_.nonEmpty).toList
    }
    override def replyToMessageId: String = row.replytomessageid.getOrElse("")
    override def threadId: String = row.threadid.getOrElse("")
    override def isDeleted: Boolean = row.isdeleted.getOrElse(false)
    override def createdDate: Date = row.createdat.orNull
    override def updatedDate: Date = row.updatedat.orNull
  }

  private def nn(s: String): String = if (s == null) "" else s

  private val selectCols: Fragment =
    fr"""SELECT chatmessageid, chatroomid, senderuserid, senderconsumerid, content, messagetype,
                mentioneduserids, replytomessageid, threadid, isdeleted, createdat, updatedat
         FROM chatmessage"""

  // Lift mirrors the 60-day cap on unread counts.
  private def effectiveSinceDate(sinceDate: Date): Date = {
    val sixtyDaysAgo = new Date(System.currentTimeMillis() - 60L * 24 * 60 * 60 * 1000)
    if (sinceDate.before(sixtyDaysAgo)) sixtyDaysAgo else sinceDate
  }

  override def createMessage(chatRoomId: String, senderUserId: String, senderConsumerId: String,
                             content: String, messageType: String, mentionedUserIds: List[String],
                             replyToMessageId: String, threadId: String): Box[ChatMessageTrait] =
    tryo {
      val newId = APIUtil.generateUUID()
      val now = new Timestamp(System.currentTimeMillis())
      val mentionStr = mentionedUserIds.mkString(",")
      DoobieUtil.runQuery(
        sql"""INSERT INTO chatmessage
                (chatmessageid, chatroomid, senderuserid, senderconsumerid, content, messagetype,
                 mentioneduserids, replytomessageid, threadid, isdeleted, createdat, updatedat)
              VALUES ($newId, ${nn(chatRoomId)}, ${nn(senderUserId)}, ${nn(senderConsumerId)},
                      ${nn(content)}, ${nn(messageType)}, $mentionStr, ${nn(replyToMessageId)},
                      ${nn(threadId)}, ${false}, $now, $now)""".update.run)
      DoobieChatMessage(MsgRow(
        Some(newId), Some(nn(chatRoomId)), Some(nn(senderUserId)), Some(nn(senderConsumerId)),
        Some(nn(content)), Some(nn(messageType)), Some(mentionStr), Some(nn(replyToMessageId)),
        Some(nn(threadId)), Some(false), Some(now), Some(now)))
    }

  override def getMessage(chatMessageId: String): Box[ChatMessageTrait] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE chatmessageid = ${nn(chatMessageId)} LIMIT 1").query[MsgRow].option) match {
      case Some(r) => Full(DoobieChatMessage(r))
      case None    => Empty
    }

  override def getMessages(chatRoomId: String, limit: Int, offset: Int,
                           fromDate: Date, toDate: Date): Box[List[ChatMessageTrait]] =
    tryo {
      val from = new Timestamp(fromDate.getTime)
      val to = new Timestamp(toDate.getTime)
      DoobieUtil.runQuery(
        (selectCols ++
          fr"""WHERE chatroomid = ${nn(chatRoomId)}
               AND createdat >= $from AND createdat <= $to
               ORDER BY id ASC LIMIT $limit OFFSET $offset""")
          .query[MsgRow].to[List])
        .map(DoobieChatMessage(_))
    }

  override def getThreadReplies(threadId: String): Box[List[ChatMessageTrait]] =
    tryo {
      DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE threadid = ${nn(threadId)} ORDER BY id ASC").query[MsgRow].to[List])
        .map(DoobieChatMessage(_))
    }

  override def getMentionsForUser(userId: String, limit: Int, offset: Int): Box[List[ChatMessageTrait]] =
    tryo {
      val pattern = s"%${nn(userId)}%"
      DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE mentioneduserids LIKE $pattern ORDER BY id DESC LIMIT $limit OFFSET $offset")
          .query[MsgRow].to[List])
        .map(DoobieChatMessage(_))
    }

  override def getUnreadCount(chatRoomId: String, userId: String, sinceDate: Date): Box[Long] =
    tryo {
      val since = new Timestamp(effectiveSinceDate(sinceDate).getTime)
      DoobieUtil.runQuery(
        fr"""SELECT COUNT(*) FROM chatmessage
             WHERE chatroomid = ${nn(chatRoomId)} AND createdat > $since AND senderuserid <> ${nn(userId)}"""
          .query[Long].unique)
    }

  override def getUnreadMentionCount(chatRoomId: String, userId: String, sinceDate: Date): Box[Long] =
    tryo {
      val since = new Timestamp(effectiveSinceDate(sinceDate).getTime)
      val pattern = s"%${nn(userId)}%"
      DoobieUtil.runQuery(
        fr"""SELECT COUNT(*) FROM chatmessage
             WHERE chatroomid = ${nn(chatRoomId)} AND createdat > $since AND senderuserid <> ${nn(userId)}
               AND mentioneduserids LIKE $pattern"""
          .query[Long].unique)
    }

  override def updateMessage(chatMessageId: String, content: String): Box[ChatMessageTrait] = {
    val now = new Timestamp(System.currentTimeMillis())
    val rows = DoobieUtil.runQuery(
      sql"""UPDATE chatmessage SET content = ${nn(content)}, updatedat = $now
            WHERE chatmessageid = ${nn(chatMessageId)}""".update.run)
    if (rows > 0) getMessage(chatMessageId) else Empty
  }

  override def softDeleteMessage(chatMessageId: String): Box[ChatMessageTrait] = {
    val now = new Timestamp(System.currentTimeMillis())
    val rows = DoobieUtil.runQuery(
      sql"""UPDATE chatmessage SET isdeleted = ${true}, updatedat = $now
            WHERE chatmessageid = ${nn(chatMessageId)}""".update.run)
    if (rows > 0) getMessage(chatMessageId) else Empty
  }
}
