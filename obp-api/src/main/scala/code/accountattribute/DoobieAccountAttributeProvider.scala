package code.accountattribute

import code.api.Constant.{PARAM_LOCALE, PARAM_TIMESTAMP}
import code.api.attributedefinition.AttributeDefinition
import code.api.util.{APIUtil, DoobieUtil}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.enums.{AccountAttributeType, AttributeCategory}
import com.openbankproject.commons.model.{AccountAttribute, AccountId, BankId, BankIdAccountId, ProductAttribute, ProductCode, ViewId}
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.mapper.By
import net.liftweb.util.Helpers.tryo

import scala.collection.immutable.List
import scala.concurrent.Future

object DoobieAccountAttributeProvider extends AccountAttributeProvider {

  private case class AttrRow(
    mbankidid: Option[String],
    maccountid: Option[String],
    mcode: Option[String],
    maccountattributeid: Option[String],
    mname: Option[String],
    mtype: Option[String],
    mvalue: Option[String],
    mproductinstancecode: Option[String]
  )

  private case class DoobieAccountAttribute(row: AttrRow) extends AccountAttribute {
    override def bankId: BankId                              = BankId(row.mbankidid.getOrElse(""))
    override def accountId: AccountId                        = AccountId(row.maccountid.getOrElse(""))
    override def productCode: ProductCode                    = ProductCode(row.mcode.getOrElse(""))
    override def accountAttributeId: String                  = row.maccountattributeid.getOrElse("")
    override def name: String                                = row.mname.getOrElse("")
    override def attributeType: AccountAttributeType.Value   =
      AccountAttributeType.withName(row.mtype.getOrElse("STRING"))
    override def value: String                               = row.mvalue.getOrElse("")
    override def productInstanceCode: Option[String]         = row.mproductinstancecode.filter(_.nonEmpty)
  }

  private def nn(s: String): String = if (s == null) "" else s

  private val selectCols: Fragment =
    fr"""SELECT mbankidid, maccountid, mcode, maccountattributeid, mname, mtype, mvalue, mproductinstancecode
         FROM mappedaccountattribute"""

