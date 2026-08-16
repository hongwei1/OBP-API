package code.branches

import code.api.util.{DoobieUtil, OBPLimit, OBPOffset, OBPQueryParam}
import code.branches.Branches.{Branch, DriveUpString, LobbyString}
import com.openbankproject.commons.model.{Meta => CommonMeta, _}
import doobie._
import doobie.implicits._

import scala.collection.immutable.List

object DoobieBranchesProvider extends BranchesProvider {

  // 51 columns — split across three case classes to stay within Scala 2's 22-field case-class limit.

  private case class BranchRow1(
    mbranchid: Option[String],
    mbankid: Option[String],
    mname: Option[String],
    mline1: Option[String],
    mline2: Option[String],
    mline3: Option[String],
    mcity: Option[String],
    mcounty: Option[String],
    mstate: Option[String],
    mcountrycode: Option[String],
    mpostcode: Option[String],
    mlocationlatitude: Option[Double],
    mlocationlongitude: Option[Double],
    mlicenseid: Option[String],
    mlicensename: Option[String],
    mlobbyhours: Option[String],
    mdriveuphours: Option[String],
    mbranchroutingscheme: Option[String],
    mbranchroutingaddress: Option[String],
    mlobbyopeningtimeonmonday: Option[String],
    mlobbyclosingtimeonmonday: Option[String],
    mlobbyopeningtimeontuesday: Option[String]
  )

  private case class BranchRow2(
    mlobbyclosingtimeontuesday: Option[String],
    mlobbyopeningtimeonwednesday: Option[String],
    mlobbyclosingtimeonwednesday: Option[String],
    mlobbyopeningtimeonthursday: Option[String],
    mlobbyclosingtimeonthursday: Option[String],
    mlobbyopeningtimeonfriday: Option[String],
    mlobbyclosingtimeonfriday: Option[String],
    mlobbyopeningtimeonsaturday: Option[String],
    mlobbyclosingtimeonsaturday: Option[String],
    mlobbyopeningtimeonsunday: Option[String],
    mlobbyclosingtimeonsunday: Option[String],
    mdriveupopeningtimeonmonday: Option[String],
    mdriveupclosingtimeonmonday: Option[String],
    mdriveupopeningtimeontuesday: Option[String],
    mdriveupclosingtimeontuesday: Option[String],
    mdriveupopeningtimeonwednesday: Option[String],
    mdriveupclosingtimeonwednesday: Option[String],
    mdriveupopeningtimeonthursday: Option[String],
    mdriveupclosingtimeonthursday: Option[String],
    mdriveupopeningtimeonfriday: Option[String],
    mdriveupclosingtimeonfriday: Option[String],
    mdriveupopeningtimeonsaturday: Option[String]
  )

  private case class BranchRow3(
    mdriveupclosingtimeonsaturday: Option[String],
    mdriveupopeningtimeonsunday: Option[String],
    mdriveupclosingtimeonsunday: Option[String],
    misaccessible: Option[String],
    maccessiblefeatures: Option[String],
    mbranchtype: Option[String],
    mmoreinfo: Option[String],
    mphonenumber: Option[String],
    misdeleted: Option[Boolean]
  )

  private type BranchRow = (BranchRow1, BranchRow2, BranchRow3)

  private def nn(s: String): String = if (s == null) "" else s

  // Mirror MappedBranch.isAccessible: "Y" -> Some(true), "N" -> Some(false), else None
  private def toOptBool(s: Option[String]): Option[Boolean] = s.getOrElse("") match {
    case "Y" => Some(true)
    case "N" => Some(false)
    case _   => None
  }

