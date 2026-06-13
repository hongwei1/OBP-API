package code.routingscheme

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

object DoobieRoutingSchemeProvider extends RoutingSchemeProvider {

  // Tables: routingscheme (custom dbTableName), banksupportedroutingscheme. Both IdPK only.
  private case class RsRow(
    scheme: Option[String],
    country: Option[String],
    category: Option[String],
    addresspattern: Option[String],
    secondaryaddresspattern: Option[String],
    exampleaddress: Option[String],
    description: Option[String],
    downstreamrails: Option[String],
    status: Option[String],
    createdbyuserid: Option[String],
    creationdate: Option[Timestamp],
    lastupdate: Option[Timestamp]
  )

  private case class DoobieRoutingScheme(row: RsRow) extends RoutingSchemeTrait {
    override def scheme: String = row.scheme.getOrElse("")
    override def country: String = row.country.getOrElse("")
    override def category: String = row.category.getOrElse("")
    override def addressPattern: String = row.addresspattern.getOrElse("")
    override def secondaryAddressPattern: Option[String] = {
      val v = row.secondaryaddresspattern.orNull
      if (v == null || v.isEmpty) None else Some(v)
    }
    override def exampleAddress: String = row.exampleaddress.getOrElse("")
    override def description: String = row.description.getOrElse("")
    override def downstreamRails: List[String] = {
      val v = row.downstreamrails.orNull
      if (v == null || v.isEmpty) Nil else v.split(",").toList.map(_.trim).filter(_.nonEmpty)
    }
    override def status: String = row.status.getOrElse("")
    override def createdByUserId: String = row.createdbyuserid.getOrElse("")
    override def createdAt: Date = row.creationdate.orNull
    override def updatedAt: Date = row.lastupdate.orNull
  }

  private case class BsRow(bankid: Option[String], scheme: Option[String], enabled: Option[Boolean], banknotes: Option[String])

  private case class DoobieBankSupportedRoutingScheme(row: BsRow) extends BankSupportedRoutingSchemeTrait {
    override def bankId: String = row.bankid.getOrElse("")
    override def scheme: String = row.scheme.getOrElse("")
    override def enabled: Boolean = row.enabled.getOrElse(true)
    override def bankNotes: Option[String] = {
      val v = row.banknotes.orNull
      if (v == null || v.isEmpty) None else Some(v)
    }
  }

  private def nn(s: String): String = if (s == null) "" else s

  private val selectRs: Fragment =
    fr"""SELECT scheme, country, category, addresspattern, secondaryaddresspattern, exampleaddress,
                description, downstreamrails, status, createdbyuserid, creationdate, lastupdate
         FROM routingscheme"""

  private def findRsRow(scheme: String): Box[RsRow] =
    DoobieUtil.runQuery((selectRs ++ fr"WHERE scheme = ${nn(scheme)} LIMIT 1").query[RsRow].option) match {
      case Some(r) => Full(r)
      case None    => Empty
    }

  override def createRoutingScheme(scheme: String, country: String, category: String, addressPattern: String,
                                   secondaryAddressPattern: Option[String], exampleAddress: String,
                                   description: String, downstreamRails: List[String], status: String,
                                   createdByUserId: String): Box[RoutingSchemeTrait] =
    tryo {
      val now = new Timestamp(System.currentTimeMillis())
      val secondary = nn(secondaryAddressPattern.getOrElse(""))
      val rails = nn(downstreamRails.mkString(","))
      DoobieUtil.runQuery(
        sql"""INSERT INTO routingscheme
                (scheme, country, category, addresspattern, secondaryaddresspattern, exampleaddress,
                 description, downstreamrails, status, createdbyuserid, creationdate, lastupdate)
              VALUES (${nn(scheme)}, ${nn(country)}, ${nn(category)}, ${nn(addressPattern)}, $secondary,
                      ${nn(exampleAddress)}, ${nn(description)}, $rails, ${nn(status)}, ${nn(createdByUserId)}, $now, $now)""".update.run)
      DoobieRoutingScheme(RsRow(Some(nn(scheme)), Some(nn(country)), Some(nn(category)), Some(nn(addressPattern)),
        Some(secondary), Some(nn(exampleAddress)), Some(nn(description)), Some(rails), Some(nn(status)),
        Some(nn(createdByUserId)), Some(now), Some(now)))
    }

  override def getRoutingScheme(scheme: String): Box[RoutingSchemeTrait] =
    findRsRow(scheme).map(DoobieRoutingScheme(_))

