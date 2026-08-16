package code.loginattempts

import code.api.util.{APIUtil, DoobieUtil}
import code.userlocks.UserLocksProvider
import code.util.Helper.MdcLoggable
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Failure, Full}
import net.liftweb.mapper.By
import net.liftweb.util.Helpers._

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
    username.isEmpty() match {
      case true => // Not a valid case. GitLab issue 389
        logger.warn(s"Username is empty: incrementBadLoginAttempts(username=$username, provider=$provider")
      case false =>
        logger.debug(s"Hello from incrementBadLoginAttempts with $username")

        // Atomically increment the counter; if no row exists yet, create one.
        // The create path is itself a check-then-insert: two concurrent first-time bad logins both
        // see rowsUpdated==0, so wrap in tryo to absorb the UniqueIndex violation from the loser.
        val rowsUpdated = code.bankconnectors.DoobieBadLoginAttemptQueries.incrementBadLoginAttempts(provider, username)
        if (rowsUpdated == 0) {
          tryo {
            MappedBadLoginAttempt.create
              .mUsername(username)
              .Provider(provider)
              .mLastFailureDate(now)
              .mBadAttemptsSinceLastSuccessOrReset(1)
              .save
          }
          logger.debug(s"incrementBadLoginAttempts created loginAttempt")
        } else {
          logger.debug(s"incrementBadLoginAttempts atomically incremented for $username (rows=$rowsUpdated)")
        }
    }
  }

  def getOrCreateBadLoginStatus(provider: String, username: String): Box[BadLoginAttempt] = {
    MappedBadLoginAttempt.find(
      By(MappedBadLoginAttempt.Provider, provider),
      By(MappedBadLoginAttempt.mUsername, username)
    ) match {
      case full @ Full(_) => full
      case _ =>
        // .or(Full(saveMe())) evaluates saveMe eagerly — two concurrent first-time callers
        // both get Empty and both call saveMe; the loser hits UniqueIndex(Provider, mUsername).
        tryo {
          MappedBadLoginAttempt.create
            .mUsername(username)
            .Provider(provider)
            .mLastFailureDate(now)
            .mBadAttemptsSinceLastSuccessOrReset(0)
            .saveMe()
        } match {
          case full @ Full(_) => full
          case Failure(_, _, _) =>
            // UniqueIndex violation from concurrent insert — re-fetch the committed row
            MappedBadLoginAttempt.find(
              By(MappedBadLoginAttempt.Provider, provider),
              By(MappedBadLoginAttempt.mUsername, username)
            )
          case other => other
        }
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
