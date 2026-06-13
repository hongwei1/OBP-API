package code.cardattribute

import code.api.util.{APIUtil, DoobieUtil}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.enums.CardAttributeType
import com.openbankproject.commons.model.{BankId, CardAttribute}
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import scala.concurrent.Future

object DoobieCardAttributeProvider extends CardAttributeProvider {

  private case class AttrRow(
    mbankid: Option[String],
    mcardid: Option[String],
    mcardattributeid: Option[String],
    mname: Option[String],
    mtype: Option[String],
    mvalue: Option[String]
  )

  private case class DoobieCardAttribute(row: AttrRow) extends CardAttribute {
    override def bankId: Option[BankId]          = row.mbankid.filter(_.nonEmpty).map(BankId(_))
    override def cardId: Option[String]           = row.mcardid.filter(_.nonEmpty)
    override def cardAttributeId: Option[String]  = row.mcardattributeid.filter(_.nonEmpty)
    override def name: String                     = row.mname.getOrElse("")
    override def attributeType: CardAttributeType.Value =
      CardAttributeType.withName(row.mtype.getOrElse("STRING"))
    override def value: String = row.mvalue.getOrElse("")
  }

  private def nn(s: String): String = if (s == null) "" else s
  private def nnOpt(o: Option[String]): String = o.map(nn).getOrElse("")

  private val selectCols: Fragment =
    fr"SELECT mbankid, mcardid, mcardattributeid, mname, mtype, mvalue FROM mappedcardattribute"

  private def findById(id: String): Option[DoobieCardAttribute] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE mcardattributeid = ${nn(id)} LIMIT 1")
        .query[AttrRow].option).map(DoobieCardAttribute(_))

  override def getCardAttributesFromProvider(cardId: String): Future[Box[List[CardAttribute]]] = Future {
    tryo {
      DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE mcardid = ${nn(cardId)}")
          .query[AttrRow].to[List]).map(DoobieCardAttribute(_))
    }
  }

  override def getCardAttributeById(cardAttributeId: String): Future[Box[CardAttribute]] = Future {
    findById(cardAttributeId) match {
      case Some(a) => Full(a)
      case None    => Empty
    }
  }

  override def createOrUpdateCardAttribute(bankId: Option[BankId],
                                           cardId: Option[String],
                                           cardAttributeId: Option[String],
                                           name: String,
                                           attributeType: CardAttributeType.Value,
                                           value: String): Future[Box[CardAttribute]] = {
    val bankIdStr = bankId.map(_.value).getOrElse("")
    val cardIdStr = cardId.getOrElse("")
    cardAttributeId match {
      case Some(id) => Future {
        findById(id) match {
          case Some(_) =>
            tryo {
              DoobieUtil.runQuery(
                sql"""UPDATE mappedcardattribute
                      SET mbankid = $bankIdStr, mcardid = $cardIdStr,
                          mname = ${nn(name)}, mtype = ${attributeType.toString}, mvalue = ${nn(value)}
                      WHERE mcardattributeid = ${nn(id)}""".update.run)
              DoobieCardAttribute(AttrRow(
                Some(bankIdStr), Some(cardIdStr), Some(nn(id)),
                Some(nn(name)), Some(attributeType.toString), Some(nn(value))
              ))
            }
          case None => Empty
        }
      }
      case None => Future {
        tryo {
          val newId = APIUtil.generateUUID()
          DoobieUtil.runQuery(
            sql"""INSERT INTO mappedcardattribute (mbankid, mcardid, mcardattributeid, mname, mtype, mvalue)
                  VALUES ($bankIdStr, $cardIdStr, $newId, ${nn(name)}, ${attributeType.toString}, ${nn(value)})""".update.run)
          DoobieCardAttribute(AttrRow(
            Some(bankIdStr), Some(cardIdStr), Some(newId),
            Some(nn(name)), Some(attributeType.toString), Some(nn(value))
          ))
        }
      }
    }
  }

  override def deleteCardAttribute(cardAttributeId: String): Future[Box[Boolean]] = Future {
    Full(DoobieUtil.runQuery(
      sql"DELETE FROM mappedcardattribute WHERE mcardattributeid = ${nn(cardAttributeId)}".update.run) > 0)
  }
}
