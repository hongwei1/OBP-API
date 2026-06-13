package code.DynamicData

import code.api.util.ErrorMessages.DynamicDataNotFound
import code.api.util.DoobieUtil
import com.openbankproject.commons.util.json
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Failure, Full}
import net.liftweb.util.Helpers.tryo
import org.json4s.JObject
import org.json4s.JsonAST.JString

object DoobieDynamicDataProvider extends DynamicDataProvider {

  private def nn(s: String): String = if (s == null) "" else s

  private def getIdName(entityName: String): String =
    s"${entityName}_Id".replaceAll("(?<=[a-z0-9])(?=[A-Z])|-", "_").toLowerCase

  private case class Row(
    dynamicdataid: String,
    dynamicentityname: String,
    datajson: String,
    bankid: Option[String],
    userid: Option[String],
    ispersonalentity: Boolean
  )

  private def toCommons(row: Row): DynamicDataCommons = DynamicDataCommons(
    dynamicEntityName = row.dynamicentityname,
    dataJson          = row.datajson,
    dynamicDataId     = Some(row.dynamicdataid),
    bankId            = row.bankid.filter(_.nonEmpty),
    userId            = row.userid.filter(_.nonEmpty),
    isPersonalEntity  = row.ispersonalentity
  )

  private val selectCols: Fragment =
    fr"SELECT dynamicdataid, dynamicentityname, datajson, bankid, userid, ispersonalentity FROM dynamicdata"

  private def bankIdFragment(bankId: Option[String]): Fragment =
    if (bankId.isEmpty) fr"bankid IS NULL"
    else                fr"bankid = ${bankId.get}"

  override def save(bankId: Option[String], entityName: String, requestBody: JObject, userId: Option[String], isPersonalEntity: Boolean): Box[DynamicDataT] =
    tryo {
      val idName    = getIdName(entityName)
      val JString(idValue) = (requestBody \ idName).asInstanceOf[JString]
      val dataStr   = json.compactRender(requestBody)
      val bankIdVal = bankId.filter(_.nonEmpty)
      val userIdVal = userId.filter(_.nonEmpty)
      DoobieUtil.runQuery(
        sql"""INSERT INTO dynamicdata (dynamicdataid, dynamicentityname, datajson, bankid, userid, ispersonalentity)
              VALUES ($idValue, ${nn(entityName)}, $dataStr, $bankIdVal, $userIdVal, $isPersonalEntity)""".update.run)
      code.api.dynamic.entity.projection.ProjectionDualWrite.onSave(bankId, entityName, idValue, requestBody)
      DynamicDataCommons(entityName, dataStr, Some(idValue), bankId, userId, isPersonalEntity)
    }

  override def update(bankId: Option[String], entityName: String, requestBody: JObject, id: String, userId: Option[String], isPersonalEntity: Boolean): Box[DynamicDataT] =
    tryo {
      val dataStr   = json.compactRender(requestBody)
      val bankIdVal = bankId.filter(_.nonEmpty)
      val userIdVal = userId.filter(_.nonEmpty)
      val rows = DoobieUtil.runQuery(
        sql"""UPDATE dynamicdata SET datajson = $dataStr, bankid = $bankIdVal, userid = $userIdVal, ispersonalentity = $isPersonalEntity
              WHERE dynamicdataid = $id AND dynamicentityname = ${nn(entityName)}""".update.run)
      if (rows == 0) throw new RuntimeException(s"$DynamicDataNotFound dynamicEntityName=$entityName, dynamicDataId=$id")
      code.api.dynamic.entity.projection.ProjectionDualWrite.onSave(bankId, entityName, id, requestBody)
      DynamicDataCommons(entityName, dataStr, Some(id), bankId, userId, isPersonalEntity)
    }

  override def get(bankId: Option[String], entityName: String, id: String, userId: Option[String], isPersonalEntity: Boolean): Box[DynamicDataT] = {
    val whereClause: Fragment =
      if (bankId.isEmpty && !isPersonalEntity)
        fr"WHERE dynamicdataid = $id AND dynamicentityname = ${nn(entityName)} AND ispersonalentity = false AND bankid IS NULL"
      else if (bankId.isEmpty && isPersonalEntity)
        fr"WHERE dynamicdataid = $id AND dynamicentityname = ${nn(entityName)} AND userid = ${userId.getOrElse("")} AND bankid IS NULL"
      else if (bankId.isDefined && !isPersonalEntity)
        fr"WHERE dynamicdataid = $id AND dynamicentityname = ${nn(entityName)} AND ispersonalentity = false AND bankid = ${bankId.get}"
      else
        fr"WHERE dynamicdataid = $id AND dynamicentityname = ${nn(entityName)} AND bankid = ${bankId.get} AND userid = ${userId.getOrElse("")}"

    DoobieUtil.runQuery((selectCols ++ whereClause ++ fr"LIMIT 1").query[Row].option) match {
      case Some(row) => Full(toCommons(row))
      case None =>
        val hint = if (bankId.isDefined) s", bankId=${bankId.get}" else ""
        val userHint = if (userId.isDefined) s", userId=${userId.get}" else ""
        Failure(s"$DynamicDataNotFound dynamicEntityName=$entityName, dynamicDataId=$id$hint$userHint")
    }
  }

