package code.entitlement

import code.api.dynamic.entity.helper.DynamicEntityInfo
import code.api.util.ApiRole.{CanCreateEntitlementAtAnyBank, CanCreateEntitlementAtOneBank}
import code.api.util.{APIUtil, DoobieUtil, ErrorMessages, NotificationUtil}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Failure, Full}
import net.liftweb.util.Helpers.tryo

import scala.collection.immutable.List
import scala.concurrent.Future

object DoobieEntitlementsProvider extends EntitlementProvider {

  // Table "mappedentitlement" (Lift class name lowercased). Custom dbColumnName overrides:
  // mGroupId → group_id, mProcess → process, entitlement_request_id → entitlement_request_id.
  private case class EntRow(
    mentitlementid: Option[String],
    mbankid: Option[String],
    muserid: Option[String],
    mrolename: Option[String],
    mcreatedbyprocess: Option[String],
    group_id: Option[String],
    process: Option[String],
    entitlement_request_id: Option[String]
  )

  private case class DoobieEntitlement(row: EntRow) extends Entitlement {
    override def entitlementId: String = row.mentitlementid.getOrElse("")
    override def bankId: String = row.mbankid.getOrElse("")
    override def userId: String = row.muserid.getOrElse("")
    override def roleName: String = row.mrolename.getOrElse("")
    override def createdByProcess: String = {
      val p = row.mcreatedbyprocess.orNull
      if (p == null || p.isEmpty) "manual" else p
    }
    override def groupId: Option[String] = {
      val gid = row.group_id.orNull
      if (gid == null || gid.isEmpty) None else Some(gid)
    }
    override def process: Option[String] = {
      val p = row.process.orNull
      if (p == null || p.isEmpty) None else Some(p)
    }
    override def entitlementRequestId: Option[String] = row.entitlement_request_id match {
      case Some(uuid) if uuid.nonEmpty && uuid != "00000000-0000-0000-0000-000000000000" => Some(uuid)
      case _ => None
    }
  }

  private def nn(s: String): String = if (s == null) "" else s

  private val selectCols: Fragment =
    fr"""SELECT mentitlementid, mbankid, muserid, mrolename, mcreatedbyprocess,
                group_id, process, entitlement_request_id
         FROM mappedentitlement"""

  private def findOne(where: Fragment): Box[DoobieEntitlement] =
    DoobieUtil.runQuery((selectCols ++ where ++ fr"LIMIT 1").query[EntRow].option) match {
      case Some(r) => Full(DoobieEntitlement(r))
      case None    => Empty
    }

  private def findList(where: Fragment): List[DoobieEntitlement] =
    DoobieUtil.runQuery((selectCols ++ where).query[EntRow].to[List]).map(DoobieEntitlement(_))

  override def getEntitlement(bankId: String, userId: String, roleName: String): Box[Entitlement] =
    findOne(fr"WHERE mbankid = ${nn(bankId)} AND muserid = ${nn(userId)} AND mrolename = ${nn(roleName)}")

  override def getEntitlementById(entitlementId: String): Box[Entitlement] =
    findOne(fr"WHERE mentitlementid = ${nn(entitlementId)}")

  override def getEntitlementsByUserId(userId: String): Box[List[Entitlement]] =
    Some(findList(fr"WHERE muserid = ${nn(userId)} ORDER BY updatedat DESC"))

  override def getEntitlementsByUserIdFuture(userId: String): Future[Box[List[Entitlement]]] =
    Future { getEntitlementsByUserId(userId) }

  override def getEntitlementsByBankId(bankId: String): Future[Box[List[Entitlement]]] =
    Future { Some(findList(fr"WHERE mbankid = ${nn(bankId)} ORDER BY muserid DESC")) }

  override def getEntitlements(): Box[List[Entitlement]] =
    Some(findList(fr"ORDER BY updatedat DESC"))

  override def getEntitlementsByRole(roleName: String): Box[List[Entitlement]] =
    Some(findList(fr"WHERE mrolename = ${nn(roleName)} ORDER BY updatedat DESC"))

  override def getEntitlementsFuture(): Future[Box[List[Entitlement]]] =
    Future { getEntitlements() }

