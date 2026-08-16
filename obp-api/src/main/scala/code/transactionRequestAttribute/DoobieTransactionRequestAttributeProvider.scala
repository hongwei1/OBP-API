package code.transactionRequestAttribute

import code.api.attributedefinition.AttributeDefinition
import code.api.util.{APIUtil, DoobieUtil}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.enums.{AttributeCategory, TransactionRequestAttributeType}
import com.openbankproject.commons.model.{BankId, TransactionRequestAttributeJsonV400, TransactionRequestAttributeTrait, TransactionRequestId, ViewId}
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.mapper.By
import net.liftweb.util.Helpers.tryo

import scala.collection.immutable.List
import scala.concurrent.Future

object DoobieTransactionRequestAttributeProvider extends TransactionRequestAttributeProvider {

  private case class AttrRow(
    bankid: Option[String],
    transactionrequestid: Option[String],
    transactionrequestattributeid: Option[String],
    name: Option[String],
    type_c: Option[String],
    value: Option[String],
    ispersonal: Option[Boolean]
  )

  private case class DoobieTransactionRequestAttribute(row: AttrRow) extends TransactionRequestAttributeTrait {
    override def bankId: BankId                                           = BankId(row.bankid.getOrElse(""))
    override def transactionRequestId: TransactionRequestId               = TransactionRequestId(row.transactionrequestid.getOrElse(""))
    override def transactionRequestAttributeId: String                    = row.transactionrequestattributeid.getOrElse("")
    override def name: String                                             = row.name.getOrElse("")
    override def attributeType: TransactionRequestAttributeType.Value     =
      TransactionRequestAttributeType.withName(row.type_c.getOrElse("STRING"))
    override def value: String                                            = row.value.getOrElse("")
    override def isPersonal: Boolean                                      = row.ispersonal.getOrElse(false)
  }

  private def nn(s: String): String = if (s == null) "" else s

  private val selectCols: Fragment =
    fr"SELECT bankid, transactionrequestid, transactionrequestattributeid, name, type_c, value, ispersonal FROM transactionrequestattribute"

