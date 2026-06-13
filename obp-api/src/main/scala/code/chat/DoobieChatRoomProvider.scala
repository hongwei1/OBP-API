package code.chat

import code.api.util.{APIUtil, DoobieUtil}
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import java.sql.Timestamp
import java.util.Date

object DoobieChatRoomProvider extends ChatRoomProvider {

  // Table "ChatRoom" → postgres "chatroom".
  private case class ChatRoomRow(
    chatroomid: Option[String],
    bankid: Option[String],
    name: Option[String],
    description: Option[String],
    joiningkey: Option[String],
    createdbyuserid: Option[String],
    isopenroom: Option[Boolean],
    isarchived: Option[Boolean],
    lastmessageat: Option[Timestamp],
    lastmessagepreview: Option[String],
    lastmessagesenderusername: Option[String],
    createdat: Option[Timestamp],
    updatedat: Option[Timestamp]
  )

  private case class DoobieChatRoom(row: ChatRoomRow) extends ChatRoomTrait {
    override def chatRoomId: String = row.chatroomid.getOrElse("")
    override def bankId: String = row.bankid.getOrElse("")
    override def name: String = row.name.getOrElse("")
    override def description: String = row.description.getOrElse("")
    override def joiningKey: String = row.joiningkey.getOrElse("")
    override def createdByUserId: String = row.createdbyuserid.getOrElse("")
    override def isOpenRoom: Boolean = row.isopenroom.getOrElse(false)
    override def isArchived: Boolean = row.isarchived.getOrElse(false)
    override def lastMessageAt: Option[Date] = row.lastmessageat.map(ts => ts: Date)
    override def lastMessagePreview: String = row.lastmessagepreview.getOrElse("")
    override def lastMessageSenderUsername: String = row.lastmessagesenderusername.getOrElse("")
    override def createdDate: Date = row.createdat.orNull
    override def updatedDate: Date = row.updatedat.orNull
  }

  private def nn(s: String): String = if (s == null) "" else s

  private val selectCols: Fragment =
    fr"""SELECT chatroomid, bankid, name, description, joiningkey, createdbyuserid, isopenroom, isarchived,
                lastmessageat, lastmessagepreview, lastmessagesenderusername, createdat, updatedat
         FROM chatroom"""

  private def findByRoomId(chatRoomId: String): Option[ChatRoomRow] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE chatroomid = ${nn(chatRoomId)} LIMIT 1").query[ChatRoomRow].option)

  private def toBox(opt: Option[ChatRoomRow]): Box[ChatRoomTrait] = opt match {
    case Some(r) => Full(DoobieChatRoom(r))
    case None    => Empty
  }

  override def createChatRoom(bankId: String, name: String, description: String,
                              createdByUserId: String): Box[ChatRoomTrait] =
    tryo {
      val newId = APIUtil.generateUUID()
      val newKey = APIUtil.generateUUID()
      val now = new Timestamp(System.currentTimeMillis())
      DoobieUtil.runQuery(
        sql"""INSERT INTO chatroom
                (chatroomid, bankid, name, description, joiningkey, createdbyuserid,
                 isopenroom, isarchived, lastmessagepreview, lastmessagesenderusername, createdat, updatedat)
              VALUES ($newId, ${nn(bankId)}, ${nn(name)}, ${nn(description)}, $newKey, ${nn(createdByUserId)},
                      ${false}, ${false}, '', '', $now, $now)""".update.run)
      DoobieChatRoom(ChatRoomRow(Some(newId), Some(nn(bankId)), Some(nn(name)), Some(nn(description)),
        Some(newKey), Some(nn(createdByUserId)), Some(false), Some(false), None, Some(""), Some(""),
        Some(now), Some(now)))
    }

  override def getChatRoom(chatRoomId: String): Box[ChatRoomTrait] =
    toBox(findByRoomId(chatRoomId))

