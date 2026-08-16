package code.featuredapicollection

import code.api.util.{APIUtil, DoobieUtil}
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import java.sql.Timestamp

object DoobieFeaturedApiCollectionsProvider extends FeaturedApiCollectionsProvider {

  private case class Row(
    featuredapicollectionid: String,
    apicollectionid: String,
    sortorder: Int
  )

  private case class DoobieFeaturedApiCollection(
    featuredApiCollectionId: String,
    apiCollectionId: String,
    sortOrder: Int
  ) extends FeaturedApiCollectionTrait

  private val selectCols: Fragment =
    fr"""SELECT featuredapicollectionid, apicollectionid, sortorder
         FROM featuredapicollection"""

  private def toTrait(row: Row): FeaturedApiCollectionTrait =
    DoobieFeaturedApiCollection(row.featuredapicollectionid, row.apicollectionid, row.sortorder)

  override def createFeaturedApiCollection(apiCollectionId: String, sortOrder: Int): Box[FeaturedApiCollectionTrait] =
    tryo {
      val newId = APIUtil.generateUUID()
      val now   = new Timestamp(System.currentTimeMillis())
      DoobieUtil.runQuery(
        sql"""INSERT INTO featuredapicollection
                (featuredapicollectionid, apicollectionid, sortorder, createdat, updatedat)
              VALUES ($newId, $apiCollectionId, $sortOrder, $now, $now)
           """.update.run
      )
      DoobieFeaturedApiCollection(newId, apiCollectionId, sortOrder)
    }

  override def getFeaturedApiCollectionById(featuredApiCollectionId: String): Box[FeaturedApiCollectionTrait] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE featuredapicollectionid = $featuredApiCollectionId LIMIT 1").query[Row].option
    ) match {
      case Some(row) => Full(toTrait(row))
      case None      => Empty
    }

  override def getFeaturedApiCollectionByApiCollectionId(apiCollectionId: String): Box[FeaturedApiCollectionTrait] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE apicollectionid = $apiCollectionId LIMIT 1").query[Row].option
    ) match {
      case Some(row) => Full(toTrait(row))
      case None      => Empty
    }

  override def updateFeaturedApiCollection(featuredApiCollectionId: String,
                                           sortOrder: Int): Box[FeaturedApiCollectionTrait] = {
    val now = new Timestamp(System.currentTimeMillis())
    val rows = DoobieUtil.runQuery(
      sql"""UPDATE featuredapicollection
            SET sortorder = $sortOrder, updatedat = $now
            WHERE featuredapicollectionid = $featuredApiCollectionId
         """.update.run
    )
    if (rows > 0) getFeaturedApiCollectionById(featuredApiCollectionId) else Empty
  }

  override def getAllFeaturedApiCollections(): List[FeaturedApiCollectionTrait] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"ORDER BY sortorder ASC").query[Row].to[List]
    ).map(toTrait)

  override def deleteFeaturedApiCollectionById(featuredApiCollectionId: String): Box[Boolean] =
    tryo {
      DoobieUtil.runQuery(
        sql"DELETE FROM featuredapicollection WHERE featuredapicollectionid = $featuredApiCollectionId".update.run
      ) > 0
    }

  override def deleteFeaturedApiCollectionByApiCollectionId(apiCollectionId: String): Box[Boolean] =
    tryo {
      DoobieUtil.runQuery(
        sql"DELETE FROM featuredapicollection WHERE apicollectionid = $apiCollectionId".update.run
      ) > 0
    }
}
