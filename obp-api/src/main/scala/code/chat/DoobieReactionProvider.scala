package code.chat

import code.api.util.{APIUtil, DoobieUtil}
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import java.sql.Timestamp
import java.util.Date

object DoobieReactionProvider extends ReactionProvider {

  private case class ReactionRow(
    reactionid: Option[String],
    chatmessageid: Option[String],
    userid: Option[String],
    emoji: Option[String],
    createdat: Option[Timestamp]
  )

  private case class DoobieReaction(row: ReactionRow) extends ReactionTrait {
    override def reactionId: String = row.reactionid.getOrElse("")
    override def chatMessageId: String = row.chatmessageid.getOrElse("")
    override def userId: String = row.userid.getOrElse("")
    override def emoji: String = row.emoji.getOrElse("")
    override def createdDate: Date = row.createdat.orNull
  }

  private def nn(s: String): String = if (s == null) "" else s

  private val selectCols: Fragment =
    fr"SELECT reactionid, chatmessageid, userid, emoji, createdat FROM reaction"

  override def addReaction(chatMessageId: String, userId: String, emoji: String): Box[ReactionTrait] =
    tryo {
      val newId = APIUtil.generateUUID()
      val now = new Timestamp(System.currentTimeMillis())
      DoobieUtil.runQuery(
        sql"""INSERT INTO reaction (reactionid, chatmessageid, userid, emoji, createdat, updatedat)
              VALUES ($newId, ${nn(chatMessageId)}, ${nn(userId)}, ${nn(emoji)}, $now, $now)""".update.run)
      DoobieReaction(ReactionRow(Some(newId), Some(nn(chatMessageId)), Some(nn(userId)), Some(nn(emoji)), Some(now)))
    }

  override def removeReaction(chatMessageId: String, userId: String, emoji: String): Box[Boolean] =
    tryo {
      DoobieUtil.runQuery(
        sql"""DELETE FROM reaction
              WHERE chatmessageid = ${nn(chatMessageId)} AND userid = ${nn(userId)} AND emoji = ${nn(emoji)}""".update.run) > 0
    }

  override def getReactions(chatMessageId: String): Box[List[ReactionTrait]] =
    tryo {
      DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE chatmessageid = ${nn(chatMessageId)} ORDER BY createdat ASC")
          .query[ReactionRow].to[List])
        .map(DoobieReaction(_))
    }

  override def getReactionsForMessages(chatMessageIds: List[String]): Box[Map[String, List[ReactionTrait]]] =
    tryo {
      if (chatMessageIds.isEmpty) Map.empty[String, List[ReactionTrait]]
      else {
        val inClause = chatMessageIds.map(id => fr"$id").reduceLeft(_ ++ fr"," ++ _)
        DoobieUtil.runQuery(
          (selectCols ++ fr"WHERE chatmessageid IN (" ++ inClause ++ fr") ORDER BY createdat ASC")
            .query[ReactionRow].to[List])
          .map(r => DoobieReaction(r): ReactionTrait)
          .groupBy(_.chatMessageId)
      }
    }

  override def getReaction(chatMessageId: String, userId: String, emoji: String): Box[ReactionTrait] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE chatmessageid = ${nn(chatMessageId)} AND userid = ${nn(userId)} AND emoji = ${nn(emoji)} LIMIT 1")
        .query[ReactionRow].option) match {
      case Some(r) => Full(DoobieReaction(r))
      case None    => Empty
    }
}
