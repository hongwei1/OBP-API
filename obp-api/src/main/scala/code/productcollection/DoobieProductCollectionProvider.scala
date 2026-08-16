package code.productcollection

import code.api.util.DoobieUtil
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.ProductCollection
import doobie._
import doobie.implicits._
import net.liftweb.common.Box
import net.liftweb.util.Helpers.tryo

import java.sql.Timestamp
import scala.concurrent.Future

/**
 * Doobie implementation of the [[ProductCollectionProvider]] vend (Phase 9 of the Lift Mapper ->
 * Doobie migration). The single `mappedproductcollection` table (collectionCode, productCode) is
 * accessed via Doobie SQL through `DoobieUtil.runQuery`, wrapped in `Future` for the async trait.
 *
 * `getOrCreateProductCollection` mirrors the Lift behaviour: delete every existing row for the
 * collection code, then insert one row per product code.
 *
 * Coexistence phase: only data access is migrated. `MappedProductCollection` stays in
 * `Boot.scala`'s `ToSchemify.models` for schema and the test-isolation reset.
 */
object DoobieProductCollectionProvider extends ProductCollectionProvider {

  private case class ProductCollectionRow(collectionCode: Option[String], productCode: Option[String])

  private case class DoobieProductCollection(row: ProductCollectionRow) extends ProductCollection {
    override def collectionCode: String = row.collectionCode.getOrElse("")
    override def productCode: String = row.productCode.getOrElse("")
  }

  private def nn(s: String): String = if (s == null) "" else s
  private def now: Timestamp = new Timestamp(System.currentTimeMillis())

  private val selectCols: Fragment =
    fr"SELECT mcollectioncode, mproductcode FROM mappedproductcollection"

  override def getProductCollection(collectionCode: String): Future[Box[List[ProductCollection]]] = Future {
    tryo {
      DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE mcollectioncode = ${nn(collectionCode)}")
          .query[ProductCollectionRow].to[List]).map(DoobieProductCollection(_))
    }
  }

  override def getOrCreateProductCollection(collectionCode: String, productCodes: List[String]): Future[Box[List[ProductCollection]]] = Future {
    tryo {
      DoobieUtil.runQuery(
        sql"DELETE FROM mappedproductcollection WHERE mcollectioncode = ${nn(collectionCode)}".update.run)
      productCodes.map { pc =>
        DoobieUtil.runQuery(
          sql"""INSERT INTO mappedproductcollection (mproductcode, mcollectioncode, createdat, updatedat)
                VALUES (${nn(pc)}, ${nn(collectionCode)}, $now, $now)""".update.run)
        DoobieProductCollection(ProductCollectionRow(Some(nn(collectionCode)), Some(nn(pc))))
      }
    }
  }
}
