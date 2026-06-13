package code.methodrouting

import code.api.util.{APIUtil, DoobieUtil}
import com.openbankproject.commons.util.json
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo
import org.apache.commons.lang3.StringUtils
import org.json4s.JsonAST.JArray
import org.json4s.native.Serialization.write
import code.api.util.CustomJsonFormats

import scala.collection.immutable.List

object DoobieMethodRoutingProvider extends MethodRoutingProvider with CustomJsonFormats {

  // Table: methodrouting. Cols: methodroutingid, methodname, bankidpattern, isbankidexactmatch, connectorname, parameters.
  // bankidpattern default = ".*" (MethodRouting.bankIdPatternMatchAny).
  // parameters stored as JSON array string.

  private case class RoutingRow(
    methodroutingid: Option[String],
    methodname: Option[String],
    bankidpattern: Option[String],
    isbankidexactmatch: Option[Boolean],
    connectorname: Option[String],
    parameters: Option[String]
  )

  private val defaultBankIdPattern = ".*"

  private def row2commons(row: RoutingRow): MethodRoutingCommons = {
    val paramsJson = json.parse(row.parameters.getOrElse("[]")).asInstanceOf[JArray]
    MethodRoutingCommons(
      methodName = row.methodname.getOrElse(""),
      connectorName = row.connectorname.getOrElse(""),
      isBankIdExactMatch = row.isbankidexactmatch.getOrElse(false),
      bankIdPattern = row.bankidpattern.filter(_.nonEmpty),
      parameters = paramsJson.arr.map(MethodRoutingParam(_)),
      methodRoutingId = row.methodroutingid
    )
  }

  private def nn(s: String): String = if (s == null) "" else s

  private val selectCols: Fragment =
    fr"SELECT methodroutingid, methodname, bankidpattern, isbankidexactmatch, connectorname, parameters FROM methodrouting"

  override def getById(methodRoutingId: String): Box[MethodRoutingT] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE methodroutingid = ${nn(methodRoutingId)} LIMIT 1").query[RoutingRow].option) match {
      case Some(r) => Full(row2commons(r))
      case None    => Empty
    }

  override def getMethodRoutings(methodName: Option[String],
                                  isBankIdExactMatch: Option[Boolean],
                                  bankIdPattern: Option[String]): List[MethodRoutingT] = {
    val conditions = List(
      methodName.map(n => fr"methodname = ${nn(n)}"),
      isBankIdExactMatch.map(v => fr"isbankidexactmatch = $v"),
      bankIdPattern.map(p => fr"bankidpattern = ${nn(p)}")
    ).flatten
    val where = if (conditions.isEmpty) Fragment.empty
      else fr"WHERE" ++ conditions.reduceLeft(_ ++ fr"AND" ++ _)
    DoobieUtil.runQuery((selectCols ++ where).query[RoutingRow].to[List]).map(row2commons)
  }

  override def createOrUpdate(methodRouting: MethodRoutingT): Box[MethodRoutingT] = {
    val bankIdPattern = methodRouting.bankIdPattern.filter(StringUtils.isNotBlank)
    val isExactMatch = if (bankIdPattern.isDefined) methodRouting.isBankIdExactMatch else false
    val paramsJson = write(methodRouting.parameters)

    val existingId: Option[String] = methodRouting.methodRoutingId.filter(StringUtils.isNotBlank).flatMap { id =>
      DoobieUtil.runQuery(
        fr"SELECT methodroutingid FROM methodrouting WHERE methodroutingid = ${nn(id)} LIMIT 1"
          .query[String].option)
    }

    tryo {
      existingId match {
        case Some(id) =>
          DoobieUtil.runQuery(
            sql"""UPDATE methodrouting
                  SET methodname = ${nn(methodRouting.methodName)},
                      bankidpattern = ${bankIdPattern.orNull},
                      isbankidexactmatch = $isExactMatch,
                      connectorname = ${nn(methodRouting.connectorName)},
                      parameters = $paramsJson
                  WHERE methodroutingid = $id""".update.run)
          row2commons(RoutingRow(Some(id), Some(methodRouting.methodName), bankIdPattern,
            Some(isExactMatch), Some(methodRouting.connectorName), Some(paramsJson)))
        case None =>
          val newId = APIUtil.generateUUID()
          DoobieUtil.runQuery(
            sql"""INSERT INTO methodrouting (methodroutingid, methodname, bankidpattern, isbankidexactmatch, connectorname, parameters)
                  VALUES ($newId, ${nn(methodRouting.methodName)}, ${bankIdPattern.orNull},
                          $isExactMatch, ${nn(methodRouting.connectorName)}, $paramsJson)""".update.run)
          row2commons(RoutingRow(Some(newId), Some(methodRouting.methodName), bankIdPattern,
            Some(isExactMatch), Some(methodRouting.connectorName), Some(paramsJson)))
      }
    }
  }

  override def delete(methodRoutingId: String): Box[Boolean] =
    tryo {
      DoobieUtil.runQuery(
        sql"DELETE FROM methodrouting WHERE methodroutingid = ${nn(methodRoutingId)}".update.run) > 0
    }
}
