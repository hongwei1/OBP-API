package code.socialmedia

import code.api.util.DoobieUtil
import doobie._
import doobie.implicits._
import net.liftweb.util.Helpers.tryo

import java.sql.Timestamp
import java.util.Date
import scala.collection.immutable.List

object DoobieSocialMediasProvider extends SocialMediaHandleProvider {

  // Table "mappedsocialmedia". The user (FK) and bank columns are not part of the SocialMedia trait
  // and are not set by addSocialMedias, so they are left at their DB defaults (null).
  private case class SmRow(
    mcustomernumber: Option[String],
    mtype: Option[String],
    mhandle: Option[String],
    mdateadded: Option[Timestamp],
    mdateactivated: Option[Timestamp]
  )

  private case class DoobieSocialMedia(row: SmRow) extends SocialMedia {
    override def customerNumber: String = row.mcustomernumber.getOrElse("")
    override def `type`: String = row.mtype.getOrElse("")
    override def handle: String = row.mhandle.getOrElse("")
    override def dateAdded: Date = row.mdateadded.orNull
    override def dateActivated: Date = row.mdateactivated.orNull
  }

  private def nn(s: String): String = if (s == null) "" else s

  private val selectCols: Fragment =
    fr"SELECT mcustomernumber, mtype, mhandle, mdateadded, mdateactivated FROM mappedsocialmedia"

  override def getSocialMedias(customerNumber: String): List[SocialMedia] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE mcustomernumber = ${nn(customerNumber)} ORDER BY updatedat DESC")
        .query[SmRow].to[List]).map(DoobieSocialMedia(_))

  override def addSocialMedias(customerNumber: String, `type`: String, handle: String,
                               dateAdded: Date, dateActivated: Date): Boolean =
    tryo {
      val added: Option[Timestamp] = Option(dateAdded).map(d => new Timestamp(d.getTime))
      val activated: Option[Timestamp] = Option(dateActivated).map(d => new Timestamp(d.getTime))
      val now = new Timestamp(System.currentTimeMillis())
      DoobieUtil.runQuery(
        sql"""INSERT INTO mappedsocialmedia
                (mcustomernumber, mtype, mhandle, mdateadded, mdateactivated, createdat, updatedat)
              VALUES (${nn(customerNumber)}, ${nn(`type`)}, ${nn(handle)}, $added, $activated, $now, $now)""".update.run)
      true
    }.getOrElse(false)
}