  override def getChatRoomByBankIdAndName(bankId: String, name: String): Box[ChatRoomTrait] =
    toBox(DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE bankid = ${nn(bankId)} AND name = ${nn(name)} LIMIT 1").query[ChatRoomRow].option))

  override def getChatRoomsByBankId(bankId: String): Box[List[ChatRoomTrait]] =
    tryo {
      DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE bankid = ${nn(bankId)} ORDER BY createdat DESC").query[ChatRoomRow].to[List])
        .map(DoobieChatRoom(_))
    }

  override def getChatRoomsByBankIdForUser(bankId: String, userId: String): Box[List[ChatRoomTrait]] =
    tryo {
      val participantRoomIds: List[String] = DoobieUtil.runQuery(
        fr"SELECT chatroomid FROM participant WHERE userid = ${nn(userId)}".query[String].to[List])
      val explicitRooms: List[ChatRoomTrait] =
        if (participantRoomIds.isEmpty) Nil
        else {
          val inClause = participantRoomIds.map(id => fr"$id").reduceLeft(_ ++ fr"," ++ _)
          DoobieUtil.runQuery(
            (selectCols ++ fr"WHERE bankid = ${nn(bankId)} AND chatroomid IN (" ++ inClause ++ fr")")
              .query[ChatRoomRow].to[List])
            .map(r => DoobieChatRoom(r): ChatRoomTrait)
        }
      val openRooms: List[ChatRoomTrait] = DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE bankid = ${nn(bankId)} AND isopenroom = ${true}")
          .query[ChatRoomRow].to[List])
        .map(r => DoobieChatRoom(r): ChatRoomTrait)
      (explicitRooms ++ openRooms).groupBy(_.chatRoomId).values.map(_.head).toList
    }

