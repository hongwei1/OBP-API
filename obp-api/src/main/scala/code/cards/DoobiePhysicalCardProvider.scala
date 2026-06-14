package code.cards

import java.sql.Timestamp
import java.util.Date

import code.api.util.{APIUtil, CallContext, DoobieUtil, ErrorMessages, HashUtil, OBPAccountId, OBPCustomerId, OBPQueryParam}
import code.model.dataAccess.MappedBankAccount
import code.views.Views._
import com.openbankproject.commons.model._
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Failure, Full}
import net.liftweb.util.Helpers.tryo

import scala.collection.immutable.List

/**
 * Doobie implementation of the [[PhysicalCardProvider]] vend (Lift Mapper -> Doobie migration).
 *
 * The `mappedphysicalcard` table is accessed via Doobie SQL through `DoobieUtil.runQuery`
 * (joining the http4s/Lift request transaction when one is present). The companion `pinreset`
 * table is written/read the same way.
 *
 * Concrete (`MappedPhysicalCard`) and trait (`PhysicalCardTrait`) return types are both satisfied by
 * reusing the Lift `MappedPhysicalCard` class as an in-memory holder (no `saveMe`). This is required
 * because downstream callers (`LocalMappedConnector`) read the `account` accessor — which resolves the
 * `maccount` foreign key to a `MappedBankAccount` via `mAccount.obj` — and the `pinResets` accessor,
 * which iterates the `mPinResets` one-to-many collection. `MappedBankAccount` is still a Lift entity, so
 * setting `mAccount(<pk>)` lets the FK navigation resolve, and `mPinResets` is populated explicitly here.
 *
 * Coexistence phase: only data access is migrated. `MappedPhysicalCard` and `PinReset` stay in
 * `Boot.scala`'s `ToSchemify.models` for schema creation and test-isolation reset.
 *
 * Faithful-to-Mapper notes:
 * - Replacement/collected/posted dates are stored as SQL NULL (not "") when absent, because the
 *   `replacement`/`collected`/`posted` accessors key off the date column being null to return None
 *   (a non-null reason string would otherwise hit `CardReplacementReason.valueOf("")`).
 * - create-response pin resets reuse the original request `Date` objects in-memory (matching the Mapper's
 *   `v.mPinResets += pin` behaviour); get-path pin resets are read back from the `pinreset` table.
 * - `getPhysicalCardsForBank` translates exactly the OBP query params the Mapper handled
 *   (`OBPCustomerId`, `OBPAccountId`) plus the always-on bank filter — nothing more.
 */
object DoobiePhysicalCardProvider extends PhysicalCardProvider {

  // One row of mappedphysicalcard. Nullable TIMESTAMP columns are Option[Timestamp] so absent
  // dates round-trip as SQL NULL (mirrors Lift MappedDateTime.get returning null).
  private case class CardRow(
    id: Long,
    mbankid: Option[String],
    missuenumber: Option[String],
    mcardid: Option[String],
    mnameoncard: Option[String],
    mserialnumber: Option[String],
    mvalidfrom: Option[Timestamp],
    mexpires: Option[Timestamp],
    menabled: Option[Boolean],
    mcancelled: Option[Boolean],
    monhotlist: Option[Boolean],
    mtechnology: Option[String],
    mnetworks: Option[String],
    mallows: Option[String],
    maccount: Option[Long],
    mreplacementdate: Option[Timestamp],
    mreplacementreason: Option[String],
    mcollected: Option[Timestamp],
    mposted: Option[Timestamp],
    mcardtype: Option[String],
    mbrand: Option[String],
    mcvv: Option[String],
    mbankcardnumber: Option[String],
    mcustomerid: Option[String]
  )

  private case class PinResetRow(
    mreplacementdate: Option[Timestamp],
    mreplacementreason: Option[String]
  )

  private def nn(s: String): String = if (s == null) "" else s

  private def toTimestamp(d: Date): Option[Timestamp] =
    if (d == null) None else Some(new Timestamp(d.getTime))

  private def toDate(o: Option[Timestamp]): Date = o.map(t => new Date(t.getTime)).orNull

  private val selectCols: Fragment =
    fr"""SELECT id, mbankid, missuenumber, mcardid, mnameoncard, mserialnumber, mvalidfrom, mexpires,
                menabled, mcancelled, monhotlist, mtechnology, mnetworks, mallows, maccount,
                mreplacementdate, mreplacementreason, mcollected, mposted, mcardtype, mbrand, mcvv,
                mbankcardnumber, mcustomerid
         FROM mappedphysicalcard"""

  private def findRow(where: Fragment): Option[CardRow] =
    DoobieUtil.runQuery((selectCols ++ where ++ fr"LIMIT 1").query[CardRow].option)

  private def runList(where: Fragment): List[CardRow] =
    DoobieUtil.runQuery((selectCols ++ where).query[CardRow].to[List])

