package code.apicollectionendpoint

import code.api.util.{APIUtil, DoobieUtil}
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import java.sql.Timestamp

object DoobieApiCollectionEndpointsProvider extends ApiCollectionEndpointsProvider {

  private case class Row(
    apicollectionendpointid: String,
    apicollectionid: String,
    operationid: String
  )

  private case class DoobieApiCollectionEndpoint(
    apiCollectionEndpointId: String,
    apiCollectionId: String,
    operationId: String
  ) extends ApiCollectionEndpointTrait

  private val selectCols: Fragment =
    fr"""SELECT apicollectionendpointid, apicollectionid, operationid
         FROM apicollectionendpoint"""

  private def toTrait(row: Row): ApiCollectionEndpointTrait =
    DoobieApiCollectionEndpoint(row.apicollectionendpointid, row.apicollectionid, row.operationid)

  override def createApiCollectionEndpoint(apiCollectionId: String,
                                           operationId: String): Box[ApiCollectionEndpointTrait] =
    tryo {
      val newId = APIUtil.generateUUID()
      val now   = new Timestamp(System.currentTimeMillis())
      DoobieUtil.runQuery(
        sql"""INSERT INTO apicollectionendpoint
                (apicollectionendpointid, apicollectionid, operationid, createdat, updatedat)
              VALUES ($newId, $apiCollectionId, $operationId, $now, $now)
           """.update.run
      )
      DoobieApiCollectionEndpoint(newId, apiCollectionId, operationId)
    }

  override def getApiCollectionEndpointById(apiCollectionEndpointId: String): Box[ApiCollectionEndpointTrait] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE apicollectionendpointid = $apiCollectionEndpointId LIMIT 1").query[Row].option
    ) match {
      case Some(row) => Full(toTrait(row))
      case None      => Empty
    }

  override def getApiCollectionEndpointByApiCollectionIdAndOperationId(apiCollectionId: String,
                                                                        operationId: String): Box[ApiCollectionEndpointTrait] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"""WHERE apicollectionid = $apiCollectionId
                          AND operationid = $operationId LIMIT 1""").query[Row].option
    ) match {
      case Some(row) => Full(toTrait(row))
      case None      => Empty
    }

  override def getApiCollectionEndpoints(apiCollectionId: String): List[ApiCollectionEndpointTrait] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE apicollectionid = $apiCollectionId").query[Row].to[List]
    ).map(toTrait)

  override def deleteApiCollectionEndpointById(apiCollectionEndpointId: String): Box[Boolean] =
    tryo {
      DoobieUtil.runQuery(
        sql"DELETE FROM apicollectionendpoint WHERE apicollectionendpointid = $apiCollectionEndpointId".update.run
      ) > 0
    }
}