  override def getChatRoomByJoiningKey(joiningKey: String): Box[ChatRoomTrait] =
    toBox(DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE joiningkey = ${nn(joiningKey)} LIMIT 1").query[ChatRoomRow].option))

  override def searchChatRoomsForUserWithParticipants(userId: String,
                                                       requiredParticipantUserIds: List[String],
                                                       exactParticipants: Boolean): Box[List[ChatRoomTrait]] =
    tryo {
      val myRoomIds: List[String] = DoobieUtil.runQuery(
        fr"SELECT DISTINCT chatroomid FROM participant WHERE userid = ${nn(userId)}".query[String].to[List])
      if (myRoomIds.isEmpty) Nil
      else {
        val inClause = myRoomIds.map(id => fr"$id").reduceLeft(_ ++ fr"," ++ _)
        val myRooms: List[ChatRoomRow] = DoobieUtil.runQuery(
          (selectCols ++ fr"WHERE chatroomid IN (" ++ inClause ++ fr")").query[ChatRoomRow].to[List])
        val requiredSet = requiredParticipantUserIds.toSet
        val expectedExactSize = requiredSet.size + 1
        myRooms.filter { room =>
          val roomId = room.chatroomid.getOrElse("")
          if (exactParticipants && room.isopenroom.getOrElse(false)) {
            false
          } else {
            val participantIds: Set[String] = DoobieUtil.runQuery(
              fr"SELECT userid FROM participant WHERE chatroomid = $roomId".query[String].to[List]).toSet
            val containsAll = requiredSet.subsetOf(participantIds)
            if (!containsAll) false
            else if (exactParticipants) participantIds.size == expectedExactSize
            else true
          }
        }.map(DoobieChatRoom(_))
      }
    }

  override def updateChatRoom(chatRoomId: String, name: Option[String],
                              description: Option[String]): Box[ChatRoomTrait] = {
    val now = new Timestamp(System.currentTimeMillis())
    val setName = name.map(n => fr"name = ${nn(n)}, ")
    val setDesc = description.map(d => fr"description = ${nn(d)}, ")
    val setParts = List(setName, setDesc).flatten
    if (setParts.isEmpty) return toBox(findByRoomId(chatRoomId))
    val setClauses = setParts.reduceLeft(_ ++ _)
    val rows = DoobieUtil.runQuery(
      (fr"UPDATE chatroom SET " ++ setClauses ++ fr"updatedat = $now WHERE chatroomid = ${nn(chatRoomId)}").update.run)
    if (rows > 0) toBox(findByRoomId(chatRoomId)) else Empty
  }

  override def setIsOpenRoom(chatRoomId: String, isOpenRoom: Boolean): Box[ChatRoomTrait] = {
    val now = new Timestamp(System.currentTimeMillis())
    val rows = DoobieUtil.runQuery(
      sql"""UPDATE chatroom SET isopenroom = $isOpenRoom, updatedat = $now
            WHERE chatroomid = ${nn(chatRoomId)}""".update.run)
    if (rows > 0) toBox(findByRoomId(chatRoomId)) else Empty
  }

  override def updateLastMessageInfo(chatRoomId: String, lastMessageAt: Date,
                                     preview: String, senderUsername: String): Box[ChatRoomTrait] = {
    val ts = new Timestamp(lastMessageAt.getTime)
    val now = new Timestamp(System.currentTimeMillis())
    val truncPreview = if (preview != null && preview.length > 100) preview.substring(0, 100) else nn(preview)
    val rows = DoobieUtil.runQuery(
      sql"""UPDATE chatroom
            SET lastmessageat = $ts, lastmessagepreview = $truncPreview,
                lastmessagesenderusername = ${nn(senderUsername)}, updatedat = $now
            WHERE chatroomid = ${nn(chatRoomId)}""".update.run)
    if (rows > 0) toBox(findByRoomId(chatRoomId)) else Empty
  }

  override def archiveChatRoom(chatRoomId: String): Box[ChatRoomTrait] = {
    val now = new Timestamp(System.currentTimeMillis())
    val rows = DoobieUtil.runQuery(
      sql"""UPDATE chatroom SET isarchived = ${true}, updatedat = $now
            WHERE chatroomid = ${nn(chatRoomId)}""".update.run)
    if (rows > 0) toBox(findByRoomId(chatRoomId)) else Empty
  }

  override def deleteChatRoom(chatRoomId: String): Box[Boolean] =
    tryo {
      DoobieUtil.runQuery(
        sql"DELETE FROM chatroom WHERE chatroomid = ${nn(chatRoomId)}".update.run) > 0
    }

  override def refreshJoiningKey(chatRoomId: String): Box[ChatRoomTrait] = {
    val newKey = APIUtil.generateUUID()
    val now = new Timestamp(System.currentTimeMillis())
    val rows = DoobieUtil.runQuery(
      sql"""UPDATE chatroom SET joiningkey = $newKey, updatedat = $now
            WHERE chatroomid = ${nn(chatRoomId)}""".update.run)
    if (rows > 0) toBox(findByRoomId(chatRoomId)) else Empty
  }

  override def getOrCreateDefaultRoom(): Box[ChatRoomTrait] =
    getChatRoomByBankIdAndName("", "general") match {
      case Full(room) => Full(room)
      case _ =>
        tryo {
          val newId = APIUtil.generateUUID()
          val newKey = APIUtil.generateUUID()
          val now = new Timestamp(System.currentTimeMillis())
          DoobieUtil.runQuery(
            sql"""INSERT INTO chatroom
                    (chatroomid, bankid, name, description, joiningkey, createdbyuserid,
                     isopenroom, isarchived, lastmessagepreview, lastmessagesenderusername, createdat, updatedat)
                  VALUES ($newId, '', 'general', 'Default system-wide chat room for all users',
                          $newKey, 'system', ${true}, ${false}, '', '', $now, $now)""".update.run)
          DoobieChatRoom(ChatRoomRow(Some(newId), Some(""), Some("general"),
            Some("Default system-wide chat room for all users"), Some(newKey), Some("system"),
            Some(true), Some(false), None, Some(""), Some(""), Some(now), Some(now)))
        }
    }
}
