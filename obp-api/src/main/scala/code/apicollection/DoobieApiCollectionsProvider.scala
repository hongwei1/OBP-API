package code.apicollection

import code.api.util.{APIUtil, DoobieUtil}
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import java.sql.Timestamp

object DoobieApiCollectionsProvider extends ApiCollectionsProvider {

  private case class Row(
    apicollectionid: String,
    userid: String,
    apicollectionname: String,
    issharable: Boolean,
    description: String
  )

  private case class DoobieApiCollection(
    apiCollectionId: String,
    userId: String,
    apiCollectionName: String,
    isSharable: Boolean,
    description: String
  ) extends ApiCollectionTrait

  private val selectCols: Fragment =
    fr"""SELECT apicollectionid, userid, apicollectionname, issharable, description
         FROM apicollection"""

  private def toTrait(row: Row): ApiCollectionTrait =
    DoobieApiCollection(row.apicollectionid, row.userid, row.apicollectionname, row.issharable, row.description)

  override def createApiCollection(userId: String, apiCollectionName: String,
                                   isSharable: Boolean, description: String): Box[ApiCollectionTrait] =
    tryo {
      val newId = APIUtil.generateUUID()
      val now   = new Timestamp(System.currentTimeMillis())
      DoobieUtil.runQuery(
        sql"""INSERT INTO apicollection
                (apicollectionid, userid, apicollectionname, issharable, description, createdat, updatedat)
              VALUES ($newId, $userId, $apiCollectionName, $isSharable, $description, $now, $now)
           """.update.run
      )
      DoobieApiCollection(newId, userId, apiCollectionName, isSharable, description)
    }

  override def getApiCollectionById(apiCollectionId: String): Box[ApiCollectionTrait] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE apicollectionid = $apiCollectionId LIMIT 1").query[Row].option
    ) match {
      case Some(row) => Full(toTrait(row))
      case None      => Empty
    }

  override def updateApiCollectionById(apiCollectionId: String, name: String,
                                       description: String, isSharable: Boolean): Box[ApiCollectionTrait] = {
    val now = new Timestamp(System.currentTimeMillis())
    val rows = DoobieUtil.runQuery(
      sql"""UPDATE apicollection
            SET apicollectionname = $name, description = $description, issharable = $isSharable, updatedat = $now
            WHERE apicollectionid = $apiCollectionId
         """.update.run
    )
    if (rows > 0) getApiCollectionById(apiCollectionId) else Empty
  }

  override def getApiCollectionByUserIdAndCollectionName(userId: String,
                                                          apiCollectionName: String): Box[ApiCollectionTrait] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"""WHERE userid = $userId AND apicollectionname = $apiCollectionName LIMIT 1""")
        .query[Row].option
    ) match {
      case Some(row) => Full(toTrait(row))
      case None      => Empty
    }

  override def getAllApiCollections(): List[ApiCollectionTrait] =
    DoobieUtil.runQuery(selectCols.query[Row].to[List]).map(toTrait)

  override def deleteApiCollectionById(apiCollectionId: String): Box[Boolean] =
    tryo {
      DoobieUtil.runQuery(
        sql"DELETE FROM apicollection WHERE apicollectionid = $apiCollectionId".update.run
      ) > 0
    }

  override def getApiCollectionsByUserId(userId: String): List[ApiCollectionTrait] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE userid = $userId").query[Row].to[List]
    ).map(toTrait)
}
