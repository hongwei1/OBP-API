package code.transactionattribute

import code.api.attributedefinition.AttributeDefinition
import code.api.util.{APIUtil, DoobieUtil}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.enums.{AttributeCategory, TransactionAttributeType}
import com.openbankproject.commons.model.{BankId, TransactionAttribute, TransactionId, ViewId}
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.mapper.By
import net.liftweb.util.Helpers.tryo

import scala.collection.immutable.List
import scala.concurrent.Future

object DoobieTransactionAttributeProvider extends TransactionAttributeProvider {

  private case class AttrRow(
    mbankid: Option[String],
    mtransactionid: Option[String],
    mtransactionattributeid: Option[String],
    mname: Option[String],
    mtype: Option[String],
    mvalue: Option[String]
  )

  private case class DoobieTransactionAttribute(row: AttrRow) extends TransactionAttribute {
    override def bankId: BankId                              = BankId(row.mbankid.getOrElse(""))
    override def transactionId: TransactionId                = TransactionId(row.mtransactionid.getOrElse(""))
    override def transactionAttributeId: String             = row.mtransactionattributeid.getOrElse("")
    override def name: String                               = row.mname.getOrElse("")
    override def attributeType: TransactionAttributeType.Value =
      TransactionAttributeType.withName(row.mtype.getOrElse("STRING"))
    override def value: String = row.mvalue.getOrElse("")
  }

  private def nn(s: String): String = if (s == null) "" else s

  private val selectCols: Fragment =
    fr"""SELECT mbankid, mtransactionid, mtransactionattributeid, mname, mtype, mvalue
         FROM mappedtransactionattribute"""

