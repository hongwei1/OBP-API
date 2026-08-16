package code.endpointMapping

import code.api.util.{APIUtil, CustomJsonFormats, DoobieUtil}
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo
import org.apache.commons.lang3.StringUtils

object DoobieEndpointMappingProvider extends EndpointMappingProvider with CustomJsonFormats {

  private case class EndpointMappingRow(
    endpointmappingid: Option[String],
    operationid: Option[String],
    requestmapping: Option[String],
    responsemapping: Option[String],
    bankid: Option[String]
  )

  private case class DoobieEndpointMapping(row: EndpointMappingRow) extends EndpointMappingT {
    override def endpointMappingId: Option[String] = row.endpointmappingid
    override def operationId: String               = row.operationid.getOrElse("")
    override def requestMapping: String            = row.requestmapping.getOrElse("")
    override def responseMapping: String           = row.responsemapping.getOrElse("")
    // Mirror MappedEndpointMapping.bankId: null or empty -> None, else Some
    override def bankId: Option[String] = row.bankid match {
      case Some(b) if b != null && b.nonEmpty => Some(b)
      case _                                  => None
    }
  }

  private def nn(s: String): String = if (s == null) "" else s

  private val selectCols: Fragment =
    fr"SELECT endpointmappingid, operationid, requestmapping, responsemapping, bankid FROM endpointmapping"

  // Mirror MappedEndpointMappingProvider.getByEndpointMappingId(endpointMappingId) — no bankId filter
  private def findByEndpointMappingId(endpointMappingId: String): Option[EndpointMappingRow] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE endpointmappingid = ${nn(endpointMappingId)} LIMIT 1")
        .query[EndpointMappingRow].option)

  // Mirror MappedEndpointMappingProvider.getByEndpointMappingId(bankId, endpointMappingId)
  private def findByEndpointMappingId(bankId: String, endpointMappingId: String): Option[EndpointMappingRow] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE endpointmappingid = ${nn(endpointMappingId)} AND bankid = ${nn(bankId)} LIMIT 1")
        .query[EndpointMappingRow].option)

  override def getById(bankId: Option[String], endpointMappingId: String): Box[EndpointMappingT] = {
    val found =
      if (bankId.isEmpty) findByEndpointMappingId(endpointMappingId)
      else findByEndpointMappingId(bankId.getOrElse(""), endpointMappingId)
    found match {
      case Some(row) => Full(DoobieEndpointMapping(row))
      case None      => Empty
    }
  }

  override def getByOperationId(bankId: Option[String], operationId: String): Box[EndpointMappingT] = {
    val query =
      if (bankId.isEmpty)
        selectCols ++ fr"WHERE operationid = ${nn(operationId)} LIMIT 1"
      else
        selectCols ++ fr"WHERE operationid = ${nn(operationId)} AND bankid = ${nn(bankId.getOrElse(""))} LIMIT 1"
    DoobieUtil.runQuery(query.query[EndpointMappingRow].option) match {
      case Some(row) => Full(DoobieEndpointMapping(row))
      case None      => Empty
    }
  }

  override def createOrUpdate(bankId: Option[String], endpointMapping: EndpointMappingT): Box[EndpointMappingT] = {
    // to find exists endpointMapping, if endpointMappingId supplied, query by endpointMappingId (no bankId filter — mirror Lift)
    val existsEndpointMapping: Option[EndpointMappingRow] = endpointMapping.endpointMappingId match {
      case Some(id) if StringUtils.isNotBlank(id) => findByEndpointMappingId(id)
      case _                                      => None
    }
    // Mirror Lift: BankId(endpointMapping.bankId.getOrElse(null)) — null bankId persists as NULL
    val bankIdToPersist: Option[String] = endpointMapping.bankId
    tryo {
      existsEndpointMapping match {
        case Some(existing) =>
          val id = existing.endpointmappingid.getOrElse("")
          DoobieUtil.runQuery(
            sql"""UPDATE endpointmapping
                  SET operationid = ${nn(endpointMapping.operationId)},
                      requestmapping = ${nn(endpointMapping.requestMapping)},
                      responsemapping = ${nn(endpointMapping.responseMapping)},
                      bankid = ${bankIdToPersist}
                  WHERE endpointmappingid = ${nn(id)}""".update.run)
          DoobieEndpointMapping(EndpointMappingRow(
            Some(id),
            Some(nn(endpointMapping.operationId)),
            Some(nn(endpointMapping.requestMapping)),
            Some(nn(endpointMapping.responseMapping)),
            bankIdToPersist
          ))
        case None =>
          // EndpointMappingId is a MappedUUID — auto-generated on create in Lift
          val newId = APIUtil.generateUUID()
          DoobieUtil.runQuery(
            sql"""INSERT INTO endpointmapping
                    (endpointmappingid, operationid, requestmapping, responsemapping, bankid)
                  VALUES (${newId}, ${nn(endpointMapping.operationId)},
                          ${nn(endpointMapping.requestMapping)}, ${nn(endpointMapping.responseMapping)},
                          ${bankIdToPersist})""".update.run)
          DoobieEndpointMapping(EndpointMappingRow(
            Some(newId),
            Some(nn(endpointMapping.operationId)),
            Some(nn(endpointMapping.requestMapping)),
            Some(nn(endpointMapping.responseMapping)),
            bankIdToPersist
          ))
      }
    }
  }

  override def delete(bankId: Option[String], endpointMappingId: String): Box[Boolean] = {
    val found =
      if (bankId.isEmpty) findByEndpointMappingId(endpointMappingId)
      else findByEndpointMappingId(bankId.getOrElse(""), endpointMappingId)
    found.map { row =>
      val id = row.endpointmappingid.getOrElse("")
      DoobieUtil.runQuery(
        sql"DELETE FROM endpointmapping WHERE endpointmappingid = ${nn(id)}".update.run) >= 0
    }
  }

  override def getAllEndpointMappings(bankId: Option[String]): List[EndpointMappingT] = {
    val query =
      if (bankId.isEmpty)
        selectCols
      else
        selectCols ++ fr"WHERE bankid = ${nn(bankId.getOrElse(""))}"
    DoobieUtil.runQuery(query.query[EndpointMappingRow].to[List]).map(DoobieEndpointMapping(_))
  }
}
