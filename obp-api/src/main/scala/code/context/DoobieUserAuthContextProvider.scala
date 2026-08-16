package code.context

import code.api.util.{APIUtil, DoobieUtil}
import code.api.util.ErrorMessages.{CreateUserAuthContextError, DeleteUserAuthContextNotFound}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.{BasicUserAuthContext, UserAuthContext}
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import java.sql.Timestamp
import java.util.Date
import scala.collection.immutable.List
import scala.concurrent.Future

object DoobieUserAuthContextProvider extends UserAuthContextProvider {

  private case class CtxRow(
    muserauthcontextid: Option[String],
    muserid: Option[String],
    mkey: Option[String],
    mvalue: Option[String],
    mconsumerid: Option[String],
    createdat: Option[Timestamp]
  )

  private case class DoobieUserAuthContext(row: CtxRow) extends UserAuthContext {
    override def userAuthContextId: String = row.muserauthcontextid.getOrElse("")
    override def userId: String            = row.muserid.getOrElse("")
    override def key: String               = row.mkey.getOrElse("")
    override def value: String             = row.mvalue.getOrElse("")
    override def timeStamp: Date           = row.createdat.map(ts => new Date(ts.getTime)).getOrElse(new Date())
    override def consumerId: String        = row.mconsumerid.getOrElse("")
  }

  private def nn(s: String): String = if (s == null) "" else s

  private val selectCols: Fragment =
    fr"SELECT muserauthcontextid, muserid, mkey, mvalue, mconsumerid, createdat FROM mappeduserauthcontext"

  private def findByKey(userId: String, key: String): Option[DoobieUserAuthContext] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE muserid = ${nn(userId)} AND mkey = ${nn(key)} LIMIT 1")
        .query[CtxRow].option).map(DoobieUserAuthContext(_))

  private def insertRow(userId: String, key: String, value: String, consumerId: String, newId: String): DoobieUserAuthContext = {
    val now = new Timestamp(System.currentTimeMillis())
    DoobieUtil.runQuery(
      sql"""INSERT INTO mappeduserauthcontext (muserauthcontextid, muserid, mkey, mvalue, mconsumerid, createdat, updatedat)
            VALUES ($newId, ${nn(userId)}, ${nn(key)}, ${nn(value)}, ${nn(consumerId)}, $now, $now)""".update.run)
    DoobieUserAuthContext(CtxRow(Some(newId), Some(nn(userId)), Some(nn(key)), Some(nn(value)), Some(nn(consumerId)), Some(now)))
  }

  override def createUserAuthContext(userId: String, key: String, value: String, consumerId: String): Future[Box[UserAuthContext]] =
    Future {
      tryo {
        if (consumerId == null || consumerId.isEmpty)
          throw new RuntimeException(s"$CreateUserAuthContextError current consumerId is empty here.")
        insertRow(userId, key, value, consumerId, APIUtil.generateUUID())
      }
    }

  override def getUserAuthContexts(userId: String): Future[Box[List[UserAuthContext]]] =
    Future { getUserAuthContextsBox(userId) }

  override def getUserAuthContextsBox(userId: String): Box[List[UserAuthContext]] =
    tryo {
      DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE muserid = ${nn(userId)}")
          .query[CtxRow].to[List]).map(DoobieUserAuthContext(_))
    }

  // Replicates Lift behaviour: existing entries are returned unchanged (no value update).
  override def createOrUpdateUserAuthContexts(userId: String, userAuthContexts: List[BasicUserAuthContext]): Box[List[UserAuthContext]] =
    tryo {
      val distinct = userAuthContexts.distinct
      val existing = DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE muserid = ${nn(userId)}")
          .query[CtxRow].to[List]).map(DoobieUserAuthContext(_))
      val existingKeys = existing.map(_.key).toSet

      val toCreate = distinct.filter(ac => !existingKeys.contains(ac.key))
      val toKeep   = existing.filter(ac => distinct.exists(_.key == ac.key))

      val created = toCreate.map { ac =>
        insertRow(userId, ac.key, ac.value, "", APIUtil.generateUUID())
      }
      toKeep ::: created
    }

  override def deleteUserAuthContexts(userId: String): Future[Box[Boolean]] =
    Future {
      tryo {
        DoobieUtil.runQuery(
          sql"DELETE FROM mappeduserauthcontext WHERE muserid = ${nn(userId)}".update.run) >= 0
      }
    }

  override def deleteUserAuthContextById(userAuthContextId: String): Future[Box[Boolean]] =
    Future {
      val exists = DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE muserauthcontextid = ${nn(userAuthContextId)} LIMIT 1")
          .query[CtxRow].option)
      exists match {
        case Some(_) =>
          tryo {
            DoobieUtil.runQuery(
              sql"DELETE FROM mappeduserauthcontext WHERE muserauthcontextid = ${nn(userAuthContextId)}".update.run) > 0
          }
        case None => Empty ?~! DeleteUserAuthContextNotFound
      }
    }
}
