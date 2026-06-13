package code.productfee

import code.api.util.APIUtil
import code.api.util.DoobieUtil
import code.api.util.ErrorMessages.{CreateProductFeeError, UpdateProductFeeError}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.{BankId, ProductCode, ProductFeeTrait}
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import scala.collection.immutable.List
import scala.concurrent.Future
import scala.math.BigDecimal

object DoobieProductFeeProvider extends ProductFeeProvider {

  private case class ProductFeeRow(
    bankid: Option[String],
    productcode: Option[String],
    productfeeid: Option[String],
    name: Option[String],
    isactive: Option[Boolean],
    moreinfo: Option[String],
    currency: Option[String],
    amount: Option[BigDecimal],
    frequency: Option[String],
    type_c: Option[String]
  )

  private case class DoobieProductFee(row: ProductFeeRow) extends ProductFeeTrait {
    override def bankId: BankId           = BankId(row.bankid.getOrElse(""))
    override def productCode: ProductCode = ProductCode(row.productcode.getOrElse(""))
    override def productFeeId: String     = row.productfeeid.getOrElse("")
    override def name: String             = row.name.getOrElse("")
    override def isActive: Boolean        = row.isactive.getOrElse(true)
    override def moreInfo: String         = row.moreinfo.getOrElse("")
    override def currency: String         = row.currency.getOrElse("")
    override def amount: BigDecimal       = row.amount.getOrElse(BigDecimal(0))
    override def frequency: String        = row.frequency.getOrElse("")
    override def `type`: String           = row.type_c.getOrElse("")
  }

  private def nn(s: String): String = if (s == null) "" else s

  private val selectCols: Fragment =
    fr"""SELECT bankid, productcode, productfeeid, name, isactive, moreinfo, currency, amount, frequency, type_c
         FROM productfee"""

  private def findById(productFeeId: String): Option[DoobieProductFee] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE productfeeid = ${nn(productFeeId)} LIMIT 1")
        .query[ProductFeeRow].option).map(DoobieProductFee(_))

  override def getProductFeesFromProvider(bankId: BankId, productCode: ProductCode): Future[Box[List[ProductFeeTrait]]] =
    Future {
      Box !! DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE bankid = ${nn(bankId.value)} AND productcode = ${nn(productCode.value)}")
          .query[ProductFeeRow].to[List]).map(DoobieProductFee(_))
    }

  override def getProductFeeById(productFeeId: String): Future[Box[ProductFeeTrait]] = Future {
    findById(productFeeId) match {
      case Some(productFee) => Full(productFee)
      case None             => Empty
    }
  }

  override def createOrUpdateProductFee(
    bankId: BankId,
    productCode: ProductCode,
    productFeeId: Option[String],
    name: String,
    isActive: Boolean,
    moreInfo: String,
    currency: String,
    amount: BigDecimal,
    frequency: String,
    `type`: String
  ): Future[Box[ProductFeeTrait]] = {
    productFeeId match {
      case Some(id) => Future {
        findById(id) match {
          case Some(_) => tryo {
            DoobieUtil.runQuery(
              sql"""UPDATE productfee
                    SET bankid = ${nn(bankId.value)}, productcode = ${nn(productCode.value)}, name = ${nn(name)},
                        isactive = $isActive, moreinfo = ${nn(moreInfo)}, currency = ${nn(currency)},
                        amount = $amount, frequency = ${nn(frequency)}, type_c = ${nn(`type`)}
                    WHERE productfeeid = ${nn(id)}""".update.run)
            DoobieProductFee(ProductFeeRow(
              Some(nn(bankId.value)), Some(nn(productCode.value)), Some(nn(id)), Some(nn(name)),
              Some(isActive), Some(nn(moreInfo)), Some(nn(currency)), Some(amount),
              Some(nn(frequency)), Some(nn(`type`))
            )): ProductFeeTrait
          } ?~! s"$UpdateProductFeeError"
          case None => Empty
        }
      }
      case None => Future {
        tryo {
          val newId = APIUtil.generateUUID
          DoobieUtil.runQuery(
            sql"""INSERT INTO productfee
                    (bankid, productcode, productfeeid, name, isactive, moreinfo, currency, amount, frequency, type_c)
                  VALUES (${nn(bankId.value)}, ${nn(productCode.value)}, $newId, ${nn(name)},
                          $isActive, ${nn(moreInfo)}, ${nn(currency)}, $amount, ${nn(frequency)}, ${nn(`type`)})""".update.run)
          DoobieProductFee(ProductFeeRow(
            Some(nn(bankId.value)), Some(nn(productCode.value)), Some(newId), Some(nn(name)),
            Some(isActive), Some(nn(moreInfo)), Some(nn(currency)), Some(amount),
            Some(nn(frequency)), Some(nn(`type`))
          )): ProductFeeTrait
        } ?~! s"$CreateProductFeeError"
      }
    }
  }

  override def deleteProductFee(productFeeId: String): Future[Box[Boolean]] = Future {
    tryo {
      DoobieUtil.runQuery(
        sql"DELETE FROM productfee WHERE productfeeid = ${nn(productFeeId)}".update.run) >= 0
    }
  }
}
