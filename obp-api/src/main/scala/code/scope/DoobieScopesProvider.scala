package code.scope

import code.api.util.{APIUtil, DoobieUtil}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}

import java.sql.Timestamp
import scala.collection.immutable.List
import scala.concurrent.Future

object DoobieScopesProvider extends ScopeProvider {

  // Table "mappedscope": id, mconsumerid, mscopeid, mbankid, mrolename, createdat, updatedat.
  private case class ScopeRow(
    mscopeid: Option[String],
    mbankid: Option[String],
    mconsumerid: Option[String],
    mrolename: Option[String]
  )

  private case class DoobieScope(row: ScopeRow) extends Scope {
    override def scopeId: String = row.mscopeid.getOrElse("")
    override def bankId: String = row.mbankid.getOrElse("")
    override def consumerId: String = row.mconsumerid.getOrElse("")
    override def roleName: String = row.mrolename.getOrElse("")
  }

  private def nn(s: String): String = if (s == null) "" else s

  private val selectCols: Fragment =
    fr"SELECT mscopeid, mbankid, mconsumerid, mrolename FROM mappedscope"

  private def findOne(where: Fragment): Box[DoobieScope] =
    DoobieUtil.runQuery((selectCols ++ where ++ fr"LIMIT 1").query[ScopeRow].option) match {
      case Some(r) => Full(DoobieScope(r))
      case None    => Empty
    }

  private def findList(where: Fragment): List[Scope] =
    DoobieUtil.runQuery((selectCols ++ where).query[ScopeRow].to[List]).map(DoobieScope(_))

  override def getScope(bankId: String, consumerId: String, roleName: String): Box[Scope] =
    findOne(fr"WHERE mbankid = ${nn(bankId)} AND mconsumerid = ${nn(consumerId)} AND mrolename = ${nn(roleName)}")

  override def getScopeById(scopeId: String): Box[Scope] =
    findOne(fr"WHERE mscopeid = ${nn(scopeId)}")

  override def getScopesByConsumerId(consumerId: String): Box[List[Scope]] =
    Some(findList(fr"WHERE mconsumerid = ${nn(consumerId)} ORDER BY updatedat DESC"))

  override def getScopesByConsumerIdFuture(consumerId: String): Future[Box[List[Scope]]] =
    Future { getScopesByConsumerId(consumerId) }

  override def getScopes(): Box[List[Scope]] =
    Some(findList(fr"ORDER BY updatedat DESC"))

  override def getScopesFuture(): Future[Box[List[Scope]]] =
    Future { getScopes() }

  override def deleteScope(scope: Box[Scope]): Box[Boolean] =
    for {
      findScope <- scope
      bankId <- Some(findScope.bankId)
      consumerId <- Some(findScope.consumerId)
      roleName <- Some(findScope.roleName)
      foundScope <- findOne(
        fr"WHERE mbankid = ${nn(bankId)} AND mconsumerid = ${nn(consumerId)} AND mrolename = ${nn(roleName)}")
    } yield {
      DoobieUtil.runQuery(
        sql"DELETE FROM mappedscope WHERE mscopeid = ${nn(foundScope.scopeId)}".update.run) > 0
    }

  override def addScope(bankId: String, consumerId: String, roleName: String): Box[Scope] = {
    val newId = APIUtil.generateUUID()
    val now = new Timestamp(System.currentTimeMillis())
    DoobieUtil.runQuery(
      sql"""INSERT INTO mappedscope (mscopeid, mbankid, mconsumerid, mrolename, createdat, updatedat)
            VALUES ($newId, ${nn(bankId)}, ${nn(consumerId)}, ${nn(roleName)}, $now, $now)""".update.run)
    Some(DoobieScope(ScopeRow(Some(newId), Some(nn(bankId)), Some(nn(consumerId)), Some(nn(roleName)))))
  }
}
