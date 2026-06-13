package code.users

import code.api.util.ErrorMessages.UserAttributeNotFound
import code.api.util.{APIUtil, DoobieUtil}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.UserAttributeTrait
import com.openbankproject.commons.model.enums.UserAttributeType
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import java.sql.Timestamp
import java.util.Date
import scala.concurrent.Future

object DoobieUserAttributeProvider extends UserAttributeProvider {

  private case class AttrRow(
    userid: Option[String],
    userattributeid: Option[String],
    name: Option[String],
    type_c: Option[String],
    value: Option[String],
    ispersonal: Option[Boolean],
    createdat: Option[Timestamp]
  )

  private case class DoobieUserAttribute(row: AttrRow) extends UserAttributeTrait {
    override def userAttributeId: String                   = row.userattributeid.getOrElse("")
    override def userId: String                            = row.userid.getOrElse("")
    override def name: String                              = row.name.getOrElse("")
    override def attributeType: UserAttributeType.Value    =
      UserAttributeType.withName(row.type_c.getOrElse("STRING"))
    override def value: String                             = row.value.getOrElse("")
    override def insertDate: Date                          =
      row.createdat.map(ts => new Date(ts.getTime)).getOrElse(new Date())
    override def isPersonal: Boolean                       = row.ispersonal.getOrElse(false)
  }

  private def nn(s: String): String = if (s == null) "" else s

  // H2 stores the column as TYPE_C because TYPE is a reserved word.
  private val selectCols: Fragment =
    fr"SELECT userid, userattributeid, name, type_c, value, ispersonal, createdat FROM userattribute"

  private def findById(id: String): Option[DoobieUserAttribute] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE userattributeid = ${nn(id)} LIMIT 1")
        .query[AttrRow].option).map(DoobieUserAttribute(_))

  override def getUserAttributesByUser(userId: String): Future[Box[List[UserAttributeTrait]]] = Future {
    tryo {
      DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE userid = ${nn(userId)}")
          .query[AttrRow].to[List]).map(DoobieUserAttribute(_))
    }
  }

  override def getPersonalUserAttributes(userId: String): Future[Box[List[UserAttributeTrait]]] = Future {
    tryo {
      DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE userid = ${nn(userId)} AND ispersonal = true ORDER BY createdat DESC")
          .query[AttrRow].to[List]).map(DoobieUserAttribute(_))
    }
  }

  override def getNonPersonalUserAttributes(userId: String): Future[Box[List[UserAttributeTrait]]] = Future {
    tryo {
      DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE userid = ${nn(userId)} AND ispersonal = false ORDER BY createdat DESC")
          .query[AttrRow].to[List]).map(DoobieUserAttribute(_))
    }
  }

  override def getUserAttributesByUsers(userIds: List[String]): Future[Box[List[UserAttributeTrait]]] = Future {
    if (userIds.isEmpty) Full(Nil)
    else tryo {
      val idFragments = userIds.map(id => fr"${nn(id)}")
      val inList = idFragments.reduceLeft((a, b) => a ++ fr"," ++ b)
      DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE userid IN (" ++ inList ++ fr")")
          .query[AttrRow].to[List]).map(DoobieUserAttribute(_))
    }
  }

  override def deleteUserAttribute(userAttributeId: String): Future[Box[Boolean]] = {
    Future {
      findById(userAttributeId) match {
        case Some(_) => Full(DoobieUtil.runQuery(
          sql"DELETE FROM userattribute WHERE userattributeid = ${nn(userAttributeId)}".update.run) > 0)
        case None    => Empty ?~! UserAttributeNotFound
      }
    }
  }

  override def createOrUpdateUserAttribute(userId: String,
                                           userAttributeId: Option[String],
                                           name: String,
                                           attributeType: UserAttributeType.Value,
                                           value: String,
                                           isPersonal: Boolean): Future[Box[UserAttributeTrait]] = {
    val now = new Timestamp(System.currentTimeMillis())
    userAttributeId match {
      case Some(id) => Future {
        findById(id) match {
          case Some(existing) =>
            tryo {
              DoobieUtil.runQuery(
                sql"""UPDATE userattribute
                      SET userid = ${nn(userId)}, name = ${nn(name)},
                          type_c = ${attributeType.toString}, value = ${nn(value)},
                          updatedat = $now
                      WHERE userattributeid = ${nn(id)}""".update.run)
              // isPersonal is intentionally not updated (Lift had this commented out)
              DoobieUserAttribute(existing.row.copy(
                userid = Some(nn(userId)),
                name = Some(nn(name)),
                type_c = Some(attributeType.toString),
                value = Some(nn(value))
              ))
            }
          case None => Empty
        }
      }
      case None => Future {
        tryo {
          val newId = APIUtil.generateUUID()
          DoobieUtil.runQuery(
            sql"""INSERT INTO userattribute (userid, userattributeid, name, type_c, value, ispersonal, createdat, updatedat)
                  VALUES (${nn(userId)}, $newId, ${nn(name)},
                          ${attributeType.toString}, ${nn(value)}, $isPersonal, $now, $now)""".update.run)
          DoobieUserAttribute(AttrRow(
            Some(nn(userId)), Some(newId), Some(nn(name)),
            Some(attributeType.toString), Some(nn(value)), Some(isPersonal), Some(now)
          ))
        }
      }
    }
  }
}
