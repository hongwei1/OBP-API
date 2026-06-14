package code.userlocks

import code.api.util.DoobieUtil
import code.users.Users
import code.util.Helper.MdcLoggable
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.now

import java.sql.Timestamp
import java.util.Date

object UserLocksProvider extends MdcLoggable {

  private case class UserLocksRow(id: Long, userId: String, typeOfLock: String, lastLockDate: Option[Timestamp])

  private val selectCols: Fragment =
    fr"SELECT id, userid, typeoflock, lastlockdate FROM userlocks"

  private def findByUserId(userId: String): Option[UserLocksRow] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE userid = $userId LIMIT 1").query[UserLocksRow].option
    )

  private def toHolder(row: UserLocksRow): UserLocks = {
    val lockDate: Date = row.lastLockDate.map(t => new Date(t.getTime)).getOrElse(new Date(0L))
    UserLocks.create
      .UserId(row.userId)
      .TypeOfLock(row.typeOfLock)
      .LastLockDate(lockDate)
  }

  def isLocked(provider: String, username: String): Boolean =
    Users.users.vend.getUserByProviderAndUsername(provider, username) match {
      case Full(user) => findByUserId(user.userId).isDefined
      case _          => false
    }

  def lockUser(provider: String, username: String): Box[UserLocks] =
    Users.users.vend.getUserByProviderAndUsername(provider, username) match {
      case Full(user) =>
        val ts = new Timestamp(now.getTime)
        findByUserId(user.userId) match {
          case Some(row) =>
            DoobieUtil.runQuery(
              sql"UPDATE userlocks SET lastlockdate = $ts WHERE id = ${row.id}".update.run
            )
          case None =>
            DoobieUtil.runQuery(
              sql"""INSERT INTO userlocks (userid, typeoflock, lastlockdate)
                    VALUES (${user.userId}, 'lock_via_api', $ts)""".update.run
            )
        }
        findByUserId(user.userId).map(r => Full(toHolder(r))).getOrElse(Empty)
      case _ =>
        Empty
    }

  def unlockUser(provider: String, username: String): Box[Boolean] =
    Users.users.vend.getUserByProviderAndUsername(provider, username) match {
      case Full(user) =>
        findByUserId(user.userId) match {
          case Some(row) =>
            DoobieUtil.runQuery(
              sql"DELETE FROM userlocks WHERE id = ${row.id}".update.run
            )
            Full(true)
          case None =>
            Full(true)
        }
      case _ =>
        Empty
    }
}
