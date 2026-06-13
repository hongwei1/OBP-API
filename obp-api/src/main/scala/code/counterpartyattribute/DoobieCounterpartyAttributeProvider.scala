package code.counterpartyattribute

import code.api.util.{APIUtil, DoobieUtil}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.enums.CounterpartyAttributeType
import com.openbankproject.commons.model.{CounterpartyAttributeTrait, CounterpartyId}
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import scala.concurrent.Future

object DoobieCounterpartyAttributeProvider extends CounterpartyAttributeProviderTrait {

  private case class AttrRow(
    counterpartyid: Option[String],
    counterpartyattributeid: Option[String],
    name: Option[String],
    type_c: Option[String],
    value: Option[String],
    isactive: Option[Boolean]
  )

  private case class DoobieCounterpartyAttribute(row: AttrRow) extends CounterpartyAttributeTrait {
    override def counterpartyId: CounterpartyId                      = CounterpartyId(row.counterpartyid.getOrElse(""))
    override def counterpartyAttributeId: String                     = row.counterpartyattributeid.getOrElse("")
    override def name: String                                        = row.name.getOrElse("")
    override def attributeType: CounterpartyAttributeType.Value      =
      CounterpartyAttributeType.withName(row.type_c.getOrElse("STRING"))
    override def value: String                                       = row.value.getOrElse("")
    override def isActive: Option[Boolean]                           = row.isactive
  }

  private def nn(s: String): String = if (s == null) "" else s

  // H2 stores the column as TYPE_C because TYPE is a reserved word.
  private val selectCols: Fragment =
    fr"SELECT counterpartyid, counterpartyattributeid, name, type_c, value, isactive FROM counterpartyattribute"

  private def findById(id: String): Option[DoobieCounterpartyAttribute] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE counterpartyattributeid = ${nn(id)} LIMIT 1")
        .query[AttrRow].option).map(DoobieCounterpartyAttribute(_))

  override def getCounterpartyAttributes(counterpartyId: CounterpartyId): Future[Box[List[CounterpartyAttributeTrait]]] = Future {
    tryo {
      DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE counterpartyid = ${nn(counterpartyId.value)}")
          .query[AttrRow].to[List]).map(DoobieCounterpartyAttribute(_))
    }
  }

  override def getCounterpartyAttributeById(counterpartyAttributeId: String): Future[Box[CounterpartyAttributeTrait]] = Future {
    findById(counterpartyAttributeId) match {
      case Some(a) => Full(a)
      case None    => Empty
    }
  }

  override def createOrUpdateCounterpartyAttribute(
    counterpartyId: CounterpartyId,
    counterpartyAttributeId: Option[String],
    name: String,
    attributeType: CounterpartyAttributeType.Value,
    value: String,
    isActive: Option[Boolean]
  ): Future[Box[CounterpartyAttributeTrait]] = {
    val activeVal = isActive.getOrElse(true)
    counterpartyAttributeId match {
      case Some(id) => Future {
        findById(id) match {
          case Some(_) =>
            tryo {
              DoobieUtil.runQuery(
                sql"""UPDATE counterpartyattribute
                      SET counterpartyid = ${nn(counterpartyId.value)}, name = ${nn(name)},
                          type_c = ${attributeType.toString}, value = ${nn(value)},
                          isactive = $activeVal
                      WHERE counterpartyattributeid = ${nn(id)}""".update.run)
              DoobieCounterpartyAttribute(AttrRow(
                Some(nn(counterpartyId.value)), Some(nn(id)),
                Some(nn(name)), Some(attributeType.toString), Some(nn(value)), Some(activeVal)
              ))
            }
          case None => Empty
        }
      }
      case None => Future {
        tryo {
          val newId = APIUtil.generateUUID()
          DoobieUtil.runQuery(
            sql"""INSERT INTO counterpartyattribute (counterpartyid, counterpartyattributeid, name, type_c, value, isactive)
                  VALUES (${nn(counterpartyId.value)}, $newId,
                          ${nn(name)}, ${attributeType.toString}, ${nn(value)}, $activeVal)""".update.run)
          DoobieCounterpartyAttribute(AttrRow(
            Some(nn(counterpartyId.value)), Some(newId),
            Some(nn(name)), Some(attributeType.toString), Some(nn(value)), Some(activeVal)
          ))
        }
      }
    }
  }

  override def deleteCounterpartyAttribute(counterpartyAttributeId: String): Future[Box[Boolean]] = Future {
    Full(DoobieUtil.runQuery(
      sql"DELETE FROM counterpartyattribute WHERE counterpartyattributeid = ${nn(counterpartyAttributeId)}".update.run) > 0)
  }

  override def deleteCounterpartyAttributesByCounterpartyId(counterpartyId: CounterpartyId): Future[Box[Boolean]] = Future {
    Full(DoobieUtil.runQuery(
      sql"DELETE FROM counterpartyattribute WHERE counterpartyid = ${nn(counterpartyId.value)}".update.run) > 0)
  }
}