  override def getEntitlementsByRoleFuture(roleName: String): Future[Box[List[Entitlement]]] =
    Future {
      if (roleName == null || roleName.isEmpty) getEntitlements()
      else getEntitlementsByRole(roleName)
    }

  override def getEntitlementsByGroupId(groupId: String): Future[Box[List[Entitlement]]] =
    Future { Some(findList(fr"WHERE group_id = ${nn(groupId)} ORDER BY updatedat DESC")) }

  override def deleteEntitlement(entitlement: Box[Entitlement]): Box[Boolean] =
    for {
      findEntitlement <- entitlement
      bankId <- Some(findEntitlement.bankId)
      userId <- Some(findEntitlement.userId)
      roleName <- Some(findEntitlement.roleName)
      foundEntitlement <- findOne(
        fr"WHERE mbankid = ${nn(bankId)} AND muserid = ${nn(userId)} AND mrolename = ${nn(roleName)}")
    } yield {
      DoobieUtil.runQuery(
        sql"DELETE FROM mappedentitlement WHERE mentitlementid = ${nn(foundEntitlement.entitlementId)}".update.run) > 0
    }

  override def deleteDynamicEntityEntitlement(entityName: String, bankId: Option[String]): Box[Boolean] = {
    val roleNames = DynamicEntityInfo.roleNames(entityName, bankId)
    deleteEntitlements(roleNames)
  }

  override def deleteEntitlements(entityNames: List[String]): Box[Boolean] =
    tryo {
      if (entityNames.isEmpty) {
        true
      } else {
        val inFrag = entityNames.map(n => fr"${nn(n)}").reduceLeft((a, b) => a ++ fr"," ++ b)
        DoobieUtil.runQuery(
          (fr"DELETE FROM mappedentitlement WHERE mrolename IN (" ++ inFrag ++ fr")").update.run) >= 0
      }
    }

  override def addEntitlement(bankId: String,
                              userId: String,
                              roleName: String,
                              createdByProcess: String = "manual",
                              grantorUserId: Option[String] = None,
                              groupId: Option[String] = None,
                              process: Option[String] = None): Box[Entitlement] = {
    def addEntitlementToUser(): Full[Entitlement] = {
      val newId = APIUtil.generateUUID()
      val now = new java.sql.Timestamp(System.currentTimeMillis())
      val groupIdStr = nn(groupId.getOrElse(""))
      val processStr = nn(process.getOrElse(""))
      DoobieUtil.runQuery(
        sql"""INSERT INTO mappedentitlement
                (mentitlementid, mbankid, muserid, mrolename, mcreatedbyprocess, group_id, process, createdat, updatedat)
              VALUES ($newId, ${nn(bankId)}, ${nn(userId)}, ${nn(roleName)}, ${nn(createdByProcess)},
                      $groupIdStr, $processStr, $now, $now)""".update.run)
      val addEntitlement = DoobieEntitlement(EntRow(
        Some(newId), Some(nn(bankId)), Some(nn(userId)), Some(nn(roleName)), Some(nn(createdByProcess)),
        Some(groupIdStr), Some(processStr), None))
      // When a role is Granted, we should send an email to the Recipient telling them they have been granted the role.
      NotificationUtil.sendEmailRegardingAssignedRole(userId, addEntitlement)
      Full(addEntitlement)
    }
    grantorUserId match {
      case Some(grantor) =>
        val grantorEntitlements = findList(fr"WHERE muserid = ${nn(grantor)}")
        val canCreateEntitlementAtAnyBank =
          grantorEntitlements.exists(e => e.roleName == CanCreateEntitlementAtAnyBank)
        val canCreateEntitlementAtOneBank =
          grantorEntitlements.exists(e => e.roleName == CanCreateEntitlementAtOneBank && e.bankId == bankId)
        if (canCreateEntitlementAtAnyBank || canCreateEntitlementAtOneBank) {
          addEntitlementToUser()
        } else {
          Failure(ErrorMessages.EntitlementCannotBeGrantedGrantorIssue)
        }
      case None =>
        addEntitlementToUser()
    }
  }
}
