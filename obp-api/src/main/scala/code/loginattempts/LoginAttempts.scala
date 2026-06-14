package code.loginattempts

import code.api.util.{APIUtil, DoobieUtil}
import code.userlocks.UserLocksProvider
import code.util.Helper.MdcLoggable
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.now

import java.sql.Timestamp
import java.util.Date

object LoginAttempt extends MdcLoggable {

  def maxBadLoginAttempts: String = APIUtil.getPropsValue("max.bad.login.attempts") openOr "5"

  private case class LoginAttemptRow(
    id: Long,
    username: String,
    provider: String,
    attempts: Int,
    lastFailure: Option[Timestamp]
  )

  private case class DoobieBadLoginAttempt(
    username: String,
    provider: String,
    badAttemptsSinceLastSuccessOrReset: Int,
    lastFailureDate: Date
  ) extends BadLoginAttempt

  private val selectCols: Fragment =
    fr"SELECT id, musername, provider, mbadattemptssincelastsuccessorreset, mlastfailuredate FROM mappedbadloginattempt"

  private def findRow(provider: String, username: String): Option[LoginAttemptRow] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE provider = $provider AND musername = $username LIMIT 1")
        .query[LoginAttemptRow].option
    )

  private def toTrait(row: LoginAttemptRow): BadLoginAttempt =
    DoobieBadLoginAttempt(
      username  = row.username,
      provider  = row.provider,
      badAttemptsSinceLastSuccessOrReset = row.attempts,
      lastFailureDate = row.lastFailure.map(t => new Date(t.getTime)).getOrElse(new Date(0L))
    )

  def incrementBadLoginAttempts(provider: String, username: String): Unit = {
    if (username.isEmpty) {
      logger.warn(s"Username is empty: incrementBadLoginAttempts(username=$username, provider=$provider")
      return
    }
    logger.debug(s"Hello from incrementBadLoginAttempts with $username")
    val ts = new Timestamp(now.getTime)
    findRow(provider, username) match {
      case Some(row) =>
        val newCount = row.attempts + 1
        logger.debug(s"incrementBadLoginAttempts found ${row.attempts} loginAttempt(s) with id ${row.id}")
        DoobieUtil.runQuery(
          sql"""UPDATE mappedbadloginattempt
                SET mbadattemptssincelastsuccessorreset = $newCount, mlastfailuredate = $ts
                WHERE id = ${row.id}""".update.run
        )
      case None =>
        DoobieUtil.runQuery(
          sql"""INSERT INTO mappedbadloginattempt (musername, provider, mbadattemptssincelastsuccessorreset, mlastfailuredate)
                VALUES ($username, $provider, 1, $ts)""".update.run
        )
        logger.debug(s"incrementBadLoginAttempts created loginAttempt")
    }
  }

  def getOrCreateBadLoginStatus(provider: String, username: String): Box[BadLoginAttempt] = {
    findRow(provider, username) match {
      case Some(row) => Full(toTrait(row))
      case None =>
        val ts = new Timestamp(now.getTime)
        DoobieUtil.runQuery(
          sql"""INSERT INTO mappedbadloginattempt (musername, provider, mbadattemptssincelastsuccessorreset, mlastfailuredate)
                VALUES ($username, $provider, 0, $ts)""".update.run
        )
        findRow(provider, username).map(r => Full(toTrait(r))).getOrElse(Empty)
    }
  }

  def userIsLocked(provider: String, username: String): Boolean = {
    val result: Boolean = findRow(provider, username) match {
      case Some(row) =>
        if (row.attempts > maxBadLoginAttempts.toInt) true
        else UserLocksProvider.isLocked(provider, username)
      case _ =>
        UserLocksProvider.isLocked(provider, username)
    }
    logger.debug(s"userIsLocked result for $username is $result")
    result
  }

  def resetBadLoginAttempts(provider: String, username: String): Unit = {
    findRow(provider, username) match {
      case Some(row) =>
        val ts = new Timestamp(now.getTime)
        DoobieUtil.runQuery(
          sql"""UPDATE mappedbadloginattempt
                SET mbadattemptssincelastsuccessorreset = 0, mlastfailuredate = $ts
                WHERE id = ${row.id}""".update.run
        )
      case None =>
        Empty
    }
  }

}