  override def getAllDataJson(bankId: Option[String], entityName: String, userId: Option[String], isPersonalEntity: Boolean): List[JObject] =
    getAll(bankId, entityName, userId, isPersonalEntity)
      .map(it => json.parse(it.dataJson).asInstanceOf[JObject])

  override def getAll(bankId: Option[String], entityName: String, userId: Option[String], isPersonalEntity: Boolean): List[DynamicDataT] = {
    val whereClause: Fragment =
      if (bankId.isEmpty && !isPersonalEntity)
        fr"WHERE dynamicentityname = ${nn(entityName)} AND ispersonalentity = false AND bankid IS NULL"
      else if (bankId.isEmpty && isPersonalEntity)
        fr"WHERE dynamicentityname = ${nn(entityName)} AND userid = ${userId.getOrElse("")} AND bankid IS NULL"
      else if (bankId.isDefined && !isPersonalEntity)
        fr"WHERE dynamicentityname = ${nn(entityName)} AND ispersonalentity = false AND bankid = ${bankId.get}"
      else
        fr"WHERE dynamicentityname = ${nn(entityName)} AND bankid = ${bankId.get} AND userid = ${userId.getOrElse("")}"

    DoobieUtil.runQuery((selectCols ++ whereClause).query[Row].to[List]).map(toCommons)
  }

  override def delete(bankId: Option[String], entityName: String, id: String, userId: Option[String], isPersonalEntity: Boolean): Box[Boolean] =
    get(bankId, entityName, id, userId, isPersonalEntity).map { _ =>
      val rows = DoobieUtil.runQuery(
        sql"DELETE FROM dynamicdata WHERE dynamicdataid = $id AND dynamicentityname = ${nn(entityName)}".update.run)
      code.api.dynamic.entity.projection.ProjectionDualWrite.onDelete(bankId, entityName, id)
      rows >= 0
    }

  override def existsData(bankId: Option[String], dynamicEntityName: String, userId: Option[String], isPersonalEntity: Boolean): Boolean = {
    val whereClause: Fragment =
      if (bankId.isEmpty && !isPersonalEntity)
        fr"WHERE dynamicentityname = ${nn(dynamicEntityName)} AND bankid IS NULL AND ispersonalentity = false"
      else if (bankId.isDefined && !isPersonalEntity)
        fr"WHERE dynamicentityname = ${nn(dynamicEntityName)} AND bankid = ${bankId.get} AND ispersonalentity = false"
      else if (bankId.isEmpty && isPersonalEntity)
        fr"WHERE dynamicentityname = ${nn(dynamicEntityName)} AND bankid IS NULL AND userid = ${userId.getOrElse("")}"
      else
        fr"WHERE dynamicentityname = ${nn(dynamicEntityName)} AND bankid = ${bankId.get} AND userid = ${userId.getOrElse("")}"

    DoobieUtil.runQuery((fr"SELECT COUNT(*) FROM dynamicdata" ++ whereClause).query[Long].unique) > 0
  }

  override def existsById(entityName: String, id: String): Boolean =
    DoobieUtil.runQuery(
      fr"SELECT COUNT(*) FROM dynamicdata WHERE dynamicdataid = $id AND dynamicentityname = ${nn(entityName)}"
        .query[Long].unique) > 0

  override def getAllCommunity(bankId: Option[String], entityName: String): List[DynamicDataT] = {
    val whereClause: Fragment =
      if (bankId.isEmpty) fr"WHERE dynamicentityname = ${nn(entityName)} AND bankid IS NULL"
      else                fr"WHERE dynamicentityname = ${nn(entityName)} AND bankid = ${bankId.get}"
    DoobieUtil.runQuery((selectCols ++ whereClause).query[Row].to[List]).map(toCommons)
  }

  override def getAllDataJsonCommunity(bankId: Option[String], entityName: String): List[JObject] =
    getAllCommunity(bankId, entityName)
      .map(it => json.parse(it.dataJson).asInstanceOf[JObject])

  override def getCommunity(bankId: Option[String], entityName: String, id: String): Box[DynamicDataT] = {
    val whereClause: Fragment =
      if (bankId.isEmpty) fr"WHERE dynamicdataid = $id AND dynamicentityname = ${nn(entityName)} AND bankid IS NULL LIMIT 1"
      else                fr"WHERE dynamicdataid = $id AND dynamicentityname = ${nn(entityName)} AND bankid = ${bankId.get} LIMIT 1"

    DoobieUtil.runQuery((selectCols ++ whereClause).query[Row].option) match {
      case Some(row) => Full(toCommons(row))
      case None =>
        val hint = bankId.map(b => s", bankId=$b").getOrElse("")
        Failure(s"$DynamicDataNotFound dynamicEntityName=$entityName, dynamicDataId=$id$hint")
    }
  }
}
