package code.regulatedentities

import code.api.util.{APIUtil, DoobieUtil}
import code.regulatedentities.attribute.RegulatedEntityAttribute
import com.openbankproject.commons.model.{RegulatedEntityAttributeSimple, RegulatedEntityTrait}
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.mapper.By
import net.liftweb.util.Helpers.tryo

import scala.collection.immutable.List

object DoobieRegulatedEntityProvider extends RegulatedEntityProvider {

  // Table name is the custom dbTableName "RegulatedEntity" (case-insensitive in H2).
  private case class EntRow(
    entityid: Option[String],
    certificateauthoritycaownerid: Option[String],
    entityname: Option[String],
    entitycode: Option[String],
    entitycertificatepublickey: Option[String],
    entitytype: Option[String],
    entityaddress: Option[String],
    entitytowncity: Option[String],
    entitypostcode: Option[String],
    entitycountry: Option[String],
    entitywebsite: Option[String],
    services: Option[String]
  )

  private case class DoobieRegulatedEntity(row: EntRow) extends RegulatedEntityTrait {
    override def entityId: String                     = row.entityid.getOrElse("")
    override def certificateAuthorityCaOwnerId: String = row.certificateauthoritycaownerid.getOrElse("")
    override def entityName: String                   = row.entityname.getOrElse("")
    override def entityCode: String                   = row.entitycode.getOrElse("")
    override def entityCertificatePublicKey: String    = row.entitycertificatepublickey.getOrElse("")
    override def entityType: String                   = row.entitytype.getOrElse("")
    override def entityAddress: String                = row.entityaddress.getOrElse("")
    override def entityTownCity: String               = row.entitytowncity.getOrElse("")
    override def entityPostCode: String               = row.entitypostcode.getOrElse("")
    override def entityCountry: String                = row.entitycountry.getOrElse("")
    override def entityWebSite: String                = row.entitywebsite.getOrElse("")
    override def services: String                     = row.services.getOrElse("")
    // Mirrors the Mapper accessor: the RegulatedEntityAttribute Lift entity is still present.
    override def attributes: Option[List[RegulatedEntityAttributeSimple]] =
      Some(
        RegulatedEntityAttribute.findAll(
          By(RegulatedEntityAttribute.RegulatedEntityId_, entityId)
        ).map(i => RegulatedEntityAttributeSimple(i.attributeType.toString, i.name, i.value))
      )
  }

  private def nn(s: String): String = if (s == null) "" else s

  private val selectCols: Fragment =
    fr"""SELECT entityid, certificateauthoritycaownerid, entityname, entitycode, entitycertificatepublickey,
                entitytype, entityaddress, entitytowncity, entitypostcode, entitycountry, entitywebsite, services
         FROM regulatedentity"""

  override def getRegulatedEntities(): List[RegulatedEntityTrait] =
    DoobieUtil.runQuery(selectCols.query[EntRow].to[List]).map(DoobieRegulatedEntity(_))

  override def getRegulatedEntityByEntityId(entityId: String): Box[RegulatedEntityTrait] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE entityid = ${nn(entityId)} LIMIT 1").query[EntRow].option) match {
      case Some(r) => Full(DoobieRegulatedEntity(r))
      case None    => Empty
    }

  override def createRegulatedEntity(certificateAuthorityCaOwnerId: Option[String],
                                     entityCertificatePublicKey: Option[String],
                                     entityName: Option[String],
                                     entityCode: Option[String],
                                     entityType: Option[String],
                                     entityAddress: Option[String],
                                     entityTownCity: Option[String],
                                     entityPostCode: Option[String],
                                     entityCountry: Option[String],
                                     entityWebSite: Option[String],
                                     services: Option[String]): Box[RegulatedEntityTrait] =
    tryo {
      // Unset Option fields default to "" (the MappedString default the Lift create relied on).
      val newId = APIUtil.generateUUID()
      val caOwner = nn(certificateAuthorityCaOwnerId.getOrElse(""))
      val pubKey  = nn(entityCertificatePublicKey.getOrElse(""))
      val name    = nn(entityName.getOrElse(""))
      val code    = nn(entityCode.getOrElse(""))
      val etype   = nn(entityType.getOrElse(""))
      val address = nn(entityAddress.getOrElse(""))
      val town    = nn(entityTownCity.getOrElse(""))
      val postal  = nn(entityPostCode.getOrElse(""))
      val country = nn(entityCountry.getOrElse(""))
      val website = nn(entityWebSite.getOrElse(""))
      val svc     = nn(services.getOrElse(""))
      DoobieUtil.runQuery(
        sql"""INSERT INTO regulatedentity
                (entityid, certificateauthoritycaownerid, entityname, entitycode, entitycertificatepublickey,
                 entitytype, entityaddress, entitytowncity, entitypostcode, entitycountry, entitywebsite, services)
              VALUES ($newId, $caOwner, $name, $code, $pubKey, $etype, $address, $town, $postal, $country, $website, $svc)""".update.run)
      DoobieRegulatedEntity(EntRow(
        Some(newId), Some(caOwner), Some(name), Some(code), Some(pubKey), Some(etype),
        Some(address), Some(town), Some(postal), Some(country), Some(website), Some(svc)))
    }

  override def deleteRegulatedEntity(id: String): Box[Boolean] =
    tryo {
      DoobieUtil.runQuery(
        sql"DELETE FROM regulatedentity WHERE entityid = ${nn(id)}".update.run) >= 0
    }
}
