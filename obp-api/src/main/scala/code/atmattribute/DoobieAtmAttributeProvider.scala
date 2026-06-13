package code.atmattribute

import code.api.util.{APIUtil, DoobieUtil}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.enums.AtmAttributeType
import com.openbankproject.commons.model.{AtmAttributeTrait, AtmId, BankId}
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import scala.concurrent.Future

object DoobieAtmAttributeProvider extends AtmAttributeProviderTrait {

  private case class AttrRow(
    bankid: Option[String],
    atmid: Option[String],
    atmattributeid: Option[String],
    name: Option[String],
    type_c: Option[String],
    value: Option[String],
    isactive: Option[Boolean]
  )

  private case class DoobieAtmAttribute(row: AttrRow) extends AtmAttributeTrait {
    override def bankId: BankId                            = BankId(row.bankid.getOrElse(""))
    override def atmId: AtmId                              = AtmId(row.atmid.getOrElse(""))
    override def atmAttributeId: String                    = row.atmattributeid.getOrElse("")
    override def name: String                              = row.name.getOrElse("")
    override def attributeType: AtmAttributeType.Value     =
      AtmAttributeType.withName(row.type_c.getOrElse("STRING"))
    override def value: String                             = row.value.getOrElse("")
    override def isActive: Option[Boolean]                 = row.isactive
  }

  private def nn(s: String): String = if (s == null) "" else s

  // H2 stores the column as TYPE_C because TYPE is a reserved word.
  private val selectCols: Fragment =
    fr"SELECT bankid, atmid, atmattributeid, name, type_c, value, isactive FROM atmattribute"

  private def findById(id: String): Option[DoobieAtmAttribute] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE atmattributeid = ${nn(id)} LIMIT 1")
        .query[AttrRow].option).map(DoobieAtmAttribute(_))

  override def getAtmAttributesFromProvider(bankId: BankId, atmId: AtmId): Future[Box[List[AtmAttributeTrait]]] = Future {
    tryo {
      DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE bankid = ${nn(bankId.value)} AND atmid = ${nn(atmId.value)}")
          .query[AttrRow].to[List]).map(DoobieAtmAttribute(_))
    }
  }

  override def getAtmAttributeById(AtmAttributeId: String): Future[Box[AtmAttributeTrait]] = Future {
    findById(AtmAttributeId) match {
      case Some(a) => Full(a)
      case None    => Empty
    }
  }

  override def createOrUpdateAtmAttribute(bankId: BankId,
                                          atmId: AtmId,
                                          AtmAttributeId: Option[String],
                                          name: String,
                                          attributeType: AtmAttributeType.Value,
                                          value: String,
                                          isActive: Option[Boolean]): Future[Box[AtmAttributeTrait]] = {
    val activeVal = isActive.getOrElse(true)
    AtmAttributeId match {
      case Some(id) => Future {
        findById(id) match {
          case Some(_) =>
            tryo {
              DoobieUtil.runQuery(
                sql"""UPDATE atmattribute
                      SET bankid = ${nn(bankId.value)}, atmid = ${nn(atmId.value)},
                          name = ${nn(name)}, type_c = ${attributeType.toString},
                          value = ${nn(value)}, isactive = $activeVal
                      WHERE atmattributeid = ${nn(id)}""".update.run)
              DoobieAtmAttribute(AttrRow(
                Some(nn(bankId.value)), Some(nn(atmId.value)), Some(nn(id)),
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
            sql"""INSERT INTO atmattribute (bankid, atmid, atmattributeid, name, type_c, value, isactive)
                  VALUES (${nn(bankId.value)}, ${nn(atmId.value)}, $newId,
                          ${nn(name)}, ${attributeType.toString}, ${nn(value)}, $activeVal)""".update.run)
          DoobieAtmAttribute(AttrRow(
            Some(nn(bankId.value)), Some(nn(atmId.value)), Some(newId),
            Some(nn(name)), Some(attributeType.toString), Some(nn(value)), Some(activeVal)
          ))
        }
      }
    }
  }

  override def deleteAtmAttribute(AtmAttributeId: String): Future[Box[Boolean]] = Future {
    Full(DoobieUtil.runQuery(
      sql"DELETE FROM atmattribute WHERE atmattributeid = ${nn(AtmAttributeId)}".update.run) > 0)
  }

  override def deleteAtmAttributesByAtmId(atmId: AtmId): Future[Box[Boolean]] = Future {
    Full(DoobieUtil.runQuery(
      sql"DELETE FROM atmattribute WHERE atmid = ${nn(atmId.value)}".update.run) > 0)
  }
}
