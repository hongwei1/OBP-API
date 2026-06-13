package code.productcollectionitem

import code.api.util.DoobieUtil
import code.productAttributeattribute.MappedProductAttribute
import code.products.MappedProduct
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.{ProductAttribute, ProductCollectionItem}
import doobie._
import doobie.implicits._
import net.liftweb.common.Box
import net.liftweb.mapper.By
import net.liftweb.util.Helpers.tryo

import scala.collection.immutable.List
import scala.concurrent.Future

object DoobieProductCollectionItemProvider extends ProductCollectionItemProvider {

  private case class ItemRow(mcollectioncode: Option[String], mmemberproductcode: Option[String])

  private case class DoobieProductCollectionItem(row: ItemRow) extends ProductCollectionItem {
    override def collectionCode: String    = row.mcollectioncode.getOrElse("")
    override def memberProductCode: String = row.mmemberproductcode.getOrElse("")
  }

  private def nn(s: String): String = if (s == null) "" else s

  private val selectCols: Fragment =
    fr"SELECT mcollectioncode, mmemberproductcode FROM mappedproductcollectionitem"

  private def findByCollectionCode(collectionCode: String): List[DoobieProductCollectionItem] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE mcollectioncode = ${nn(collectionCode)}")
        .query[ItemRow].to[List]).map(DoobieProductCollectionItem(_))

  override def getProductCollectionItems(collectionCode: String): Future[Box[List[ProductCollectionItem]]] =
    Future { tryo { findByCollectionCode(collectionCode) } }

  override def getProductCollectionItemsTree(collectionCode: String, bankId: String): Future[Box[List[(ProductCollectionItem, MappedProduct, List[ProductAttribute])]]] =
    Future {
      tryo {
        findByCollectionCode(collectionCode).map { productCollectionItem =>
          // The tuple type requires the concrete MappedProduct, so the product + attribute lookups stay on Lift.
          val product = MappedProduct.find(
            By(MappedProduct.mBankId, bankId),
            By(MappedProduct.mCode, productCollectionItem.memberProductCode)
          ).openOrThrowException("There is no product")
          val attributes: List[MappedProductAttribute] = MappedProductAttribute.findAll(
            By(MappedProductAttribute.mBankId, bankId),
            By(MappedProductAttribute.mCode, product.code.value)
          )
          (productCollectionItem, product, attributes)
        }
      }
    }

  override def getOrCreateProductCollectionItem(collectionCode: String, memberProductCodes: List[String]): Future[Box[List[ProductCollectionItem]]] =
    Future {
      tryo {
        // Replace the collection's items: delete all existing, then insert the new members.
        DoobieUtil.runQuery(
          sql"DELETE FROM mappedproductcollectionitem WHERE mcollectioncode = ${nn(collectionCode)}".update.run)
        memberProductCodes.map { productCode =>
          val now = new java.sql.Timestamp(System.currentTimeMillis())
          DoobieUtil.runQuery(
            sql"""INSERT INTO mappedproductcollectionitem (mcollectioncode, mmemberproductcode, createdat, updatedat)
                  VALUES (${nn(collectionCode)}, ${nn(productCode)}, $now, $now)""".update.run)
          DoobieProductCollectionItem(ItemRow(Some(nn(collectionCode)), Some(nn(productCode))))
        }
      }
    }
}
