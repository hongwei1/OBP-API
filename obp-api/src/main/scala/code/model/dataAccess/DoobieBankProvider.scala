package code.model.dataAccess

import code.api.util.{APIUtil, DoobieUtil}
import com.openbankproject.commons.model.{Bank, BankId}
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import java.sql.Timestamp

/**
 * Doobie-backed data access for the `mappedbank` table, replacing the Lift
 * Mapper queries that previously lived inline in [[code.bankconnectors.LocalMappedConnector]].
 *
 * The MappedBank entity is intentionally retained (Boot.ToSchemify, default-bank
 * bootstrap, settlement-account migration, DeleteBankCascade) during the
 * coexistence phase — both paths read and write the same table.
 */
object DoobieBankProvider {

  private case class BankRow(
    permalink: Option[String],
    fullbankname: Option[String],
    shortbankname: Option[String],
    logourl: Option[String],
    websiteurl: Option[String],
    swiftbic: Option[String],
    national_identifier: Option[String],
    mbankroutingscheme: Option[String],
    mbankroutingaddress: Option[String]
  )

  // A plain wrapper that exposes the row values verbatim — exactly like MappedBank's
  // accessors. The OBP routing-default fallback is applied when the row is built in
  // the read methods below, mirroring getBankLegacy's in-memory mutation (not persisted).
  private case class DoobieBank(row: BankRow) extends Bank {
    override def bankId: BankId            = BankId(row.permalink.getOrElse(""))
    override def shortName: String         = row.shortbankname.getOrElse("")
    override def fullName: String          = row.fullbankname.getOrElse("")
    override def logoUrl: String           = row.logourl.getOrElse("")
    override def websiteUrl: String        = row.websiteurl.getOrElse("")
    override def swiftBic: String          = row.swiftbic.getOrElse("")
    override def nationalIdentifier: String = row.national_identifier.getOrElse("")
    override def bankRoutingScheme: String  = row.mbankroutingscheme.getOrElse("")
    override def bankRoutingAddress: String = row.mbankroutingaddress.getOrElse("")
  }

  private def nn(s: String): String = if (s == null) "" else s

  // Mirrors getBankLegacy: bankRoutingScheme defaults to "OBP", bankRoutingAddress to the bankId.
  private def applyRoutingDefaults(r: BankRow): BankRow =
    r.copy(
      mbankroutingscheme  = Some(APIUtil.ValueOrOBP(r.mbankroutingscheme.orNull)),
      mbankroutingaddress = Some(APIUtil.ValueOrOBPId(r.mbankroutingaddress.orNull, r.permalink.getOrElse("")))
    )

  private val selectCols: Fragment =
    fr"""SELECT permalink, fullbankname, shortbankname, logourl, websiteurl, swiftbic,
                national_identifier, mbankroutingscheme, mbankroutingaddress
         FROM mappedbank"""

  def getBankByBankId(bankId: BankId): Box[Bank] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE permalink = ${bankId.value} LIMIT 1").query[BankRow].option
    ) match {
      case Some(r) => Full(DoobieBank(applyRoutingDefaults(r)))
      case None    => Empty
    }

  def getAllBanks: List[Bank] =
    DoobieUtil.runQuery(selectCols.query[BankRow].to[List]).map(r => DoobieBank(applyRoutingDefaults(r)))

  /**
   * Upsert a bank by permalink. Returns the saved bank with the RAW routing values
   * (no OBP fallback applied) — matching the original createOrUpdateBank, which
   * overwrote the in-memory routing fields with the passed-in values before saveMe.
   */
  def createOrUpdateBank(
    bankId: String,
    fullBankName: String,
    shortBankName: String,
    logoURL: String,
    websiteURL: String,
    swiftBIC: String,
    national_identifier: String,
    bankRoutingScheme: String,
    bankRoutingAddress: String
  ): Box[Bank] = tryo {
    val now = new Timestamp(System.currentTimeMillis())
    val exists = DoobieUtil.runQuery(
      fr"SELECT permalink FROM mappedbank WHERE permalink = ${nn(bankId)} LIMIT 1".query[String].option
    ).isDefined
    if (exists) {
      DoobieUtil.runQuery(
        sql"""UPDATE mappedbank
              SET fullbankname = ${nn(fullBankName)}, shortbankname = ${nn(shortBankName)},
                  logourl = ${nn(logoURL)}, websiteurl = ${nn(websiteURL)}, swiftbic = ${nn(swiftBIC)},
                  national_identifier = ${nn(national_identifier)},
                  mbankroutingscheme = ${nn(bankRoutingScheme)}, mbankroutingaddress = ${nn(bankRoutingAddress)},
                  updatedat = $now
              WHERE permalink = ${nn(bankId)}""".update.run)
    } else {
      DoobieUtil.runQuery(
        sql"""INSERT INTO mappedbank
                (permalink, fullbankname, shortbankname, logourl, websiteurl, swiftbic,
                 national_identifier, mbankroutingscheme, mbankroutingaddress, createdat, updatedat)
              VALUES (${nn(bankId)}, ${nn(fullBankName)}, ${nn(shortBankName)}, ${nn(logoURL)},
                      ${nn(websiteURL)}, ${nn(swiftBIC)}, ${nn(national_identifier)},
                      ${nn(bankRoutingScheme)}, ${nn(bankRoutingAddress)}, $now, $now)""".update.run)
    }
    DoobieBank(BankRow(
      Some(nn(bankId)), Some(nn(fullBankName)), Some(nn(shortBankName)), Some(nn(logoURL)),
      Some(nn(websiteURL)), Some(nn(swiftBIC)), Some(nn(national_identifier)),
      Some(nn(bankRoutingScheme)), Some(nn(bankRoutingAddress))
    ))
  }
}
