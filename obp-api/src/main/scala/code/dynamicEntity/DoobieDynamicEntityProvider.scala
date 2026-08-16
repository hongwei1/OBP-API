package code.dynamicEntity

import code.api.util.{APIUtil, DoobieUtil}
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.util.json
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo
import org.apache.commons.lang3.StringUtils

import java.sql.Timestamp
import java.time.Instant

object DoobieDynamicEntityProvider extends DynamicEntityProvider with MdcLoggable {

  private def nn(s: String): String = if (s == null) "" else s

  private case class Row(
    dynamicentityid: String,
    entityname: String,
    metadatajson: String,
    userid: String,
    bankid: Option[String],
    haspersonalentity: Boolean,
    haspublicaccess: Boolean,
    hascommunityaccess: Boolean,
    personalrequiresrole: Boolean
  )

  private def toCommons(row: Row): DynamicEntityCommons = DynamicEntityCommons(
    entityName          = row.entityname,
    metadataJson        = row.metadatajson,
    dynamicEntityId     = Some(row.dynamicentityid),
    userId              = row.userid,
    bankId              = row.bankid.filter(_.nonEmpty),
    hasPersonalEntity   = row.haspersonalentity,
    hasPublicAccess     = row.haspublicaccess,
    hasCommunityAccess  = row.hascommunityaccess,
    personalRequiresRole = row.personalrequiresrole
  )

  private val selectCols: Fragment =
    fr"""SELECT dynamicentityid, entityname, metadatajson, userid, bankid,
               haspersonalentity, haspublicaccess, hascommunityaccess, personalrequiresrole
         FROM dynamicentity"""

  override def getById(bankId: Option[String], dynamicEntityId: String): Box[DynamicEntityT] = {
    val q =
      if (bankId.isEmpty) selectCols ++ fr"WHERE dynamicentityid = $dynamicEntityId AND bankid IS NULL LIMIT 1"
      else                selectCols ++ fr"WHERE dynamicentityid = $dynamicEntityId AND bankid = ${bankId.get} LIMIT 1"
    DoobieUtil.runQuery(q.query[Row].option) match {
      case Some(row) => Full(toCommons(row))
      case None      => Empty
    }
  }

  override def getByEntityName(bankId: Option[String], entityName: String): Box[DynamicEntityT] = {
    val q =
      if (bankId.isEmpty) selectCols ++ fr"WHERE entityname = $entityName AND bankid IS NULL LIMIT 1"
      else                selectCols ++ fr"WHERE bankid = ${bankId.get} AND entityname = $entityName LIMIT 1"
    DoobieUtil.runQuery(q.query[Row].option) match {
      case Some(row) => Full(toCommons(row))
      case None      => Empty
    }
  }

  override def getDynamicEntities(bankId: Option[String], returnBothBankAndSystemLevel: Boolean): List[DynamicEntityT] = {
    val q =
      if (returnBothBankAndSystemLevel) selectCols
      else if (bankId.isEmpty)          selectCols ++ fr"WHERE bankid IS NULL"
      else                              selectCols ++ fr"WHERE bankid = ${bankId.get}"
    DoobieUtil.runQuery(q.query[Row].to[List]).map(toCommons)
  }

  override def getDynamicEntitiesByUserId(userId: String): List[DynamicEntityT] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE userid = $userId").query[Row].to[List]).map(toCommons)

