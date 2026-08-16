package code.chat

import code.api.util.{APIUtil, DoobieUtil}
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import java.sql.Timestamp
import java.util.Date

object DoobieParticipantProvider extends ParticipantProvider {

  // Table "Participant" → postgres "participant". No CreatedUpdated; has custom JoinedAt / LastReadAt.
  private case class ParticipantRow(
    participantid: Option[String],
    chatroomid: Option[String],
    userid: Option[String],
    consumerid: Option[String],
    permissions: Option[String],
    webhookurl: Option[String],
    joinedat: Option[Timestamp],
    lastreadat: Option[Timestamp],
    ismuted: Option[Boolean]
  )

  private case class DoobieParticipant(row: ParticipantRow) extends ParticipantTrait {
    override def participantId: String = row.participantid.getOrElse("")
    override def chatRoomId: String = row.chatroomid.getOrElse("")
    override def userId: String = row.userid.getOrElse("")
    override def consumerId: String = row.consumerid.getOrElse("")
    override def permissions: List[String] = {
      val s = row.permissions.getOrElse("")
      if (s.isEmpty) List.empty else s.split(",").map(_.trim).filter(_.nonEmpty).toList
    }
    override def webhookUrl: String = row.webhookurl.getOrElse("")
    override def joinedAt: Date = row.joinedat.orNull
    override def lastReadAt: Date = row.lastreadat.orNull
    override def isMuted: Boolean = row.ismuted.getOrElse(false)
  }

  private def nn(s: String): String = if (s == null) "" else s

  private val selectCols: Fragment =
    fr"""SELECT participantid, chatroomid, userid, consumerid, permissions, webhookurl, joinedat, lastreadat, ismuted
         FROM participant"""

  private def findRow(chatRoomId: String, userId: String): Option[ParticipantRow] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE chatroomid = ${nn(chatRoomId)} AND userid = ${nn(userId)} LIMIT 1")
        .query[ParticipantRow].option)

  private def toBox(opt: Option[ParticipantRow]): Box[ParticipantTrait] = opt match {
    case Some(r) => Full(DoobieParticipant(r))
    case None    => Empty
  }

  override def addParticipant(chatRoomId: String, userId: String, consumerId: String,
                              permissions: List[String], webhookUrl: String): Box[ParticipantTrait] =
    tryo {
      val newId = APIUtil.generateUUID()
      val now = new Timestamp(System.currentTimeMillis())
      val permsStr = permissions.mkString(",")
      DoobieUtil.runQuery(
        sql"""INSERT INTO participant
                (participantid, chatroomid, userid, consumerid, permissions, webhookurl, joinedat, lastreadat, ismuted)
              VALUES ($newId, ${nn(chatRoomId)}, ${nn(userId)}, ${nn(consumerId)}, $permsStr, ${nn(webhookUrl)}, $now, $now, ${false})""".update.run)
      DoobieParticipant(ParticipantRow(Some(newId), Some(nn(chatRoomId)), Some(nn(userId)),
        Some(nn(consumerId)), Some(permsStr), Some(nn(webhookUrl)), Some(now), Some(now), Some(false)))
    }

  override def getParticipant(chatRoomId: String, userId: String): Box[ParticipantTrait] =
    toBox(findRow(chatRoomId, userId))

  override def getParticipantByConsumerId(chatRoomId: String, consumerId: String): Box[ParticipantTrait] =
    toBox(DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE chatroomid = ${nn(chatRoomId)} AND consumerid = ${nn(consumerId)} LIMIT 1")
        .query[ParticipantRow].option))

  override def getParticipants(chatRoomId: String): Box[List[ParticipantTrait]] =
    tryo {
      DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE chatroomid = ${nn(chatRoomId)} ORDER BY joinedat ASC")
          .query[ParticipantRow].to[List])
        .map(DoobieParticipant(_))
    }

  override def getParticipantRoomsByUserId(userId: String): Box[List[ParticipantTrait]] =
    tryo {
      DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE userid = ${nn(userId)} ORDER BY joinedat ASC")
          .query[ParticipantRow].to[List])
        .map(DoobieParticipant(_))
    }

  override def updateParticipantPermissions(chatRoomId: String, userId: String,
                                            permissions: List[String]): Box[ParticipantTrait] = {
    val permsStr = permissions.mkString(",")
    val rows = DoobieUtil.runQuery(
      sql"""UPDATE participant SET permissions = $permsStr
            WHERE chatroomid = ${nn(chatRoomId)} AND userid = ${nn(userId)}""".update.run)
    if (rows > 0) toBox(findRow(chatRoomId, userId)) else Empty
  }

  override def updateWebhookUrl(chatRoomId: String, userId: String, webhookUrl: String): Box[ParticipantTrait] = {
    val rows = DoobieUtil.runQuery(
      sql"""UPDATE participant SET webhookurl = ${nn(webhookUrl)}
            WHERE chatroomid = ${nn(chatRoomId)} AND userid = ${nn(userId)}""".update.run)
    if (rows > 0) toBox(findRow(chatRoomId, userId)) else Empty
  }

  override def updateLastReadAt(chatRoomId: String, userId: String): Box[ParticipantTrait] = {
    val now = new Timestamp(System.currentTimeMillis())
    val rows = DoobieUtil.runQuery(
      sql"""UPDATE participant SET lastreadat = $now
            WHERE chatroomid = ${nn(chatRoomId)} AND userid = ${nn(userId)}""".update.run)
    if (rows > 0) toBox(findRow(chatRoomId, userId)) else Empty
  }

  override def updateMuted(chatRoomId: String, userId: String, isMuted: Boolean): Box[ParticipantTrait] = {
    val rows = DoobieUtil.runQuery(
      sql"""UPDATE participant SET ismuted = $isMuted
            WHERE chatroomid = ${nn(chatRoomId)} AND userid = ${nn(userId)}""".update.run)
    if (rows > 0) toBox(findRow(chatRoomId, userId)) else Empty
  }

  override def removeParticipant(chatRoomId: String, userId: String): Box[Boolean] =
    tryo {
      DoobieUtil.runQuery(
        sql"""DELETE FROM participant WHERE chatroomid = ${nn(chatRoomId)} AND userid = ${nn(userId)}""".update.run) > 0
    }
}
