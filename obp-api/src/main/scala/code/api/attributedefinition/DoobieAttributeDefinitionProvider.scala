package code.api.attributedefinition

import code.api.util.{APIUtil, DoobieUtil, ErrorMessages}
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.BankId
import com.openbankproject.commons.model.enums.AttributeCategory
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}

import java.sql.Timestamp
import scala.collection.immutable.List
import scala.concurrent.Future

object DoobieAttributeDefinitionProvider extends AttributeDefinitionProviderTrait with MdcLoggable {

  // Table "attributedefinition". Concrete-returning trait: reuse the Lift AttributeDefinition class as an
  // in-memory return holder (no second AttributeDefinitionTrait impl → no equality regression).
  private case class AdRow(
    attributedefinitionid: Option[String],
    bankid: Option[String],
    name: Option[String],
    category: Option[String],
    typeofvalue: Option[String],
    description: Option[String],
    alias: Option[String],
    canbeseenonviews: Option[String],
    isactive: Option[Boolean]
  )

  private def nn(s: String): String = if (s == null) "" else s

  private def toEntity(row: AdRow): AttributeDefinition =
    AttributeDefinition.create
      .AttributeDefinitionId(row.attributedefinitionid.getOrElse(""))
      .BankId(row.bankid.getOrElse(""))
      .Name(row.name.getOrElse(""))
      .Category(row.category.getOrElse(""))
      .`TypeOfValue`(row.typeofvalue.getOrElse(""))
      .Description(row.description.getOrElse(""))
      .Alias(row.alias.getOrElse(""))
      .CanBeSeenOnViews(row.canbeseenonviews.getOrElse(""))
      .IsActive(row.isactive.getOrElse(false))

  private val selectCols: Fragment =
    fr"""SELECT attributedefinitionid, bankid, name, category, typeofvalue, description, alias, canbeseenonviews, isactive
         FROM attributedefinition"""

  private def findRow(where: Fragment): Box[AdRow] =
    DoobieUtil.runQuery((selectCols ++ where ++ fr"LIMIT 1").query[AdRow].option) match {
      case Some(r) => Full(r)
      case None    => Empty
    }

  override def createOrUpdateAttributeDefinition(bankId: BankId, name: String, category: AttributeCategory.Value,
                                                 `type`: com.openbankproject.commons.model.enums.AttributeType.Value,
                                                 description: String, alias: String, canBeSeenOnViews: List[String],
                                                 isActive: Boolean): Future[Box[AttributeDefinition]] = Future {
    val views = nn(canBeSeenOnViews.mkString(";"))
    val now = new Timestamp(System.currentTimeMillis())
    findRow(fr"WHERE bankid = ${nn(bankId.value)} AND name = ${nn(name)} AND category = ${category.toString}") match {
      case Full(existing) =>
        DoobieUtil.runQuery(
          sql"""UPDATE attributedefinition
                SET typeofvalue = ${`type`.toString}, description = ${nn(description)}, alias = ${nn(alias)},
                    canbeseenonviews = $views, isactive = $isActive, updatedat = $now
                WHERE bankid = ${nn(bankId.value)} AND name = ${nn(name)} AND category = ${category.toString}""".update.run)
        Full(toEntity(existing.copy(typeofvalue = Some(`type`.toString), description = Some(nn(description)),
          alias = Some(nn(alias)), canbeseenonviews = Some(views), isactive = Some(isActive))))
      case _ =>
        val newId = APIUtil.generateUUID()
        DoobieUtil.runQuery(
          sql"""INSERT INTO attributedefinition
                  (attributedefinitionid, bankid, name, category, typeofvalue, description, alias, canbeseenonviews, isactive, createdat, updatedat)
                VALUES ($newId, ${nn(bankId.value)}, ${nn(name)}, ${category.toString}, ${`type`.toString},
                        ${nn(description)}, ${nn(alias)}, $views, $isActive, $now, $now)""".update.run)
        Full(toEntity(AdRow(Some(newId), Some(nn(bankId.value)), Some(nn(name)), Some(category.toString),
          Some(`type`.toString), Some(nn(description)), Some(nn(alias)), Some(views), Some(isActive))))
    }
  }

  override def deleteAttributeDefinition(attributeDefinitionId: String, category: AttributeCategory.Value): Future[Box[Boolean]] = Future {
    findRow(fr"WHERE attributedefinitionid = ${nn(attributeDefinitionId)} AND category = ${category.toString}") match {
      case Full(_) =>
        Full(DoobieUtil.runQuery(
          sql"DELETE FROM attributedefinition WHERE attributedefinitionid = ${nn(attributeDefinitionId)} AND category = ${category.toString}".update.run) > 0)
      case Empty => Empty ?~! ErrorMessages.AttributeNotFound
      case unhandledError =>
        logger.error(unhandledError)
        Full(false)
    }
  }

  override def getAttributeDefinition(category: AttributeCategory.Value): Future[Box[List[AttributeDefinition]]] = Future {
    Full(
      DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE category = ${category.toString}").query[AdRow].to[List]).map(toEntity))
  }
}