  private def findById(id: String): Option[DoobieTransactionRequestAttribute] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE transactionrequestattributeid = ${nn(id)} LIMIT 1")
        .query[AttrRow].option).map(DoobieTransactionRequestAttribute(_))

  override def getTransactionRequestAttributesFromProvider(transactionRequestId: TransactionRequestId): Future[Box[List[TransactionRequestAttributeTrait]]] =
    Future {
      tryo {
        DoobieUtil.runQuery(
          (selectCols ++ fr"WHERE transactionrequestid = ${nn(transactionRequestId.value)}")
            .query[AttrRow].to[List]).map(DoobieTransactionRequestAttribute(_))
      }
    }

  override def getTransactionRequestAttributes(bankId: BankId,
                                               transactionRequestId: TransactionRequestId): Future[Box[List[TransactionRequestAttributeTrait]]] =
    Future {
      tryo {
        DoobieUtil.runQuery(
          (selectCols ++ fr"WHERE bankid = ${nn(bankId.value)} AND transactionrequestid = ${nn(transactionRequestId.value)}")
            .query[AttrRow].to[List]).map(DoobieTransactionRequestAttribute(_))
      }
    }

  override def getTransactionRequestAttributesCanBeSeenOnView(bankId: BankId,
                                                              transactionRequestId: TransactionRequestId,
                                                              viewId: ViewId): Future[Box[List[TransactionRequestAttributeTrait]]] =
    Future {
      // Note: Lift used AttributeCategory.Account here — preserved for bug-compatibility
      val attributeDefinitions = AttributeDefinition.findAll(
        By(AttributeDefinition.BankId, bankId.value),
        By(AttributeDefinition.Category, AttributeCategory.Account.toString)
      ).filter(_.canBeSeenOnViews.exists(_ == viewId.value))
      val transactionRequestAttributes = DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE bankid = ${nn(bankId.value)} AND transactionrequestid = ${nn(transactionRequestId.value)}")
          .query[AttrRow].to[List]).map(DoobieTransactionRequestAttribute(_))
      val filtered = for {
        definition <- attributeDefinitions
        attribute  <- transactionRequestAttributes
        if definition.bankId.value == attribute.bankId.value && definition.name == attribute.name
      } yield attribute
      Full(filtered)
    }

  override def getTransactionRequestAttributeById(transactionRequestAttributeId: String): Future[Box[TransactionRequestAttributeTrait]] =
    Future {
      findById(transactionRequestAttributeId) match {
        case Some(a) => Full(a)
        case None    => Empty
      }
    }

  override def getTransactionRequestIdsByAttributeNameValues(bankId: BankId, params: Map[String, List[String]], isPersonal: Boolean): Future[Box[List[String]]] =
    getByAttributeNameValues(bankId, params, isPersonal).map(_.map(_.map(_.transactionRequestId.value)))

  override def getByAttributeNameValues(bankId: BankId, params: Map[String, List[String]], isPersonal: Boolean): Future[Box[List[TransactionRequestAttributeTrait]]] =
    Future {
      tryo {
        if (params.isEmpty) {
          DoobieUtil.runQuery(
            (selectCols ++ fr"WHERE bankid = ${nn(bankId.value)} AND ispersonal = true")
              .query[AttrRow].to[List]).map(DoobieTransactionRequestAttribute(_))
        } else {
          val conditions = params.toList.map { case (n, values) =>
            if (values.size == 1)
              fr"(name = ${nn(n)} AND value = ${nn(values.head)})"
            else {
              val inFr = values.map(v => fr0"${nn(v)}").reduce((a, b) => a ++ fr", " ++ b)
              fr"(name = ${nn(n)} AND value IN (" ++ inFr ++ fr"))"
            }
          }
          val orFr = conditions.reduce((a, b) => a ++ fr" OR " ++ b)
          DoobieUtil.runQuery(
            (selectCols ++ fr"WHERE bankid = ${nn(bankId.value)} AND ispersonal = true AND (" ++ orFr ++ fr")")
              .query[AttrRow].to[List]).map(DoobieTransactionRequestAttribute(_))
        }
      }
    }

  override def createOrUpdateTransactionRequestAttribute(bankId: BankId,
                                                         transactionRequestId: TransactionRequestId,
                                                         transactionRequestAttributeId: Option[String],
                                                         name: String,
                                                         attributeType: TransactionRequestAttributeType.Value,
                                                         value: String): Future[Box[TransactionRequestAttributeTrait]] = {
    transactionRequestAttributeId match {
      case Some(id) => Future {
        findById(id) match {
          case Some(_) =>
            tryo {
              DoobieUtil.runQuery(
                sql"""UPDATE transactionrequestattribute
                      SET bankid = ${nn(bankId.value)}, transactionrequestid = ${nn(transactionRequestId.value)},
                          name = ${nn(name)}, type_c = ${attributeType.toString}, value = ${nn(value)}
                      WHERE transactionrequestattributeid = ${nn(id)}""".update.run)
              DoobieTransactionRequestAttribute(AttrRow(
                Some(nn(bankId.value)), Some(nn(transactionRequestId.value)), Some(nn(id)),
                Some(nn(name)), Some(attributeType.toString), Some(nn(value)), None
              ))
            }
          case None => Empty
        }
      }
      case None => Future {
        tryo {
          val newId = APIUtil.generateUUID()
          DoobieUtil.runQuery(
            sql"""INSERT INTO transactionrequestattribute
                    (bankid, transactionrequestid, transactionrequestattributeid, name, type_c, value)
                  VALUES (${nn(bankId.value)}, ${nn(transactionRequestId.value)}, $newId,
                          ${nn(name)}, ${attributeType.toString}, ${nn(value)})""".update.run)
          DoobieTransactionRequestAttribute(AttrRow(
            Some(nn(bankId.value)), Some(nn(transactionRequestId.value)), Some(newId),
            Some(nn(name)), Some(attributeType.toString), Some(nn(value)), None
          ))
        }
      }
    }
  }

  override def createTransactionRequestAttributes(bankId: BankId,
                                                  transactionRequestId: TransactionRequestId,
                                                  transactionRequestAttributes: List[TransactionRequestAttributeJsonV400],
                                                  isPersonal: Boolean): Future[Box[List[TransactionRequestAttributeTrait]]] =
    Future {
      tryo {
        transactionRequestAttributes.map { attr =>
          val newId = APIUtil.generateUUID()
          DoobieUtil.runQuery(
            sql"""INSERT INTO transactionrequestattribute
                    (bankid, transactionrequestid, transactionrequestattributeid, name, type_c, value, ispersonal)
                  VALUES (${nn(bankId.value)}, ${nn(transactionRequestId.value)}, $newId,
                          ${nn(attr.name)}, ${nn(attr.attribute_type)}, ${nn(attr.value)}, $isPersonal)""".update.run)
          DoobieTransactionRequestAttribute(AttrRow(
            Some(nn(bankId.value)), Some(nn(transactionRequestId.value)), Some(newId),
            Some(nn(attr.name)), Some(nn(attr.attribute_type)), Some(nn(attr.value)), Some(isPersonal)
          ))
        }
      }
    }

  override def deleteTransactionRequestAttribute(transactionRequestAttributeId: String): Future[Box[Boolean]] =
    Future {
      Full(DoobieUtil.runQuery(
        sql"DELETE FROM transactionrequestattribute WHERE transactionrequestattributeid = ${nn(transactionRequestAttributeId)}"
          .update.run) >= 0)
    }
}
