package code.webuiprops

import code.api.cache.Caching
import code.api.util.APIUtil.{activeBrand, writeMetricEndpointTiming}
import code.api.util.{APIUtil, DoobieUtil, ErrorMessages, I18NUtil}
import code.util.MappedUUID
import com.tesobe.CacheKeyFromArguments
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Failure, Full}
import net.liftweb.mapper._

import java.util.UUID.randomUUID

/**
  * props name start with "webui_" can set in to db, this module just support the webui_ props CRUD
  *
  * NOTE: This object keeps its "Mapped..." name for a deliberate reason, but its data access is now
  * Doobie SQL (the Lift Mapper DSL has been removed from every method below). The connector code
  * generator rewrites this exact class by string — "code.webuiprops.MappedWebUiPropsProvider$" in
  * ConnectorBuilderUtil — to stub getWebUiPropsValue (return "") during connector generation, so it
  * does not hit the DB before the dataSource exists. Renaming this object would silently break
  * non-mapped (rabbitmq/grpc/storedprocedure) connector builds, a path the default mapped-connector
  * test suite does not exercise. The rename and the entity migration are deferred to Phase C, where
  * the connector coupling can be updated and verified together.
  */
object MappedWebUiPropsProvider extends WebUiPropsProvider {
  // default webUiProps value cached seconds
  private val webUiPropsTTL = APIUtil.getPropsAsIntValue("webui.props.cache.ttl.seconds", 0)

  private case class WebUiPropsRow(webuipropsid: Option[String], name: Option[String], value: Option[String])

  private val selectCols: Fragment = fr"SELECT webuipropsid, name, value FROM webuiprops"

  private def rowToCommons(r: WebUiPropsRow): WebUiPropsT =
    WebUiPropsCommons(r.name.getOrElse(""), r.value.getOrElse(""), r.webuipropsid, Some("database"))

  private def nn(s: String): String = if (s == null) "" else s

  override def getAll(): List[WebUiPropsT] =
    DoobieUtil.runQuery(selectCols.query[WebUiPropsRow].to[List]).map(rowToCommons)

  override def getByName(name: String): Box[WebUiPropsT] =
    DoobieUtil.runQuery((selectCols ++ fr"WHERE name = ${nn(name)} LIMIT 1").query[WebUiPropsRow].option) match {
      case Some(r) => Full(rowToCommons(r))
      case None    => Empty
    }

  // Mirrors the Lift find-or-create-then-saveMe exactly: look up by the ORIGINAL (untrimmed) name,
  // but persist the TRIMMED name (Lift did `.Name(webUiProps.name.trim())`).
  override def createOrUpdate(webUiProps: WebUiPropsT): Box[WebUiPropsT] = {
    val trimmedName = nn(webUiProps.name).trim()
    val value = nn(webUiProps.value)
    val existingId: Option[String] =
      DoobieUtil.runQuery(
        (fr"SELECT webuipropsid FROM webuiprops WHERE name = ${nn(webUiProps.name)} LIMIT 1")
          .query[String].option)
    existingId match {
      case Some(id) =>
        DoobieUtil.runQuery(
          sql"UPDATE webuiprops SET name = $trimmedName, value = $value WHERE webuipropsid = $id".update.run)
      case None =>
        val newId = randomUUID().toString
        DoobieUtil.runQuery(
          sql"INSERT INTO webuiprops (webuipropsid, name, value) VALUES ($newId, $trimmedName, $value)".update.run)
    }
    getByName(trimmedName)
  }

  override def delete(webUiPropsId: String): Box[Boolean] = {
    val rows = DoobieUtil.runQuery(
      sql"DELETE FROM webuiprops WHERE webuipropsid = ${nn(webUiPropsId)}".update.run)
    if (rows > 0) Full(true) else Failure(ErrorMessages.WebUiPropsNotFound)
  }

  // Rules to obtain the WebUI props value
  // 1) Get requested + brand + language if any
  // 2) Get requested + language if any
  // 3) Get requested if any
  // 4) Get default value
  override def getWebUiPropsValue(requestedPropertyName: String, defaultValue: String, language: String = I18NUtil.currentLocale().toString()): String = writeMetricEndpointTiming {
    import scala.concurrent.duration._
    var cacheKey = (randomUUID().toString, randomUUID().toString, randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithImMemory(Some(cacheKey.toString()))(webUiPropsTTL.second) {
        // If we have an active brand, construct a target property name to look for.
        val brandSpecificPropertyName = activeBrand() match {
          case Some(brand) => s"${requestedPropertyName}_FOR_BRAND_${brand}"
          case _ => requestedPropertyName
        }

        // In case there is a translation we must use it
        val webUiPropsPropertyName = s"${brandSpecificPropertyName}_${language}"
        val translatedAndOrBrandPropertyName = getByName(webUiPropsPropertyName).isDefined match {
          case true => webUiPropsPropertyName
          case false => brandSpecificPropertyName
        }

        getByName(translatedAndOrBrandPropertyName).map(_.value) // Get translated and/or brand specific value if any
          .or(getByName(requestedPropertyName).map(_.value)) // Get requested value if any
            .openOr {
              APIUtil.getPropsValue(requestedPropertyName, defaultValue) // Otherwise return the default value
            }
      }
    }
  }("getWebUiProps")("MappedWebUiPropsProvider")

}

class WebUiProps extends WebUiPropsT with LongKeyedMapper[WebUiProps] with IdPK {

  override def getSingleton = WebUiProps

  object WebUiPropsId extends MappedUUID(this)
  object Name extends MappedString(this, 255)
  object Value extends MappedText(this)

  override def webUiPropsId: Option[String] = Option(WebUiPropsId.get)
  override def name: String = Name.get
  override def value: String = Value.get
  override def source: Option[String] = Some("database")
}

object WebUiProps extends WebUiProps with LongKeyedMetaMapper[WebUiProps] {
  override def dbIndexes = UniqueIndex(WebUiPropsId) :: UniqueIndex(Name) :: super.dbIndexes
}

