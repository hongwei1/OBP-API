package code.productattribute

import code.api.util.{APIUtil, DoobieUtil}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.enums.ProductAttributeType
import com.openbankproject.commons.model.{BankId, ProductAttribute, ProductCode}
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import scala.concurrent.Future

object DoobieProductAttributeProvider extends ProductAttributeProvider {

  private case class AttrRow(
    mbankid: Option[String],
    mcode: Option[String],
    mproductattributeid: Option[String],
    mname: Option[String],
    mtype: Option[String],
    mvalue: Option[String],
    isactive: Option[Boolean]
  )

  private case class DoobieProductAttribute(row: AttrRow) extends ProductAttribute {
    override def bankId: BankId                              = BankId(row.mbankid.getOrElse(""))
    override def productCode: ProductCode                    = ProductCode(row.mcode.getOrElse(""))
    override def productAttributeId: String                  = row.mproductattributeid.getOrElse("")
    override def name: String                                = row.mname.getOrElse("")
    override def attributeType: ProductAttributeType.Value   =
      ProductAttributeType.withName(row.mtype.getOrElse("STRING"))
    override def value: String                               = row.mvalue.getOrElse("")
    override def isActive: Option[Boolean]                   = row.isactive
  }

  private def nn(s: String): String = if (s == null) "" else s

  private val selectCols: Fragment =
    fr"SELECT mbankid, mcode, mproductattributeid, mname, mtype, mvalue, isactive FROM mappedproductattribute"

  private def findById(id: String): Option[DoobieProductAttribute] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE mproductattributeid = ${nn(id)} LIMIT 1")
        .query[AttrRow].option).map(DoobieProductAttribute(_))

  override def getProductAttributesFromProvider(bankId: BankId, productCode: ProductCode): Future[Box[List[ProductAttribute]]] = Future {
    tryo {
      DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE mbankid = ${nn(bankId.value)} AND mcode = ${nn(productCode.value)}")
          .query[AttrRow].to[List]).map(DoobieProductAttribute(_))
    }
  }

  override def getProductAttributeById(productAttributeId: String): Future[Box[ProductAttribute]] = Future {
    findById(productAttributeId) match {
      case Some(a) => Full(a)
      case None    => Empty
    }
  }

  override def createOrUpdateProductAttribute(bankId: BankId,
                                              productCode: ProductCode,
                                              productAttributeId: Option[String],
                                              name: String,
                                              attributeType: ProductAttributeType.Value,
                                              value: String,
                                              isActive: Option[Boolean]): Future[Box[ProductAttribute]] = {
    val activeVal = isActive.getOrElse(true)
    productAttributeId match {
      case Some(id) => Future {
        findById(id) match {
          case Some(_) =>
            tryo {
              DoobieUtil.runQuery(
                sql"""UPDATE mappedproductattribute
                      SET mbankid = ${nn(bankId.value)}, mcode = ${nn(productCode.value)},
                          mname = ${nn(name)}, mtype = ${attributeType.toString}, mvalue = ${nn(value)},
                          isactive = $activeVal
                      WHERE mproductattributeid = ${nn(id)}""".update.run)
              DoobieProductAttribute(AttrRow(
                Some(nn(bankId.value)), Some(nn(productCode.value)), Some(nn(id)),
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
            sql"""INSERT INTO mappedproductattribute (mbankid, mcode, mproductattributeid, mname, mtype, mvalue, isactive)
                  VALUES (${nn(bankId.value)}, ${nn(productCode.value)}, $newId,
                          ${nn(name)}, ${attributeType.toString}, ${nn(value)}, $activeVal)""".update.run)
          DoobieProductAttribute(AttrRow(
            Some(nn(bankId.value)), Some(nn(productCode.value)), Some(newId),
            Some(nn(name)), Some(attributeType.toString), Some(nn(value)), Some(activeVal)
          ))
        }
      }
    }
  }

  override def deleteProductAttribute(productAttributeId: String): Future[Box[Boolean]] = Future {
    Full(DoobieUtil.runQuery(
      sql"DELETE FROM mappedproductattribute WHERE mproductattributeid = ${nn(productAttributeId)}".update.run) > 0)
  }
}