  private def rowToBranch(r: BranchRow): Branch = {
    val (r1, r2, r3) = r

    val branchIdStr     = r1.mbranchid.getOrElse("")
    val routingScheme   = r1.mbranchroutingscheme.getOrElse("")
    val routingAddress  = r1.mbranchroutingaddress.getOrElse("")

    // Mirror MappedBranch.branchRouting: scheme defaults to "BRANCH_ID" when null/empty;
    // address defaults to the branchId when null/empty.
    val scheme  = if (routingScheme == null || routingScheme == "") "BRANCH_ID" else routingScheme
    val address = if (routingAddress == null || routingAddress == "") branchIdStr else routingAddress

    Branch(
      branchId = BranchId(branchIdStr),
      bankId   = BankId(r1.mbankid.getOrElse("")),
      name     = r1.mname.getOrElse(""),
      address  = Address(
        line1       = r1.mline1.getOrElse(""),
        line2       = r1.mline2.getOrElse(""),
        line3       = r1.mline3.getOrElse(""),
        city        = r1.mcity.getOrElse(""),
        county      = Some(r1.mcounty.getOrElse("")), // MappedBranch always wraps county in Some(...)
        state       = r1.mstate.getOrElse(""),
        countryCode = r1.mcountrycode.getOrElse(""),
        postCode    = r1.mpostcode.getOrElse("")
      ),
      location = Location(
        latitude  = r1.mlocationlatitude.getOrElse(0.0),
        longitude = r1.mlocationlongitude.getOrElse(0.0),
        date = None,
        user = None
      ),
      lobbyString   = Some(LobbyString(hours = r1.mlobbyhours.getOrElse(""))),
      driveUpString = Some(DriveUpString(hours = r1.mdriveuphours.getOrElse(""))),
      meta = CommonMeta(license = License(
        id   = r1.mlicenseid.getOrElse(""),
        name = r1.mlicensename.getOrElse("")
      )),
      branchRouting = Some(Routing(scheme = scheme, address = address)),
      lobby = Some(Lobby(
        monday = List(OpeningTimes(
          openingTime = r1.mlobbyopeningtimeonmonday.getOrElse(""),
          closingTime = r1.mlobbyclosingtimeonmonday.getOrElse("")
        )),
        tuesday = List(OpeningTimes(
          openingTime = r1.mlobbyopeningtimeontuesday.getOrElse(""),
          closingTime = r2.mlobbyclosingtimeontuesday.getOrElse("")
        )),
        wednesday = List(OpeningTimes(
          openingTime = r2.mlobbyopeningtimeonwednesday.getOrElse(""),
          closingTime = r2.mlobbyclosingtimeonwednesday.getOrElse("")
        )),
        thursday = List(OpeningTimes(
          openingTime = r2.mlobbyopeningtimeonthursday.getOrElse(""),
          closingTime = r2.mlobbyclosingtimeonthursday.getOrElse("")
        )),
        friday = List(OpeningTimes(
          openingTime = r2.mlobbyopeningtimeonfriday.getOrElse(""),
          closingTime = r2.mlobbyclosingtimeonfriday.getOrElse("")
        )),
        saturday = List(OpeningTimes(
          openingTime = r2.mlobbyopeningtimeonsaturday.getOrElse(""),
          closingTime = r2.mlobbyclosingtimeonsaturday.getOrElse("")
        )),
        sunday = List(OpeningTimes(
          openingTime = r2.mlobbyopeningtimeonsunday.getOrElse(""),
          closingTime = r2.mlobbyclosingtimeonsunday.getOrElse("")
        ))
      )),
      driveUp = Some(DriveUp(
        monday = OpeningTimes(
          openingTime = r2.mdriveupopeningtimeonmonday.getOrElse(""),
          closingTime = r2.mdriveupclosingtimeonmonday.getOrElse("")
        ),
        tuesday = OpeningTimes(
          openingTime = r2.mdriveupopeningtimeontuesday.getOrElse(""),
          closingTime = r2.mdriveupclosingtimeontuesday.getOrElse("")
        ),
        wednesday = OpeningTimes(
          openingTime = r2.mdriveupopeningtimeonwednesday.getOrElse(""),
          closingTime = r2.mdriveupclosingtimeonwednesday.getOrElse("")
        ),
        thursday = OpeningTimes(
          openingTime = r2.mdriveupopeningtimeonthursday.getOrElse(""),
          closingTime = r2.mdriveupclosingtimeonthursday.getOrElse("")
        ),
        friday = OpeningTimes(
          openingTime = r2.mdriveupopeningtimeonfriday.getOrElse(""),
          closingTime = r2.mdriveupclosingtimeonfriday.getOrElse("")
        ),
        saturday = OpeningTimes(
          openingTime = r2.mdriveupopeningtimeonsaturday.getOrElse(""),
          closingTime = r3.mdriveupclosingtimeonsaturday.getOrElse("")
        ),
        sunday = OpeningTimes(
          openingTime = r3.mdriveupopeningtimeonsunday.getOrElse(""),
          closingTime = r3.mdriveupclosingtimeonsunday.getOrElse("")
        )
      )),
      isAccessible       = toOptBool(r3.misaccessible),
      accessibleFeatures = Some(r3.maccessiblefeatures.getOrElse("")), // MappedBranch always wraps in Some(...)
      branchType         = Some(r3.mbranchtype.getOrElse("")),         // MappedBranch always wraps in Some(...)
      moreInfo           = Some(r3.mmoreinfo.getOrElse("")),           // MappedBranch always wraps in Some(...)
      phoneNumber        = Some(r3.mphonenumber.getOrElse("")),        // MappedBranch always wraps in Some(...)
      isDeleted          = Some(r3.misdeleted.getOrElse(false))        // MappedBranch always wraps in Some(...)
    )
  }