  private def findById(id: String): Option[DoobieAccountAttribute] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE maccountattributeid = ${nn(id)} LIMIT 1")
        .query[AttrRow].option).map(DoobieAccountAttribute(_))

  override def getAccountAttributesFromProvider(accountId: AccountId, productCode: ProductCode): Future[Box[List[AccountAttribute]]] = Future {
    tryo {
      DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE maccountid = ${nn(accountId.value)} AND mcode = ${nn(productCode.value)}")
          .query[AttrRow].to[List]).map(DoobieAccountAttribute(_))
    }
  }

  override def getAccountAttributesByAccount(bankId: BankId, accountId: AccountId): Future[Box[List[AccountAttribute]]] = Future {
    tryo {
      DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE mbankidid = ${nn(bankId.value)} AND maccountid = ${nn(accountId.value)}")
          .query[AttrRow].to[List]).map(DoobieAccountAttribute(_))
    }
  }

  override def getAccountAttributesByAccountCanBeSeenOnView(bankId: BankId,
                                                             accountId: AccountId,
                                                             viewId: ViewId): Future[Box[List[AccountAttribute]]] = Future {
    val attributeDefinitions = AttributeDefinition.findAll(
      By(AttributeDefinition.BankId, bankId.value),
      By(AttributeDefinition.Category, AttributeCategory.Account.toString)
    ).filter(_.canBeSeenOnViews.exists(_ == viewId.value))
    val accountAttributes = DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE mbankidid = ${nn(bankId.value)} AND maccountid = ${nn(accountId.value)}")
        .query[AttrRow].to[List]).map(DoobieAccountAttribute(_))
    val filtered = for {
      definition <- attributeDefinitions
      attribute  <- accountAttributes
      if definition.bankId.value == attribute.bankId.value && definition.name == attribute.name
    } yield attribute
    Full(filtered)
  }

  override def getAccountAttributesByAccountsCanBeSeenOnView(accounts: List[BankIdAccountId],
                                                              viewId: ViewId): Future[Box[List[AccountAttribute]]] = Future {
    val attributeDefinitions = AttributeDefinition.findAll(
      By(AttributeDefinition.BankId, accounts.headOption.map(_.bankId.value).getOrElse("")),
      By(AttributeDefinition.Category, AttributeCategory.Account.toString)
    ).filter(_.canBeSeenOnViews.exists(_ == viewId.value))
    val accountIdValues = accounts.map(_.accountId.value)
    val allAttrs = DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE mbankidid = ${accounts.headOption.map(a => nn(a.bankId.value)).getOrElse("")}")
        .query[AttrRow].to[List]).map(DoobieAccountAttribute(_))
      .filter(a => accounts.exists(acc =>
        (acc.bankId.value, acc.accountId.value) == (a.bankId.value, a.accountId.value)))
    val filtered = for {
      definition <- attributeDefinitions
      attribute  <- allAttrs
      if definition.bankId.value == attribute.bankId.value && definition.name == attribute.name
    } yield attribute
    Full(filtered)
  }

  override def getAccountAttributeById(accountAttributeId: String): Future[Box[AccountAttribute]] = Future {
    findById(accountAttributeId) match {
      case Some(a) => Full(a)
      case None    => Empty
    }
  }

  override def createOrUpdateAccountAttribute(bankId: BankId,
                                               accountId: AccountId,
                                               productCode: ProductCode,
                                               accountAttributeId: Option[String],
                                               name: String,
                                               attributeType: AccountAttributeType.Value,
                                               value: String,
                                               productInstanceCode: Option[String]): Future[Box[AccountAttribute]] = {
    val pic = productInstanceCode.getOrElse("")
    accountAttributeId match {
      case Some(id) => Future {
        findById(id) match {
          case Some(_) =>
            tryo {
              DoobieUtil.runQuery(
                sql"""UPDATE mappedaccountattribute
                      SET mbankidid = ${nn(bankId.value)}, maccountid = ${nn(accountId.value)},
                          mcode = ${nn(productCode.value)}, mname = ${nn(name)},
                          mtype = ${attributeType.toString}, mvalue = ${nn(value)},
                          mproductinstancecode = $pic
                      WHERE maccountattributeid = ${nn(id)}""".update.run)
              DoobieAccountAttribute(AttrRow(
                Some(nn(bankId.value)), Some(nn(accountId.value)), Some(nn(productCode.value)),
                Some(nn(id)), Some(nn(name)), Some(attributeType.toString), Some(nn(value)), Some(pic)
              ))
            }
          case None => Empty
        }
      }
      case None => Future {
        tryo {
          val newId = APIUtil.generateUUID()
          DoobieUtil.runQuery(
            sql"""INSERT INTO mappedaccountattribute
                  (mbankidid, maccountid, mcode, maccountattributeid, mname, mtype, mvalue, mproductinstancecode)
                  VALUES (${nn(bankId.value)}, ${nn(accountId.value)}, ${nn(productCode.value)}, $newId,
                          ${nn(name)}, ${attributeType.toString}, ${nn(value)}, $pic)""".update.run)
          DoobieAccountAttribute(AttrRow(
            Some(nn(bankId.value)), Some(nn(accountId.value)), Some(nn(productCode.value)),
            Some(newId), Some(nn(name)), Some(attributeType.toString), Some(nn(value)), Some(pic)
          ))
        }
      }
    }
  }

  override def createAccountAttributes(bankId: BankId,
                                        accountId: AccountId,
                                        productCode: ProductCode,
                                        accountAttributes: List[ProductAttribute],
                                        productInstanceCode: Option[String]): Future[Box[List[AccountAttribute]]] = Future {
    val pic = productInstanceCode.getOrElse("")
    tryo {
      accountAttributes.map { attr =>
        val newId = APIUtil.generateUUID()
        DoobieUtil.runQuery(
          sql"""INSERT INTO mappedaccountattribute
                (mbankidid, maccountid, mcode, maccountattributeid, mname, mtype, mvalue, mproductinstancecode)
                VALUES (${nn(bankId.value)}, ${nn(accountId.value)}, ${nn(productCode.value)}, $newId,
                        ${nn(attr.name)}, ${attr.attributeType.toString}, ${nn(attr.value)}, $pic)""".update.run)
        DoobieAccountAttribute(AttrRow(
          Some(nn(bankId.value)), Some(nn(accountId.value)), Some(nn(productCode.value)),
          Some(newId), Some(nn(attr.name)), Some(attr.attributeType.toString), Some(nn(attr.value)), Some(pic)
        ))
      }
    }
  }

  override def deleteAccountAttribute(accountAttributeId: String): Future[Box[Boolean]] = Future {
    Full(DoobieUtil.runQuery(
      sql"DELETE FROM mappedaccountattribute WHERE maccountattributeid = ${nn(accountAttributeId)}".update.run) > 0)
  }

  override def getAccountIdsByParams(bankId: BankId, params: Map[String, List[String]]): Future[Box[List[String]]] = Future {
    val paramFiltered = params
      .filterNot(_._1 == PARAM_TIMESTAMP)
      .filterNot(_._1 == PARAM_LOCALE)
    tryo {
      if (paramFiltered.isEmpty) {
        DoobieUtil.runQuery(
          fr"SELECT maccountid FROM mappedaccountattribute WHERE mbankidid = ${nn(bankId.value)}"
            .query[Option[String]].to[List]).flatten
      } else {
        val paramList = paramFiltered.toList
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
          (fr"SELECT maccountid FROM mappedaccountattribute WHERE mbankidid = ${nn(bankId.value)} AND (" ++ orFr ++ fr")")
            .query[Option[String]].to[List]).flatten
      }
    }
  }
}