  // Look up the MappedBankAccount primary key for a (bankId, accountId) pair. Mirrors the Mapper's
  // MappedBankAccount.find(...).id.get. Returns None when the account does not exist.
  private def findAccountPk(bankId: String, accountId: String): Option[Long] =
    DoobieUtil.runQuery(
      fr"SELECT id FROM mappedbankaccount WHERE bank = ${nn(bankId)} AND theaccountid = ${nn(accountId)} LIMIT 1"
        .query[Long].option)

  // Read this card's pin resets from the pinreset table, ordered by id ascending to match the Mapper's
  // mPinResets OrderBy(PinReset.id, Ascending).
  private def loadPinResets(cardPk: Long): List[PinResetRow] =
    DoobieUtil.runQuery(
      fr"SELECT mreplacementdate, mreplacementreason FROM pinreset WHERE card = $cardPk ORDER BY id ASC"
        .query[PinResetRow].to[List])

  // Build the in-memory MappedPhysicalCard holder from a DB row. Sets the account FK so the `account`
  // accessor resolves, and populates mPinResets from the supplied PinReset infos.
  private def toEntity(row: CardRow, pinResets: List[PinResetRow]): MappedPhysicalCard = {
    val card = new MappedPhysicalCard
    card.mCardId(row.mcardid.getOrElse(""))
    card.mBankId(row.mbankid.getOrElse(""))
    card.mBankCardNumber(row.mbankcardnumber.getOrElse(""))
    card.mNameOnCard(row.mnameoncard.getOrElse(""))
    card.mIssueNumber(row.missuenumber.getOrElse(""))
    card.mSerialNumber(row.mserialnumber.getOrElse(""))
    card.mValidFrom(toDate(row.mvalidfrom))
    card.mExpires(toDate(row.mexpires))
    card.mEnabled(row.menabled.getOrElse(false))
    card.mCancelled(row.mcancelled.getOrElse(false))
    card.mOnHotList(row.monhotlist.getOrElse(false))
    card.mTechnology(row.mtechnology.getOrElse(""))
    card.mNetworks(row.mnetworks.getOrElse(""))
    card.mAllows(row.mallows.getOrElse(""))
    // Replacement/collected/posted: keep null when absent so the accessors return None.
    card.mReplacementDate(toDate(row.mreplacementdate))
    card.mReplacementReason(row.mreplacementreason.orNull)
    card.mCollected(toDate(row.mcollected))
    card.mPosted(toDate(row.mposted))
    card.mCardType(row.mcardtype.getOrElse(""))
    card.mCustomerId(row.mcustomerid.getOrElse(""))
    card.mBrand(row.mbrand.getOrElse(""))
    card.mCVV(row.mcvv.getOrElse(""))
    row.maccount.foreach(pk => card.mAccount(pk)) // MappedLongForeignKey -> account accessor resolves via .obj
    pinResets.foreach { pr =>
      val pin = new PinReset
      pin.mReplacementDate(toDate(pr.mreplacementdate))
      pin.mReplacementReason(pr.mreplacementreason.orNull)
      card.mPinResets += pin
    }
    card
  }

  // Persist pin resets exactly as the Mapper does: if a pinreset row with the same mReplacementDate
  // already exists (any card), update its reason; otherwise insert a new row linked to this card.
  // Returns the request infos so the create/update response can mirror the in-memory holder.
  private def persistPinResets(cardPk: Long, pinResets: List[PinResetInfo]): List[PinResetRow] = {
    pinResets.foreach { pinReset =>
      val ts = toTimestamp(pinReset.requestedDate)
      val reason = pinReset.reasonRequested.toString
      val existing = DoobieUtil.runQuery(
        (fr"SELECT id FROM pinreset WHERE" ++
          ts.map(t => fr"mreplacementdate = $t").getOrElse(fr"mreplacementdate IS NULL") ++
          fr"LIMIT 1").query[Long].option)
      existing match {
        case Some(_) =>
          DoobieUtil.runQuery(
            (fr"UPDATE pinreset SET mreplacementreason = ${nn(reason)} WHERE" ++
              ts.map(t => fr"mreplacementdate = $t").getOrElse(fr"mreplacementdate IS NULL"))
              .update.run)
        case None =>
          DoobieUtil.runQuery(
            sql"""INSERT INTO pinreset (card, mreplacementdate, mreplacementreason)
                  VALUES ($cardPk, $ts, ${nn(reason)})""".update.run)
      }
    }
    pinResets.map(pr => PinResetRow(toTimestamp(pr.requestedDate), Some(pr.reasonRequested.toString)))
  }

