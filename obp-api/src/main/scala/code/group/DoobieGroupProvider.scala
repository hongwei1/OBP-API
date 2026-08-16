package code.group

import code.api.util.{APIUtil, DoobieUtil}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import java.sql.Timestamp
import scala.collection.immutable.List
import scala.concurrent.Future

object DoobieGroupProvider extends GroupProvider {

  // Custom dbTableName "GroupOfRoles" → table groupofroles. listofroles is a comma-separated text column.
  private case class GroupRow(
    groupid: Option[String],
    bankid: Option[String],
    groupname: Option[String],
    groupdescription: Option[String],
    listofroles: Option[String],
    isenabled: Option[Boolean]
  )

  private case class DoobieGroup(row: GroupRow) extends GroupTrait {
    override def groupId: String = row.groupid.getOrElse("")
    override def bankId: Option[String] = {
      val id = row.bankid.orNull
      if (id == null || id.isEmpty) None else Some(id)
    }
    override def groupName: String = row.groupname.getOrElse("")
    override def groupDescription: String = row.groupdescription.getOrElse("")
    override def listOfRoles: List[String] = {
      val rolesStr = row.listofroles.orNull
      if (rolesStr == null || rolesStr.isEmpty) List.empty
      else rolesStr.split(",").map(_.trim).filter(_.nonEmpty).toList
    }
    override def isEnabled: Boolean = row.isenabled.getOrElse(false)
  }

  private def nn(s: String): String = if (s == null) "" else s

  private val selectCols: Fragment =
    fr"SELECT groupid, bankid, groupname, groupdescription, listofroles, isenabled FROM groupofroles"

  private def findRow(groupId: String): Box[GroupRow] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE groupid = ${nn(groupId)} LIMIT 1").query[GroupRow].option) match {
      case Some(r) => Full(r)
      case None    => Empty
    }

  private def findList(where: Fragment): List[GroupTrait] =
    DoobieUtil.runQuery((selectCols ++ where).query[GroupRow].to[List]).map(DoobieGroup(_))

  override def createGroup(bankId: Option[String],
                           groupName: String,
                           groupDescription: String,
                           listOfRoles: List[String],
                           isEnabled: Boolean): Box[GroupTrait] =
    tryo {
      val newId = APIUtil.generateUUID()
      val bankIdStr = nn(bankId.getOrElse(""))
      val rolesStr = nn(listOfRoles.mkString(","))
      val now = new Timestamp(System.currentTimeMillis())
      DoobieUtil.runQuery(
        sql"""INSERT INTO groupofroles
                (groupid, bankid, groupname, groupdescription, listofroles, isenabled, createdat, updatedat)
              VALUES ($newId, $bankIdStr, ${nn(groupName)}, ${nn(groupDescription)}, $rolesStr, $isEnabled, $now, $now)""".update.run)
      DoobieGroup(GroupRow(Some(newId), Some(bankIdStr), Some(nn(groupName)),
        Some(nn(groupDescription)), Some(rolesStr), Some(isEnabled)))
    }

  override def getGroup(groupId: String): Box[GroupTrait] =
    findRow(groupId).map(DoobieGroup(_))

  override def getGroupsByBankId(bankId: Option[String]): Future[Box[List[GroupTrait]]] =
    Future {
      tryo {
        bankId match {
          case Some(id) => findList(fr"WHERE bankid = ${nn(id)}")
          case None     => findList(fr"WHERE bankid = ''")
        }
      }
    }

  override def getAllGroups(): Future[Box[List[GroupTrait]]] =
    Future { tryo { findList(Fragment.empty) } }

  override def updateGroup(groupId: String,
                           groupName: Option[String],
                           groupDescription: Option[String],
                           listOfRoles: Option[List[String]],
                           isEnabled: Option[Boolean]): Box[GroupTrait] =
    findRow(groupId).flatMap { row =>
      tryo {
        val newName = nn(groupName.getOrElse(row.groupname.getOrElse("")))
        val newDesc = nn(groupDescription.getOrElse(row.groupdescription.getOrElse("")))
        val newRoles = nn(listOfRoles.map(_.mkString(",")).getOrElse(row.listofroles.getOrElse("")))
        val newEnabled = isEnabled.getOrElse(row.isenabled.getOrElse(false))
        val now = new Timestamp(System.currentTimeMillis())
        DoobieUtil.runQuery(
          sql"""UPDATE groupofroles
                SET groupname = $newName, groupdescription = $newDesc, listofroles = $newRoles,
                    isenabled = $newEnabled, updatedat = $now
                WHERE groupid = ${nn(groupId)}""".update.run)
        DoobieGroup(row.copy(
          groupname = Some(newName), groupdescription = Some(newDesc),
          listofroles = Some(newRoles), isenabled = Some(newEnabled)))
      }
    }

  override def deleteGroup(groupId: String): Box[Boolean] =
    findRow(groupId).flatMap { _ =>
      tryo {
        DoobieUtil.runQuery(
          sql"DELETE FROM groupofroles WHERE groupid = ${nn(groupId)}".update.run) > 0
      }
    }
}