  override def getRoutingSchemes(country: Option[String], category: Option[String], status: Option[String],
                                 rail: Option[String], limit: Int, offset: Int): Future[Box[(List[RoutingSchemeTrait], Int)]] =
    Future {
      tryo {
        val whereFrags: List[Fragment] =
          country.map(c => fr"country = ${nn(c)}").toList :::
          category.map(c => fr"category = ${nn(c)}").toList :::
          status.map(s => fr"status = ${nn(s)}").toList
        val whereClause =
          if (whereFrags.isEmpty) Fragment.empty
          else fr"WHERE" ++ whereFrags.reduceLeft((a, b) => a ++ fr"AND" ++ b)
        // Count BEFORE applying limit/offset for total.
        val total: Int = DoobieUtil.runQuery(
          (fr"SELECT COUNT(*) FROM routingscheme" ++ whereClause).query[Int].unique)
        val rows: List[RoutingSchemeTrait] = DoobieUtil.runQuery(
          (selectRs ++ whereClause ++ fr"ORDER BY scheme ASC LIMIT $limit OFFSET $offset").query[RsRow].to[List])
          .map(DoobieRoutingScheme(_))
        // Rail is a free-text tag list (CSV); filter in-memory after the SQL pass (mirrors the Mapper).
        val filtered = rail match {
          case Some(r) => rows.filter(_.downstreamRails.contains(r))
          case None    => rows
        }
        (filtered, total)
      }
    }

  override def updateRoutingScheme(scheme: String, addressPattern: Option[String],
                                   secondaryAddressPattern: Option[String], exampleAddress: Option[String],
                                   description: Option[String], downstreamRails: Option[List[String]],
                                   status: Option[String]): Box[RoutingSchemeTrait] =
    findRsRow(scheme).flatMap { row =>
      tryo {
        val newAddr = nn(addressPattern.getOrElse(row.addresspattern.getOrElse("")))
        val newSecondary = nn(secondaryAddressPattern.getOrElse(row.secondaryaddresspattern.getOrElse("")))
        val newExample = nn(exampleAddress.getOrElse(row.exampleaddress.getOrElse("")))
        val newDesc = nn(description.getOrElse(row.description.getOrElse("")))
        val newRails = nn(downstreamRails.map(_.mkString(",")).getOrElse(row.downstreamrails.getOrElse("")))
        val newStatus = nn(status.getOrElse(row.status.getOrElse("")))
        val now = new Timestamp(System.currentTimeMillis())
        DoobieUtil.runQuery(
          sql"""UPDATE routingscheme
                SET addresspattern = $newAddr, secondaryaddresspattern = $newSecondary, exampleaddress = $newExample,
                    description = $newDesc, downstreamrails = $newRails, status = $newStatus, lastupdate = $now
                WHERE scheme = ${nn(scheme)}""".update.run)
        DoobieRoutingScheme(row.copy(
          addresspattern = Some(newAddr), secondaryaddresspattern = Some(newSecondary), exampleaddress = Some(newExample),
          description = Some(newDesc), downstreamrails = Some(newRails), status = Some(newStatus), lastupdate = Some(now)))
      }
    }

  override def deleteRoutingScheme(scheme: String): Box[Boolean] =
    // Soft delete — set status to RETIRED, keep the row for historical resolution.
    findRsRow(scheme).flatMap { _ =>
      tryo {
        val now = new Timestamp(System.currentTimeMillis())
        DoobieUtil.runQuery(
          sql"UPDATE routingscheme SET status = 'RETIRED', lastupdate = $now WHERE scheme = ${nn(scheme)}".update.run)
        true
      }
    }

  override def getBankSupportedRoutingSchemes(bankId: String): Future[Box[List[BankSupportedRoutingSchemeTrait]]] =
    Future {
      tryo {
        DoobieUtil.runQuery(
          fr"SELECT bankid, scheme, enabled, banknotes FROM banksupportedroutingscheme WHERE bankid = ${nn(bankId)}"
            .query[BsRow].to[List]).map(DoobieBankSupportedRoutingScheme(_))
      }
    }

  override def putBankSupportedRoutingScheme(bankId: String, scheme: String, enabled: Boolean,
                                             bankNotes: Option[String]): Box[BankSupportedRoutingSchemeTrait] = {
    val existing = DoobieUtil.runQuery(
      fr"SELECT bankid, scheme, enabled, banknotes FROM banksupportedroutingscheme WHERE bankid = ${nn(bankId)} AND scheme = ${nn(scheme)} LIMIT 1"
        .query[BsRow].option)
    tryo {
      val notes = nn(bankNotes.getOrElse(""))
      existing match {
        case Some(_) =>
          DoobieUtil.runQuery(
            sql"""UPDATE banksupportedroutingscheme SET enabled = $enabled, banknotes = $notes
                  WHERE bankid = ${nn(bankId)} AND scheme = ${nn(scheme)}""".update.run)
        case None =>
          DoobieUtil.runQuery(
            sql"""INSERT INTO banksupportedroutingscheme (bankid, scheme, enabled, banknotes)
                  VALUES (${nn(bankId)}, ${nn(scheme)}, $enabled, $notes)""".update.run)
      }
      DoobieBankSupportedRoutingScheme(BsRow(Some(nn(bankId)), Some(nn(scheme)), Some(enabled), Some(notes)))
    }
  }
}
