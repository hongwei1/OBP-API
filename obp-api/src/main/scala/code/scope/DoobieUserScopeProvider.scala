package code.scope

import code.api.util.DoobieUtil
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}

import scala.collection.immutable.List

object DoobieUserScopeProvider extends UserScopeProvider {

  // Table "mappeduserscope": id, mscopeid, muserid, createdat, updatedat. Unique index (mscopeid, muserid).
  private case class UserScopeRow(mscopeid: Option[String], muserid: Option[String])

  private case class DoobieUserScope(row: UserScopeRow) extends UserScope {
    override def scopeId: String = row.mscopeid.getOrElse("")
    override def userId: String = row.muserid.getOrElse("")
  }

  private def nn(s: String): String = if (s == null) "" else s

  private val selectCols: Fragment = fr"SELECT mscopeid, muserid FROM mappeduserscope"

  private def findOne(where: Fragment): Box[DoobieUserScope] =
    DoobieUtil.runQuery((selectCols ++ where ++ fr"LIMIT 1").query[UserScopeRow].option) match {
      case Some(r) => Full(DoobieUserScope(r))
      case None    => Empty
    }

  private def findList(where: Fragment): List[UserScope] =
    DoobieUtil.runQuery((selectCols ++ where).query[UserScopeRow].to[List]).map(DoobieUserScope(_))

  override def addUserScope(scopeId: String, userId: String): Box[UserScope] = {
    val now = new java.sql.Timestamp(System.currentTimeMillis())
    DoobieUtil.runQuery(
      sql"""INSERT INTO mappeduserscope (mscopeid, muserid, createdat, updatedat)
            VALUES (${nn(scopeId)}, ${nn(userId)}, $now, $now)""".update.run)
    Full(DoobieUserScope(UserScopeRow(Some(nn(scopeId)), Some(nn(userId)))))
  }

  override def deleteUserScope(scopeId: String, userId: String): Box[Boolean] =
    findOne(fr"WHERE mscopeid = ${nn(scopeId)} AND muserid = ${nn(userId)}").map { _ =>
      DoobieUtil.runQuery(
        sql"DELETE FROM mappeduserscope WHERE mscopeid = ${nn(scopeId)} AND muserid = ${nn(userId)}".update.run) > 0
    }

  override def getUserScope(scopeId: String, userId: String): Box[UserScope] =
    findOne(fr"WHERE mscopeid = ${nn(scopeId)} AND muserid = ${nn(userId)}")

  override def getUserScopesByScopeId(scopeId: String): Box[List[UserScope]] =
    Full(findList(fr"WHERE mscopeid = ${nn(scopeId)} ORDER BY updatedat DESC"))

  override def getUserScopesByUserId(userId: String): Box[List[UserScope]] =
    Full(findList(fr"WHERE muserid = ${nn(userId)} ORDER BY updatedat DESC"))
}
