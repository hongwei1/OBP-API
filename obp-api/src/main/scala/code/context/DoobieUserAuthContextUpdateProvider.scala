package code.context

import code.api.util.{APIUtil, DoobieUtil, ErrorMessages, SecureRandomUtil}
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.{UserAuthContextUpdate, UserAuthContextUpdateStatus}
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Failure, Full}
import net.liftweb.util.Helpers
import net.liftweb.util.Helpers.tryo

import scala.compat.Platform
import scala.concurrent.Future

object DoobieUserAuthContextUpdateProvider extends UserAuthContextUpdateProvider with MdcLoggable {

  private case class AuthRow(
    muserauthcontextupdateid: Option[String],
    muserid: Option[String],
    mconsumerid: Option[String],
    mkey: Option[String],
    mvalue: Option[String],
    mchallenge: Option[String],
    mstatus: Option[String],
    createdat: Option[java.sql.Timestamp]
  )

  private case class DoobieUserAuthContextUpdate(row: AuthRow) extends UserAuthContextUpdate {
    override def userAuthContextUpdateId: String = row.muserauthcontextupdateid.getOrElse("")
    override def userId: String                  = row.muserid.getOrElse("")
    override def consumerId: String              = row.mconsumerid.getOrElse("")
    override def key: String                     = row.mkey.getOrElse("")
    override def value: String                   = row.mvalue.getOrElse("")
    override def challenge: String               = row.mchallenge.getOrElse("")
    override def status: String                  = row.mstatus.getOrElse("")
  }

  private def nn(s: String): String = if (s == null) "" else s

  private val selectCols: Fragment =
    fr"SELECT muserauthcontextupdateid, muserid, mconsumerid, mkey, mvalue, mchallenge, mstatus, createdat FROM mappeduserauthcontextupdate"

  private def findById(id: String): Option[AuthRow] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE muserauthcontextupdateid = ${nn(id)} LIMIT 1")
        .query[AuthRow].option)

  override def createUserAuthContextUpdates(userId: String, consumerId: String, key: String, value: String): Future[Box[UserAuthContextUpdate]] =
    Future {
      tryo {
        val newId = APIUtil.generateUUID()
        // Reproduce the Lift MappedUserAuthContextUpdate.mChallenge.defaultValue verbatim
        val challenge = SecureRandomUtil.csprng.nextInt(99999999).toString()
        val status = UserAuthContextUpdateStatus.INITIATED.toString
        val now = new java.sql.Timestamp(System.currentTimeMillis())
        DoobieUtil.runQuery(
          sql"""INSERT INTO mappeduserauthcontextupdate
                  (muserauthcontextupdateid, muserid, mconsumerid, mkey, mvalue, mchallenge, mstatus, createdat, updatedat)
                VALUES ($newId, ${nn(userId)}, ${nn(consumerId)}, ${nn(key)}, ${nn(value)}, $challenge, $status, $now, $now)""".update.run)
        DoobieUserAuthContextUpdate(AuthRow(
          Some(newId), Some(nn(userId)), Some(nn(consumerId)), Some(nn(key)), Some(nn(value)),
          Some(challenge), Some(status), Some(now)
        ))
      }
    }

  override def getUserAuthContextUpdates(userId: String): Future[Box[List[UserAuthContextUpdate]]] = Future {
    getUserAuthContextUpdatesBox(userId)
  }

  override def getUserAuthContextUpdatesBox(userId: String): Box[List[UserAuthContextUpdate]] = {
    tryo {
      DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE muserid = ${nn(userId)}")
          .query[AuthRow].to[List]).map(DoobieUserAuthContextUpdate(_))
    }
  }

  override def deleteUserAuthContextUpdates(userId: String): Future[Box[Boolean]] =
    Future {
      tryo {
        DoobieUtil.runQuery(
          sql"DELETE FROM mappeduserauthcontextupdate WHERE muserid = ${nn(userId)}".update.run)
        true
      }
    }

  override def deleteUserAuthContextUpdateById(userAuthContextId: String): Future[Box[Boolean]] =
    Future {
      findById(userAuthContextId) match {
        case Some(_) =>
          Full(DoobieUtil.runQuery(
            sql"DELETE FROM mappeduserauthcontextupdate WHERE muserauthcontextupdateid = ${nn(userAuthContextId)}".update.run) > 0)
        case None =>
          Empty ?~! ErrorMessages.DeleteUserAuthContextNotFound
      }
    }

  override def checkAnswer(consentId: String, challenge: String): Future[Box[UserAuthContextUpdate]] = Future {
    findById(consentId) match {
      case Some(row) =>
        val createDateTime: java.util.Date = row.createdat.getOrElse(new java.sql.Timestamp(System.currentTimeMillis()))
        val challengeTTL: Long = Helpers.seconds(APIUtil.userAuthContextUpdateRequestChallengeTtl)
        val expiredDateTime: Long = createDateTime.getTime + challengeTTL
        val currentTime: Long = Platform.currentTime

        if (expiredDateTime > currentTime)
          row.mstatus.getOrElse("") match {
            case value if value == UserAuthContextUpdateStatus.INITIATED.toString =>
              val status = if (row.mchallenge.getOrElse("") == challenge) UserAuthContextUpdateStatus.ACCEPTED.toString else UserAuthContextUpdateStatus.REJECTED.toString
              tryo {
                DoobieUtil.runQuery(
                  sql"UPDATE mappeduserauthcontextupdate SET mstatus = $status, updatedat = ${new java.sql.Timestamp(System.currentTimeMillis())} WHERE muserauthcontextupdateid = ${nn(consentId)}".update.run)
                DoobieUserAuthContextUpdate(row.copy(mstatus = Some(status)))
              }
            case _ =>
              Full(DoobieUserAuthContextUpdate(row))
          }
        else {
          Failure(s"${ErrorMessages.OneTimePasswordExpired} Current expiration time is ${APIUtil.userAuthContextUpdateRequestChallengeTtl} seconds")
        }
      case None =>
        Empty ?~! ErrorMessages.UserAuthContextUpdateNotFound
    }
  }
}
