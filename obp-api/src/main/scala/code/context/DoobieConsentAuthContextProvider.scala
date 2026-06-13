package code.context

import code.api.util.ErrorMessages.DeleteUserAuthContextNotFound
import code.api.util.{APIUtil, DoobieUtil}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.{BasicUserAuthContext, ConsentAuthContext}
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import java.sql.Timestamp
import java.util.Date
import scala.collection.immutable.List
import scala.concurrent.Future

object DoobieConsentAuthContextProvider extends ConsentAuthContextProvider {

  // The `Key` Mapper field is stored as column `key_c` (KEY is a reserved word, like TYPE -> type_c).
  // Custom table name: MappedConsentAuthContext.dbTableName = "ConsentAuthContext".
  private case class CtxRow(
    consentauthcontextid: Option[String],
    consentid: Option[String],
    key_c: Option[String],
    value: Option[String],
    createdat: Option[Timestamp]
  )

  private case class DoobieConsentAuthContext(row: CtxRow) extends ConsentAuthContext {
    override def consentAuthContextId: String = row.consentauthcontextid.getOrElse("")
    override def consentId: String            = row.consentid.getOrElse("")
    override def key: String                  = row.key_c.getOrElse("")
    override def value: String                = row.value.getOrElse("")
    override def timeStamp: Date              = row.createdat.map(ts => new Date(ts.getTime)).getOrElse(new Date())
  }

  private def nn(s: String): String = if (s == null) "" else s

  private val selectCols: Fragment =
    fr"SELECT consentauthcontextid, consentid, key_c, value, createdat FROM consentauthcontext"

  private def findByConsentAndKey(consentId: String, key: String): Option[DoobieConsentAuthContext] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE consentid = ${nn(consentId)} AND key_c = ${nn(key)} LIMIT 1")
        .query[CtxRow].option).map(DoobieConsentAuthContext(_))

  private def insertRow(consentId: String, key: String, value: String): DoobieConsentAuthContext = {
    val newId = APIUtil.generateUUID()
    val now = new Timestamp(System.currentTimeMillis())
    DoobieUtil.runQuery(
      sql"""INSERT INTO consentauthcontext (consentauthcontextid, consentid, key_c, value, createdat, updatedat)
            VALUES ($newId, ${nn(consentId)}, ${nn(key)}, ${nn(value)}, $now, $now)""".update.run)
    DoobieConsentAuthContext(CtxRow(Some(newId), Some(nn(consentId)), Some(nn(key)), Some(nn(value)), Some(now)))
  }

  override def createConsentAuthContext(consentId: String, key: String, value: String): Future[Box[ConsentAuthContext]] =
    Future { tryo { insertRow(consentId, key, value) } }

  override def getConsentAuthContexts(consentId: String): Future[Box[List[ConsentAuthContext]]] =
    Future { getConsentAuthContextsBox(consentId) }

  override def getConsentAuthContextsBox(consentId: String): Box[List[ConsentAuthContext]] =
    tryo {
      DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE consentid = ${nn(consentId)}")
          .query[CtxRow].to[List]).map(DoobieConsentAuthContext(_))
    }

  // Creates or replaces only the provided contexts (does not delete others). Existing (consentId, key)
  // rows have their key+value updated; new ones are inserted. Returns updated ::: created (Lift order).
  override def createOrUpdateConsentAuthContexts(consentId: String, userAuthContexts: List[BasicUserAuthContext]): Box[List[ConsentAuthContext]] =
    tryo {
      val distinct = userAuthContexts.distinct
      val toCreate = distinct.filter(ac => findByConsentAndKey(consentId, ac.key).isEmpty)
      val toUpdate = distinct diff toCreate

      val updated: List[ConsentAuthContext] = toUpdate.flatMap { ac =>
        findByConsentAndKey(consentId, ac.key).map { _ =>
          val now = new Timestamp(System.currentTimeMillis())
          DoobieUtil.runQuery(
            sql"""UPDATE consentauthcontext SET key_c = ${nn(ac.key)}, value = ${nn(ac.value)}, updatedat = $now
                  WHERE consentid = ${nn(consentId)} AND key_c = ${nn(ac.key)}""".update.run)
          findByConsentAndKey(consentId, ac.key).getOrElse(
            DoobieConsentAuthContext(CtxRow(None, Some(nn(consentId)), Some(nn(ac.key)), Some(nn(ac.value)), Some(now))))
        }
      }
      val created: List[ConsentAuthContext] = toCreate.map(ac => insertRow(consentId, ac.key, ac.value))
      updated ::: created
    }

  override def deleteConsentAuthContexts(consentId: String): Future[Box[Boolean]] =
    Future {
      tryo {
        DoobieUtil.runQuery(
          sql"DELETE FROM consentauthcontext WHERE consentid = ${nn(consentId)}".update.run) >= 0
      }
    }

  override def deleteConsentAuthContextById(consentAuthContextId: String): Future[Box[Boolean]] =
    Future {
      DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE consentauthcontextid = ${nn(consentAuthContextId)} LIMIT 1").query[CtxRow].option) match {
        case Some(_) =>
          Full(DoobieUtil.runQuery(
            sql"DELETE FROM consentauthcontext WHERE consentauthcontextid = ${nn(consentAuthContextId)}".update.run) > 0)
        case None => Empty ?~! DeleteUserAuthContextNotFound
      }
    }
}
