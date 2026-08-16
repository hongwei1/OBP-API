package code.products

import code.api.util.DoobieUtil
import com.openbankproject.commons.model.{BankId, License, Meta, Product, ProductCode}
import doobie._
import doobie.implicits._

/**
 * Doobie implementation of the [[ProductsProvider]] vend (Phase 3 of the Lift Mapper -> Doobie
 * migration). Read-only access to the single `mappedproduct` table via Doobie SQL through
 * `DoobieUtil.runQuery` (which joins the http4s request transaction when one is present).
 *
 * Coexistence phase: only data access is migrated. The `MappedProduct` entity stays registered in
 * `Boot.scala`'s `ToSchemify.models`, so Schemifier still owns the schema and the test-isolation
 * reset still clears the table. Writes (createProduct) live in the connector and continue to use the
 * Mapper entity, which targets the same table — reads and writes interoperate.
 *
 * Nullable columns are read as `Option[String]` and defaulted to "", mirroring Lift MappedString's
 * tolerance of null values.
 */
object DoobieProductsProvider extends ProductsProvider {

  private case class ProductRow(
    bankId: Option[String],
    code: Option[String],
    parentProductCode: Option[String],
    name: Option[String],
    category: Option[String],
    family: Option[String],
    superFamily: Option[String],
    moreInfoUrl: Option[String],
    termsAndConditionsUrl: Option[String],
    details: Option[String],
    description: Option[String],
    licenseId: Option[String],
    licenseName: Option[String]
  )

  private case class DoobieProduct(row: ProductRow) extends Product {
    override def code: ProductCode = ProductCode(row.code.getOrElse(""))
    override def parentProductCode: ProductCode = ProductCode(row.parentProductCode.getOrElse(""))
    override def bankId: BankId = BankId(row.bankId.getOrElse(""))
    override def name: String = row.name.getOrElse("")
    override def category: String = row.category.getOrElse("")
    override def family: String = row.family.getOrElse("")
    override def superFamily: String = row.superFamily.getOrElse("")
    override def moreInfoUrl: String = row.moreInfoUrl.getOrElse("")
    override def termsAndConditionsUrl: String = row.termsAndConditionsUrl.getOrElse("")
    override def details: String = row.details.getOrElse("")
    override def description: String = row.description.getOrElse("")
    override def meta: Meta = Meta(license = License(id = row.licenseId.getOrElse(""), name = row.licenseName.getOrElse("")))
  }

  private val selectCols: Fragment =
    fr"""SELECT mbankid, mcode, mparentproductcode, mname, mcategory, mfamily, msuperfamily,
         mmoreinfourl, mtermsandconditionsurl, mdetails, mdescription, mlicenseid, mlicensename
         FROM mappedproduct"""

  // Lift MappedString tolerated null; normalise null -> "" before binding to a SQL parameter.
  private def nn(s: String): String = if (s == null) "" else s

  override protected def getProductFromProvider(bankId: BankId, productCode: ProductCode): Option[Product] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE mbankid = ${nn(bankId.value)} AND mcode = ${nn(productCode.value)} LIMIT 1")
        .query[ProductRow].option).map(DoobieProduct(_))

  override protected def getProductsFromProvider(bankId: BankId): Option[List[Product]] =
    Some(DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE mbankid = ${nn(bankId.value)}").query[ProductRow].to[List])
      .map(DoobieProduct(_)))
}
