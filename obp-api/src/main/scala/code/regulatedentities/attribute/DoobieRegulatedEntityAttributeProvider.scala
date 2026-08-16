package code.regulatedentities.attribute

import code.api.util.APIUtil
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.enums.RegulatedEntityAttributeType
import com.openbankproject.commons.model.{RegulatedEntityAttributeTrait, RegulatedEntityId}
import code.api.util.DoobieUtil
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import scala.concurrent.Future

object DoobieRegulatedEntityAttributeProvider extends RegulatedEntityAttributeProviderTrait {

  private case class AttrRow(
    regulatedentityid: Option[String],
    regulatedentityattributeid: Option[String],
    name: Option[String],
    type_c: Option[String],
    value: Option[String],
    isactive: Option[Boolean]
  )

  private case class DoobieRegulatedEntityAttribute(row: AttrRow) extends RegulatedEntityAttributeTrait {
    override def regulatedEntityId: RegulatedEntityId          = RegulatedEntityId(row.regulatedentityid.getOrElse(""))
    override def regulatedEntityAttributeId: String            = row.regulatedentityattributeid.getOrElse("")
    override def name: String                                  = row.name.getOrElse("")
    override def attributeType: RegulatedEntityAttributeType.Value =
      RegulatedEntityAttributeType.withName(row.type_c.getOrElse("STRING"))
    override def value: String                                 = row.value.getOrElse("")
    override def isActive: Option[Boolean]                     = row.isactive
  }

  private def nn(s: String): String = if (s == null) "" else s

  private val selectCols: Fragment =
    fr"SELECT regulatedentityid, regulatedentityattributeid, name, type_c, value, isactive FROM regulatedentityattribute"

  override def getRegulatedEntityAttributes(regulatedEntityId: RegulatedEntityId): Future[Box[List[RegulatedEntityAttributeTrait]]] =
    Future {
      tryo {
        DoobieUtil.runQuery(
          (selectCols ++ fr"WHERE regulatedentityid = ${nn(regulatedEntityId.value)}")
            .query[AttrRow].to[List]).map(DoobieRegulatedEntityAttribute(_))
      }
    }

  override def getRegulatedEntityAttributeById(regulatedEntityAttributeId: String): Future[Box[RegulatedEntityAttributeTrait]] =
    Future {
      DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE regulatedentityattributeid = ${nn(regulatedEntityAttributeId)} LIMIT 1")
          .query[AttrRow].option).map(DoobieRegulatedEntityAttribute(_)) match {
        case Some(a) => Full(a)
        case None    => Empty
      }
    }

  override def createOrUpdateRegulatedEntityAttribute(
    regulatedEntityId: RegulatedEntityId,
    regulatedEntityAttributeId: Option[String],
    name: String,
    attributeType: RegulatedEntityAttributeType.Value,
    value: String,
    isActive: Option[Boolean]
  ): Future[Box[RegulatedEntityAttributeTrait]] = {
    val active = isActive.getOrElse(true)
    regulatedEntityAttributeId match {
      case Some(id) => Future {
        DoobieUtil.runQuery(
          (selectCols ++ fr"WHERE regulatedentityattributeid = ${nn(id)} LIMIT 1")
            .query[AttrRow].option) match {
          case Some(existing) =>
            tryo {
              DoobieUtil.runQuery(
                sql"""UPDATE regulatedentityattribute
                      SET regulatedentityid = ${nn(regulatedEntityId.value)}, name = ${nn(name)},
                          type_c = ${attributeType.toString}, value = ${nn(value)}, isactive = $active
                      WHERE regulatedentityattributeid = ${nn(id)}""".update.run)
              DoobieRegulatedEntityAttribute(existing.copy(
                regulatedentityid = Some(nn(regulatedEntityId.value)),
                name = Some(nn(name)),
                type_c = Some(attributeType.toString),
                value = Some(nn(value)),
                isactive = Some(active)
              ))
            }
          case None => Empty
        }
      }
      case None => Future {
        tryo {
          val newId = APIUtil.generateUUID()
          DoobieUtil.runQuery(
            sql"""INSERT INTO regulatedentityattribute
                    (regulatedentityid, regulatedentityattributeid, name, type_c, value, isactive)
                  VALUES (${nn(regulatedEntityId.value)}, $newId, ${nn(name)},
                          ${attributeType.toString}, ${nn(value)}, $active)""".update.run)
          DoobieRegulatedEntityAttribute(AttrRow(
            Some(nn(regulatedEntityId.value)), Some(newId), Some(nn(name)),
            Some(attributeType.toString), Some(nn(value)), Some(active)
          ))
        }
      }
    }
  }

  override def deleteRegulatedEntityAttribute(regulatedEntityAttributeId: String): Future[Box[Boolean]] =
    Future {
      tryo {
        DoobieUtil.runQuery(
          sql"DELETE FROM regulatedentityattribute WHERE regulatedentityattributeid = ${nn(regulatedEntityAttributeId)}"
            .update.run) >= 0
      }
    }

  override def deleteRegulatedEntityAttributesByRegulatedEntityId(regulatedEntityId: RegulatedEntityId): Future[Box[Boolean]] =
    Future {
      tryo {
        DoobieUtil.runQuery(
          sql"DELETE FROM regulatedentityattribute WHERE regulatedentityid = ${nn(regulatedEntityId.value)}"
            .update.run) >= 0
      }
    }
}