  override def createOrUpdate(dynamicEntity: DynamicEntityT): Box[DynamicEntityT] =
    tryo {
      val now       = Timestamp.from(Instant.now())
      val bankIdVal = dynamicEntity.bankId.filter(_.nonEmpty)

      val entityId = dynamicEntity.dynamicEntityId.filter(StringUtils.isNotBlank) match {
        case Some(id) =>
          val exists = DoobieUtil.runQuery(
            fr"SELECT COUNT(*) FROM dynamicentity WHERE dynamicentityid = $id".query[Long].unique) > 0
          if (exists) {
            DoobieUtil.runQuery(
              sql"""UPDATE dynamicentity SET
                      entityname = ${nn(dynamicEntity.entityName)},
                      metadatajson = ${nn(dynamicEntity.metadataJson)},
                      userid = ${nn(dynamicEntity.userId)},
                      bankid = $bankIdVal,
                      haspersonalentity = ${dynamicEntity.hasPersonalEntity},
                      haspublicaccess = ${dynamicEntity.hasPublicAccess},
                      hascommunityaccess = ${dynamicEntity.hasCommunityAccess},
                      personalrequiresrole = ${dynamicEntity.personalRequiresRole},
                      updatedat = $now
                    WHERE dynamicentityid = $id""".update.run)
          } else {
            DoobieUtil.runQuery(
              sql"""INSERT INTO dynamicentity
                      (dynamicentityid, entityname, metadatajson, userid, bankid,
                       haspersonalentity, haspublicaccess, hascommunityaccess, personalrequiresrole,
                       createdat, updatedat)
                    VALUES
                      ($id, ${nn(dynamicEntity.entityName)}, ${nn(dynamicEntity.metadataJson)},
                       ${nn(dynamicEntity.userId)}, $bankIdVal,
                       ${dynamicEntity.hasPersonalEntity}, ${dynamicEntity.hasPublicAccess},
                       ${dynamicEntity.hasCommunityAccess}, ${dynamicEntity.personalRequiresRole},
                       $now, $now)""".update.run)
          }
          id
        case None =>
          val newId = APIUtil.generateUUID()
          DoobieUtil.runQuery(
            sql"""INSERT INTO dynamicentity
                    (dynamicentityid, entityname, metadatajson, userid, bankid,
                     haspersonalentity, haspublicaccess, hascommunityaccess, personalrequiresrole,
                     createdat, updatedat)
                  VALUES
                    ($newId, ${nn(dynamicEntity.entityName)}, ${nn(dynamicEntity.metadataJson)},
                     ${nn(dynamicEntity.userId)}, $bankIdVal,
                     ${dynamicEntity.hasPersonalEntity}, ${dynamicEntity.hasPublicAccess},
                     ${dynamicEntity.hasCommunityAccess}, ${dynamicEntity.personalRequiresRole},
                     $now, $now)""".update.run)
          newId
      }

      if (code.api.dynamic.entity.projection.IndexingCapabilities.projectionEnabled) {
        try {
          val info = code.api.dynamic.entity.helper.DynamicEntityInfo(
            dynamicEntity.metadataJson, dynamicEntity.entityName, dynamicEntity.bankId,
            dynamicEntity.hasPersonalEntity, dynamicEntity.hasPublicAccess,
            dynamicEntity.hasCommunityAccess, dynamicEntity.personalRequiresRole)
          val scalar = code.api.dynamic.entity.projection.ProjectionProvisioner.scalarFieldsOf(info.indexedFields)
          if (scalar.nonEmpty)
            code.api.dynamic.entity.projection.ProjectionProvisioner
              .ensureProvisionedFields(dynamicEntity.bankId, dynamicEntity.entityName, scalar)
              .unsafeRunSync()(cats.effect.unsafe.implicits.global)
        } catch {
          case e: Throwable =>
            logger.error(s"DE projection provisioning failed for ${dynamicEntity.entityName} (definition saved; queries will report pending)", e)
        }
      }

      DynamicEntityCommons(
        entityName          = dynamicEntity.entityName,
        metadataJson        = dynamicEntity.metadataJson,
        dynamicEntityId     = Some(entityId),
        userId              = dynamicEntity.userId,
        bankId              = dynamicEntity.bankId,
        hasPersonalEntity   = dynamicEntity.hasPersonalEntity,
        hasPublicAccess     = dynamicEntity.hasPublicAccess,
        hasCommunityAccess  = dynamicEntity.hasCommunityAccess,
        personalRequiresRole = dynamicEntity.personalRequiresRole
      )
    }

  override def delete(dynamicEntity: DynamicEntityT): Box[Boolean] = Box.tryo {
    dynamicEntity.dynamicEntityId match {
      case Some(id) if StringUtils.isNotBlank(id) =>
        DoobieUtil.runQuery(
          sql"DELETE FROM dynamicentity WHERE dynamicentityid = $id".update.run) >= 0
      case _ =>
        DoobieUtil.runQuery(
          sql"DELETE FROM dynamicentity WHERE entityname = ${nn(dynamicEntity.entityName)}".update.run) >= 0
    }
  }
}
