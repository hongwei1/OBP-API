package code.bankattribute

import code.api.util.{APIUtil, DoobieUtil}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.enums.BankAttributeType
import com.openbankproject.commons.model.{BankAttributeTrait, BankId}
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import scala.concurrent.Future


object DoobieBankAttributeProvider extends BankAttributeProviderTrait {

  private case class AttrRow(
    bankid_ : Option[String],
    bankattributeid: Option[String],
    name: Option[String],
    type_c: Option[String],
    value: Option[String],
    isactive: Option[Boolean]
  )

  private case class DoobieBankAttribute(row: AttrRow) extends BankAttributeTrait {
    override def bankId: BankId                            = BankId(row.bankid_.getOrElse(""))
    override def bankAttributeId: String                   = row.bankattributeid.getOrElse("")
    override def name: String                              = row.name.getOrElse("")
    override def attributeType: BankAttributeType.Value   =
      BankAttributeType.withName(row.type_c.getOrElse("STRING"))
    override def value: String                             = row.value.getOrElse("")
    override def isActive: Option[Boolean]                 = row.isactive
  }

  private def nn(s: String): String = if (s == null) "" else s

  // H2 stores the column as TYPE_C because TYPE is a reserved word; Doobie queries use type_c.
  private val selectCols: Fragment =
    fr"""SELECT bankid_, bankattributeid, name, type_c, value, isactive FROM bankattribute"""

  private def findById(id: String): Option[DoobieBankAttribute] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE bankattributeid = ${nn(id)} LIMIT 1")
        .query[AttrRow].option).map(DoobieBankAttribute(_))

  override def getBankAttributesFromProvider(bankId: BankId): Future[Box[List[BankAttributeTrait]]] = Future {
    tryo {
      DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE bankid_ = ${nn(bankId.value)}")
          .query[AttrRow].to[List]).map(DoobieBankAttribute(_))
    }
  }

  override def getBankAttributeById(bankAttributeId: String): Future[Box[BankAttributeTrait]] = Future {
    findById(bankAttributeId) match {
      case Some(a) => Full(a)
      case None    => Empty
    }
  }

  override def createOrUpdateBankAttribute(bankId: BankId,
                                            bankAttributeId: Option[String],
                                            name: String,
                                            attributType: BankAttributeType.Value,
                                            value: String,
                                            isActive: Option[Boolean]): Future[Box[BankAttributeTrait]] = {
    val activeVal = isActive.getOrElse(true)
    bankAttributeId match {
      case Some(id) => Future {
        findById(id) match {
          case Some(_) =>
            tryo {
              DoobieUtil.runQuery(
                sql"""UPDATE bankattribute
                      SET bankid_ = ${nn(bankId.value)}, name = ${nn(name)},
                          type_c = ${attributType.toString}, value = ${nn(value)}, isactive = $activeVal
                      WHERE bankattributeid = ${nn(id)}""".update.run)
              DoobieBankAttribute(AttrRow(
                Some(nn(bankId.value)), Some(nn(id)), Some(nn(name)),
                Some(attributType.toString), Some(nn(value)), Some(activeVal)
              ))
            }
          case None => Empty
        }
      }
      case None => Future {
        tryo {
          val newId = APIUtil.generateUUID()
          DoobieUtil.runQuery(
            sql"""INSERT INTO bankattribute (bankid_, bankattributeid, name, type_c, value, isactive)
                  VALUES (${nn(bankId.value)}, $newId, ${nn(name)},
                          ${attributType.toString}, ${nn(value)}, $activeVal)""".update.run)
          DoobieBankAttribute(AttrRow(
            Some(nn(bankId.value)), Some(newId), Some(nn(name)),
            Some(attributType.toString), Some(nn(value)), Some(activeVal)
          ))
        }
      }
    }
  }

  override def deleteBankAttribute(bankAttributeId: String): Future[Box[Boolean]] = Future {
    Full(DoobieUtil.runQuery(
      sql"DELETE FROM bankattribute WHERE bankattributeid = ${nn(bankAttributeId)}".update.run) > 0)
  }
}