  override def createPhysicalCard(
    bankCardNumber: String,
    nameOnCard: String,
    cardType: String,
    issueNumber: String,
    serialNumber: String,
    validFrom: Date,
    expires: Date,
    enabled: Boolean,
    cancelled: Boolean,
    onHotList: Boolean,
    technology: String,
    networks: List[String],
    allows: List[String],
    accountId: String,
    bankId: String,
    replacement: Option[CardReplacementInfo],
    pinResets: List[PinResetInfo],
    collected: Option[CardCollectionInfo],
    posted: Option[CardPostedInfo],
    customerId: String,
    cvv: String,
    brand: String,
    callContext: Option[CallContext]
  ): Box[MappedPhysicalCard] = {

    val mappedBankAccountPrimaryKey: Long = findAccountPk(bankId, accountId)
      .getOrElse(throw new Exception(s"$accountId do not have Primary key, please contact admin, check the database! "))

    val (requestedDate, reasonRequested): (Date, String) = replacement match {
      case Some(c) => (c.requestedDate, c.reasonRequested.toString)
      case _       => (null, null)
    }
    val collectedDate: Date = collected match {
      case Some(c) => c.date
      case _       => null
    }
    val postedDate: Date = posted match {
      case Some(c) => c.date
      case _       => null
    }

    val alreadyExists = findRow(
      fr"WHERE mbankid = ${nn(bankId)} AND mserialnumber = ${nn(serialNumber)} AND mbankcardnumber = ${nn(bankCardNumber)}")

    val result: Box[CardRow] = alreadyExists match {
      case Some(_) =>
        Failure(s"${ErrorMessages.CardAlreadyExists} Current BankId($bankId), bankCardNumber($bankCardNumber) and serialNumber($serialNumber)")
      case None =>
        tryo {
          val cardId = APIUtil.generateUUID()
          DoobieUtil.runQuery(
            sql"""INSERT INTO mappedphysicalcard
                    (mcardid, mbankid, mbankcardnumber, mcardtype, missuenumber, mnameoncard, mserialnumber,
                     mvalidfrom, mexpires, menabled, mcancelled, monhotlist, mtechnology, mnetworks, mallows,
                     mreplacementdate, mreplacementreason, mcollected, mposted, maccount, mcustomerid, mbrand, mcvv)
                  VALUES (${nn(cardId)}, ${nn(bankId)}, ${nn(bankCardNumber)}, ${nn(cardType)}, ${nn(issueNumber)},
                          ${nn(nameOnCard)}, ${nn(serialNumber)}, ${toTimestamp(validFrom)}, ${toTimestamp(expires)},
                          $enabled, $cancelled, $onHotList, ${nn(technology)}, ${nn(networks.mkString(","))}, ${nn(allows.mkString(","))},
                          ${toTimestamp(requestedDate)}, ${nn(reasonRequested)}, ${toTimestamp(collectedDate)},
                          ${toTimestamp(postedDate)}, $mappedBankAccountPrimaryKey, ${nn(customerId)}, ${nn(brand)},
                          ${nn(HashUtil.Sha256Hash(cvv))})""".update.run)
          findRow(fr"WHERE mbankid = ${nn(bankId)} AND mserialnumber = ${nn(serialNumber)} AND mbankcardnumber = ${nn(bankCardNumber)}")
            .getOrElse(throw new Exception(ErrorMessages.CreateCardError))
        } ?~! ErrorMessages.CreateCardError
    }

    result.map { row =>
      val pinResetRows = persistPinResets(row.id, pinResets)
      toEntity(row, pinResetRows)
    }
  }

