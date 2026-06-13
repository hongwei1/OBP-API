package code.organisation

import code.api.util.DoobieUtil
import com.openbankproject.commons.ExecutionContext.Implicits.global
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import java.sql.Timestamp
import java.util.Date
import scala.collection.immutable.List
import scala.concurrent.Future

object DoobieOrganisationProvider extends OrganisationProvider {

  // Table "organisation" (custom dbTableName), IdPK only with custom CreationDate/LastUpdate columns.
  private case class OrgRow(
    organisationid: Option[String],
    name: Option[String],
    website: Option[String],
    logourl: Option[String],
    status: Option[String],
    visibility: Option[String],
    createdbyuserid: Option[String],
    creationdate: Option[Timestamp],
    lastupdate: Option[Timestamp]
  )

  private case class DoobieOrganisation(row: OrgRow) extends OrganisationTrait {
    override def organisationId: String = row.organisationid.getOrElse("")
    override def name: String = row.name.getOrElse("")
    override def website: Option[String] = {
      val v = row.website.orNull
      if (v == null || v.isEmpty) None else Some(v)
    }
    override def logoUrl: Option[String] = {
      val v = row.logourl.orNull
      if (v == null || v.isEmpty) None else Some(v)
    }
    override def status: String = row.status.getOrElse("")
    override def visibility: String = row.visibility.getOrElse("")
    override def createdByUserId: String = row.createdbyuserid.getOrElse("")
    override def createdAt: Date = row.creationdate.orNull
    override def updatedAt: Date = row.lastupdate.orNull
  }

  private def nn(s: String): String = if (s == null) "" else s

  private val selectCols: Fragment =
    fr"""SELECT organisationid, name, website, logourl, status, visibility, createdbyuserid, creationdate, lastupdate
         FROM organisation"""

  private def findRow(organisationId: String): Box[OrgRow] =
    DoobieUtil.runQuery((selectCols ++ fr"WHERE organisationid = ${nn(organisationId)} LIMIT 1").query[OrgRow].option) match {
      case Some(r) => Full(r)
      case None    => Empty
    }

  override def createOrganisation(organisationId: String, name: String, website: Option[String],
                                  logoUrl: Option[String], status: String, visibility: String,
                                  createdByUserId: String): Box[OrganisationTrait] =
    tryo {
      val now = new Timestamp(System.currentTimeMillis())
      val webStr = nn(website.getOrElse(""))
      val logoStr = nn(logoUrl.getOrElse(""))
      DoobieUtil.runQuery(
        sql"""INSERT INTO organisation
                (organisationid, name, website, logourl, status, visibility, createdbyuserid, creationdate, lastupdate)
              VALUES (${nn(organisationId)}, ${nn(name)}, $webStr, $logoStr, ${nn(status)}, ${nn(visibility)},
                      ${nn(createdByUserId)}, $now, $now)""".update.run)
      DoobieOrganisation(OrgRow(Some(nn(organisationId)), Some(nn(name)), Some(webStr), Some(logoStr),
        Some(nn(status)), Some(nn(visibility)), Some(nn(createdByUserId)), Some(now), Some(now)))
    }

  override def getOrganisation(organisationId: String): Box[OrganisationTrait] =
    findRow(organisationId).map(DoobieOrganisation(_))

  override def getAllOrganisations(): Future[Box[List[OrganisationTrait]]] =
    Future {
      tryo {
        DoobieUtil.runQuery(selectCols.query[OrgRow].to[List]).map(DoobieOrganisation(_))
      }
    }

  override def updateOrganisation(organisationId: String, name: Option[String], website: Option[String],
                                  logoUrl: Option[String], status: Option[String],
                                  visibility: Option[String]): Box[OrganisationTrait] =
    findRow(organisationId).flatMap { row =>
      tryo {
        val newName = nn(name.getOrElse(row.name.getOrElse("")))
        val newWeb = nn(website.getOrElse(row.website.getOrElse("")))
        val newLogo = nn(logoUrl.getOrElse(row.logourl.getOrElse("")))
        val newStatus = nn(status.getOrElse(row.status.getOrElse("")))
        val newVisibility = nn(visibility.getOrElse(row.visibility.getOrElse("")))
        val now = new Timestamp(System.currentTimeMillis())
        DoobieUtil.runQuery(
          sql"""UPDATE organisation
                SET name = $newName, website = $newWeb, logourl = $newLogo, status = $newStatus,
                    visibility = $newVisibility, lastupdate = $now
                WHERE organisationid = ${nn(organisationId)}""".update.run)
        DoobieOrganisation(row.copy(
          name = Some(newName), website = Some(newWeb), logourl = Some(newLogo),
          status = Some(newStatus), visibility = Some(newVisibility), lastupdate = Some(now)))
      }
    }

  override def deleteOrganisation(organisationId: String): Box[Boolean] =
    findRow(organisationId).flatMap { _ =>
      tryo {
        DoobieUtil.runQuery(
          sql"DELETE FROM organisation WHERE organisationid = ${nn(organisationId)}".update.run) > 0
      }
    }
}
