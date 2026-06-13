package code.UserRefreshes

import code.api.util.{APIUtil, DoobieUtil}
import doobie._
import doobie.implicits._
import net.liftweb.common.{Empty, Full}

import java.sql.Timestamp
import java.util.{Calendar, Date}

object DoobieUserRefreshesProvider extends UserRefreshesProvider {

  // Table "mappeduserrefreshes": id, muserid, createdat, updatedat.
  private def nn(s: String): String = if (s == null) "" else s

  // Mirrors the Mapper: if no row → refresh; otherwise refresh once lastUpdate + interval has passed.
  override def needToRefreshUser(userId: String): Boolean =
    DoobieUtil.runQuery(
      fr"SELECT updatedat FROM mappeduserrefreshes WHERE muserid = ${nn(userId)} LIMIT 1".query[Option[Timestamp]].option) match {
      case Some(Some(lastUpdate)) =>
        val userRefreshesInterval = APIUtil.getPropsAsIntValue("refresh_user.interval", 30)
        val lastUpdatePlusInterval: Calendar = Calendar.getInstance()
        lastUpdatePlusInterval.setTime(lastUpdate: Date)
        lastUpdatePlusInterval.add(Calendar.MINUTE, userRefreshesInterval)
        val currentDate = Calendar.getInstance()
        lastUpdatePlusInterval.before(currentDate)
      case _ => true
    }

  override def createOrUpdateRefreshUser(userId: String): UserRefreshes = {
    val now = new Timestamp(System.currentTimeMillis())
    DoobieUtil.runQuery(
      fr"SELECT muserid FROM mappeduserrefreshes WHERE muserid = ${nn(userId)} LIMIT 1".query[Option[String]].option) match {
      case Some(_) =>
        DoobieUtil.runQuery(
          sql"UPDATE mappeduserrefreshes SET updatedat = $now WHERE muserid = ${nn(userId)}".update.run)
      case None =>
        DoobieUtil.runQuery(
          sql"INSERT INTO mappeduserrefreshes (muserid, createdat, updatedat) VALUES (${nn(userId)}, $now, $now)".update.run)
    }
    // The result is discarded by all callers; reuse the Lift class as an in-memory holder.
    MappedUserRefreshes.create.mUserId(userId)
  }
}