  override def updatePhysicalCard(
    cardId: String,
    bankCardNumber: String,
    nameOnCard: String,
    cardType: String,
    issueNumber: String,
    serialNumber: String,
    validFrom: Date,
    expires: Date,
    enabled: Boolean,
    cancelled: Boolean,
    onHotList: Boolean,
    technology: String,
    networks: List[String],
    allows: List[String],
    accountId: String,
    bankId: String,
    replacement: Option[CardReplacementInfo],
    pinResets: List[PinResetInfo],
    collected: Option[CardCollectionInfo],
    posted: Option[CardPostedInfo],
    customerId: String,
    callContext: Option[CallContext]
  ): Box[PhysicalCardTrait] = {

    val mappedBankAccountPrimaryKey: Long = findAccountPk(bankId, accountId)
      .getOrElse(throw new Exception(s"$accountId do not have Primary key, please contact admin, check the database! "))

    // Mirror the Mapper: None collapses to CardReplacementInfo(null, null) and the reason is .toString'd
    // (which would NPE on the None path, exactly as the Mapper does — the v3.1.0 endpoint always supplies Some).
    val (requestedDate, reasonRequested): (Date, String) = replacement match {
      case Some(c) => (c.requestedDate, c.reasonRequested.toString)
      case _       => (null, null.asInstanceOf[CardReplacementReason].toString)
    }
    val collectedDate: Date = collected match {
      case Some(c) => c.date
      case _       => null
    }
    val postedDate: Date = posted match {
      case Some(c) => c.date
      case _       => null
    }

    val existing = findRow(fr"WHERE mbankid = ${nn(bankId)} AND mcardid = ${nn(cardId)}")

    val result: Box[CardRow] = existing match {
      case Some(_) =>
        tryo {
          DoobieUtil.runQuery(
            sql"""UPDATE mappedphysicalcard SET
                    mcardid = ${nn(cardId)}, mbankid = ${nn(bankId)}, mbankcardnumber = ${nn(bankCardNumber)},
                    mcardtype = ${nn(cardType)}, missuenumber = ${nn(issueNumber)}, mnameoncard = ${nn(nameOnCard)},
                    mserialnumber = ${nn(serialNumber)}, mvalidfrom = ${toTimestamp(validFrom)},
                    mexpires = ${toTimestamp(expires)}, menabled = $enabled, mcancelled = $cancelled,
                    monhotlist = $onHotList, mtechnology = ${nn(technology)}, mnetworks = ${nn(networks.mkString(","))},
                    mallows = ${nn(allows.mkString(","))}, mreplacementdate = ${toTimestamp(requestedDate)},
                    mreplacementreason = ${nn(reasonRequested)}, mcollected = ${toTimestamp(collectedDate)},
                    mposted = ${toTimestamp(postedDate)}, maccount = $mappedBankAccountPrimaryKey,
                    mcustomerid = ${nn(customerId)}
                  WHERE mbankid = ${nn(bankId)} AND mcardid = ${nn(cardId)}""".update.run)
          findRow(fr"WHERE mbankid = ${nn(bankId)} AND mcardid = ${nn(cardId)}")
            .getOrElse(throw new Exception(ErrorMessages.UpdateCardError))
        } ?~! ErrorMessages.UpdateCardError
      case None =>
        Failure(s"${ErrorMessages.CardNotFound} Current BankId($bankId) and CardId($cardId) ")
    }

    result.map { row =>
      val pinResetRows = persistPinResets(row.id, pinResets)
      toEntity(row, pinResetRows)
    }
  }

  override def getPhysicalCards(user: User): List[MappedPhysicalCard] = {
    val accounts = views.vend.getPrivateBankAccounts(user)
    val allCards = runList(Fragment.empty).map(row => toEntity(row, loadPinResets(row.id)))
    for {
      account <- accounts
      card <- allCards if account.accountId.value == card.account.accountId.value
    } yield card
  }

  override def getPhysicalCardByCardNumber(bankCardNumber: String, callContext: Option[CallContext]): Box[PhysicalCardTrait] =
    findRow(fr"WHERE mbankcardnumber = ${nn(bankCardNumber)}") match {
      case Some(row) => Full(toEntity(row, loadPinResets(row.id)))
      case None      => Empty
    }

  override def getPhysicalCardsForBank(bank: Bank, user: User, queryParams: List[OBPQueryParam]): List[PhysicalCardTrait] = {
    val customerIdFrag: Option[Fragment] = queryParams.collectFirst {
      case OBPCustomerId(value) => fr"mcustomerid = ${nn(value)}"
    }
    val accountIdFrag: Option[Fragment] = queryParams.collectFirst {
      case OBPAccountId(value) =>
        // Mirror the Mapper: unknown account resolves to Long.MaxValue so no rows match.
        val pk = findAccountPk(bank.bankId.value, value).getOrElse(Long.MaxValue)
        fr"maccount = $pk"
    }
    val whereFrags = fr"mbankid = ${nn(bank.bankId.value)}" :: List(customerIdFrag, accountIdFrag).flatten
    val whereClause = fr"WHERE" ++ whereFrags.reduceLeft((a, b) => a ++ fr"AND" ++ b)
    runList(whereClause).map(row => toEntity(row, loadPinResets(row.id)))
  }

  override def getPhysicalCardForBank(bankId: BankId, cardId: String, callContext: Option[CallContext]): Box[PhysicalCardTrait] =
    findRow(fr"WHERE mbankid = ${nn(bankId.value)} AND mcardid = ${nn(cardId)}") match {
      case Some(row) => Full(toEntity(row, loadPinResets(row.id)))
      case None      => Empty
    }

  override def deletePhysicalCardForBank(bankId: BankId, cardId: String, callContext: Option[CallContext]): Box[Boolean] =
    findRow(fr"WHERE mbankid = ${nn(bankId.value)} AND mcardid = ${nn(cardId)}").map { _ =>
      DoobieUtil.runQuery(
        sql"DELETE FROM mappedphysicalcard WHERE mbankid = ${nn(bankId.value)} AND mcardid = ${nn(cardId)}".update.run) > 0
    } match {
      case Some(b) => Full(b)
      case None    => Empty
    }

}