  private def findById(id: String): Option[DoobieTransactionAttribute] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE mtransactionattributeid = ${nn(id)} LIMIT 1")
        .query[AttrRow].option).map(DoobieTransactionAttribute(_))

  override def getTransactionAttributesFromProvider(transactionId: TransactionId): Future[Box[List[TransactionAttribute]]] = Future {
    tryo {
      DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE mtransactionid = ${nn(transactionId.value)}")
          .query[AttrRow].to[List]).map(DoobieTransactionAttribute(_))
    }
  }

  override def getTransactionAttributes(bankId: BankId, transactionId: TransactionId): Future[Box[List[TransactionAttribute]]] = Future {
    tryo {
      DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE mbankid = ${nn(bankId.value)} AND mtransactionid = ${nn(transactionId.value)}")
          .query[AttrRow].to[List]).map(DoobieTransactionAttribute(_))
    }
  }

  override def getTransactionAttributesCanBeSeenOnView(bankId: BankId,
                                                       transactionId: TransactionId,
                                                       viewId: ViewId): Future[Box[List[TransactionAttribute]]] = Future {
    val attributeDefinitions = AttributeDefinition.findAll(
      By(AttributeDefinition.BankId, bankId.value),
      By(AttributeDefinition.Category, AttributeCategory.Transaction.toString)
    ).filter(_.canBeSeenOnViews.exists(_ == viewId.value))
    val transactionAttributes = DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE mbankid = ${nn(bankId.value)} AND mtransactionid = ${nn(transactionId.value)}")
        .query[AttrRow].to[List]).map(DoobieTransactionAttribute(_))
    val filtered = for {
      definition <- attributeDefinitions
      attribute  <- transactionAttributes
      if definition.bankId.value == attribute.bankId.value && definition.name == attribute.name
    } yield attribute
    Full(filtered)
  }

  override def getTransactionsAttributesCanBeSeenOnView(bankId: BankId,
                                                        transactionIds: List[TransactionId],
                                                        viewId: ViewId): Future[Box[List[TransactionAttribute]]] = Future {
    val attributeDefinitions = AttributeDefinition.findAll(
      By(AttributeDefinition.BankId, bankId.value),
      By(AttributeDefinition.Category, AttributeCategory.Transaction.toString)
    ).filter(_.canBeSeenOnViews.exists(_ == viewId.value))
    val idValues = transactionIds.map(_.value)
    val allAttrs = DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE mbankid = ${nn(bankId.value)}")
        .query[AttrRow].to[List]).map(DoobieTransactionAttribute(_))
      .filter(a => idValues.contains(a.transactionId.value))
    val filtered = for {
      definition <- attributeDefinitions
      attribute  <- allAttrs
      if definition.bankId.value == attribute.bankId.value && definition.name == attribute.name
    } yield attribute
    Full(filtered)
  }

  override def getTransactionAttributeById(transactionAttributeId: String): Future[Box[TransactionAttribute]] = Future {
    findById(transactionAttributeId) match {
      case Some(a) => Full(a)
      case None    => Empty
    }
  }

  override def getTransactionIdsByAttributeNameValues(bankId: BankId, params: Map[String, List[String]]): Future[Box[List[String]]] = Future {
    tryo {
      if (params.isEmpty) {
        DoobieUtil.runQuery(
          fr"SELECT mtransactionid FROM mappedtransactionattribute WHERE mbankid = ${nn(bankId.value)}"
            .query[Option[String]].to[List]).flatten
      } else {
        val paramList = params.toList
        val conditions = paramList.flatMap { case (name, values) =>
          if (values.size == 1)
            List(fr"(mname = ${nn(name)} AND mvalue = ${nn(values.head)})")
          else {
            val inFr = values.map(v => fr0"${nn(v)}").reduce((a, b) => a ++ fr", " ++ b)
            List(fr"(mname = ${nn(name)} AND mvalue IN (" ++ inFr ++ fr"))")
          }
        }
        val orFr = conditions.reduce((a, b) => a ++ fr" OR " ++ b)
        DoobieUtil.runQuery(
          (fr"SELECT mtransactionid FROM mappedtransactionattribute WHERE mbankid = ${nn(bankId.value)} AND (" ++ orFr ++ fr")")
            .query[Option[String]].to[List]).flatten
      }
    }
  }

  override def createOrUpdateTransactionAttribute(bankId: BankId,
                                                  transactionId: TransactionId,
                                                  transactionAttributeId: Option[String],
                                                  name: String,
                                                  attributeType: TransactionAttributeType.Value,
                                                  value: String): Future[Box[TransactionAttribute]] = {
    transactionAttributeId match {
      case Some(id) => Future {
        findById(id) match {
          case Some(_) =>
            tryo {
              DoobieUtil.runQuery(
                sql"""UPDATE mappedtransactionattribute
                      SET mbankid = ${nn(bankId.value)}, mtransactionid = ${nn(transactionId.value)},
                          mname = ${nn(name)}, mtype = ${attributeType.toString}, mvalue = ${nn(value)}
                      WHERE mtransactionattributeid = ${nn(id)}""".update.run)
              DoobieTransactionAttribute(AttrRow(
                Some(nn(bankId.value)), Some(nn(transactionId.value)), Some(nn(id)),
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
            sql"""INSERT INTO mappedtransactionattribute (mbankid, mtransactionid, mtransactionattributeid, mname, mtype, mvalue)
                  VALUES (${nn(bankId.value)}, ${nn(transactionId.value)}, $newId,
                          ${nn(name)}, ${attributeType.toString}, ${nn(value)})""".update.run)
          DoobieTransactionAttribute(AttrRow(
            Some(nn(bankId.value)), Some(nn(transactionId.value)), Some(newId),
            Some(nn(name)), Some(attributeType.toString), Some(nn(value))
          ))
        }
      }
    }
  }

  override def createTransactionAttributes(bankId: BankId,
                                           transactionId: TransactionId,
                                           transactionAttributes: List[TransactionAttribute]): Future[Box[List[TransactionAttribute]]] = Future {
    tryo {
      transactionAttributes.map { attr =>
        val newId = APIUtil.generateUUID()
        DoobieUtil.runQuery(
          sql"""INSERT INTO mappedtransactionattribute (mbankid, mtransactionid, mtransactionattributeid, mname, mtype, mvalue)
                VALUES (${nn(bankId.value)}, ${nn(transactionId.value)}, $newId,
                        ${nn(attr.name)}, ${attr.attributeType.toString}, ${nn(attr.value)})""".update.run)
        DoobieTransactionAttribute(AttrRow(
          Some(nn(bankId.value)), Some(nn(transactionId.value)), Some(newId),
          Some(nn(attr.name)), Some(attr.attributeType.toString), Some(nn(attr.value))
        ))
      }
    }
  }

  override def deleteTransactionAttribute(transactionAttributeId: String): Future[Box[Boolean]] = Future {
    Full(DoobieUtil.runQuery(
      sql"DELETE FROM mappedtransactionattribute WHERE mtransactionattributeid = ${nn(transactionAttributeId)}".update.run) > 0)
  }
}