  private val selectCols: Fragment =
    fr"""SELECT
         mbranchid, mbankid, mname, mline1, mline2, mline3, mcity, mcounty, mstate, mcountrycode, mpostcode,
         mlocationlatitude, mlocationlongitude, mlicenseid, mlicensename,
         mlobbyhours, mdriveuphours, mbranchroutingscheme, mbranchroutingaddress,
         mlobbyopeningtimeonmonday, mlobbyclosingtimeonmonday, mlobbyopeningtimeontuesday,
         mlobbyclosingtimeontuesday, mlobbyopeningtimeonwednesday, mlobbyclosingtimeonwednesday,
         mlobbyopeningtimeonthursday, mlobbyclosingtimeonthursday, mlobbyopeningtimeonfriday,
         mlobbyclosingtimeonfriday, mlobbyopeningtimeonsaturday, mlobbyclosingtimeonsaturday,
         mlobbyopeningtimeonsunday, mlobbyclosingtimeonsunday,
         mdriveupopeningtimeonmonday, mdriveupclosingtimeonmonday, mdriveupopeningtimeontuesday,
         mdriveupclosingtimeontuesday, mdriveupopeningtimeonwednesday, mdriveupclosingtimeonwednesday,
         mdriveupopeningtimeonthursday, mdriveupclosingtimeonthursday, mdriveupopeningtimeonfriday,
         mdriveupclosingtimeonfriday, mdriveupopeningtimeonsaturday, mdriveupclosingtimeonsaturday,
         mdriveupopeningtimeonsunday, mdriveupclosingtimeonsunday,
         misaccessible, maccessiblefeatures, mbranchtype, mmoreinfo, mphonenumber, misdeleted
         FROM mappedbranch"""

  override protected def getBranchFromProvider(bankId: BankId, branchId: BranchId): Option[BranchT] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE mbankid = ${nn(bankId.value)} AND mbranchid = ${nn(branchId.value)} LIMIT 1")
        .query[BranchRow].option).map(rowToBranch)

  override protected def getBranchesFromProvider(bankId: BankId, queryParams: List[OBPQueryParam]): Option[List[BranchT]] = {
    logger.debug(s"getBranchesFromProvider says bankId is $bankId")

    // Mirror MappedBranch.findAll filter: bankId AND mIsDeleted = false, plus optional limit/offset.
    val limitFr  = queryParams.collectFirst { case OBPLimit(v)  => fr"LIMIT $v"  }.getOrElse(Fragment.empty)
    val offsetFr = queryParams.collectFirst { case OBPOffset(v) => fr"OFFSET $v" }.getOrElse(Fragment.empty)
    Some(DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE mbankid = ${nn(bankId.value)} AND misdeleted = false" ++ limitFr ++ offsetFr)
        .query[BranchRow].to[List]).map(rowToBranch))
  }
}
