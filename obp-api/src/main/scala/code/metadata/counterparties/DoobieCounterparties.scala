package code.metadata.counterparties

import code.api.cache.Caching
import code.api.util.{APIUtil, DoobieUtil}
import code.users.Users
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model._
import com.tesobe.CacheKeyFromArguments
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo
import net.liftweb.util.StringHelpers

import java.sql.Timestamp
import java.util.{Date, UUID}
import scala.concurrent.duration._

/**
 * Doobie implementation of the [[Counterparties]] vend (Phase 2 of the Lift Mapper -> Doobie
 * migration). It owns all four counterparty tables directly via SQL — `mappedcounterparty`,
 * `mappedcounterpartymetadata`, `mappedcounterpartywheretag` and `mappedcounterpartybespoke` —
 * so no data access goes through the Lift Mapper layer.
 *
 * During this coexistence phase only data access is migrated: the Mapper entities stay registered
 * in `Boot.scala`'s `ToSchemify.models` so Schemifier still owns the schema (Flyway skips tables it
 * already created) and the test-isolation reset still clears them. The `MapperCounterparties` /
 * `MapperCounterpartyBespoke` classes are otherwise dead code (plus a dependency of the historical
 * `MigrationOfMappedCounterpartyDescriptionLength` script) and are removed at the final teardown.
 *
 * Reads/writes go through `DoobieUtil.runQuery`, which shares the http4s request-scoped connection
 * when one is present (so writes join the request transaction) and falls back to an autocommit
 * pool connection otherwise — identical to the Phase 1 entities.
 *
 * Null handling: Lift's MappedString silently tolerated null values (transaction-derived
 * counterparties carry null bank/account ids and names, and JSON fields can be absent), whereas
 * Doobie's Put[String] rejects null. Every externally-supplied String is therefore funnelled
 * through [[nn]] before it reaches a SQL parameter, preserving the historical behaviour.
 */
object DoobieCounterparties extends Counterparties with MdcLoggable {

  val MetadataTTL = 0 // getSecondsCache("getOrCreateMetadata")

  // Lift MappedString tolerated null; Doobie Put[String] does not. Normalise null -> "".
  private def nn(s: String): String = if (s == null) "" else s

  private def now: Timestamp = new Timestamp(System.currentTimeMillis())

  // ---------------------------------------------------------------------------
  // Row models (mirror the database columns; Lift kept some quirky names: MVAULE, USER_C, DATE_C)
  // ---------------------------------------------------------------------------

  private case class CounterpartyRow(
    id: Long,
    counterpartyId: String,
    thisBankId: String,
    thisAccountId: String,
    thisViewId: String,
    name: String,
    description: String,
    createdByUserId: String,
    otherBankRoutingScheme: String,
    otherBankRoutingAddress: String,
    otherBranchRoutingScheme: String,
    otherBranchRoutingAddress: String,
    otherAccountRoutingScheme: String,
    otherAccountRoutingAddress: String,
    otherAccountSecondaryRoutingScheme: String,
    otherAccountSecondaryRoutingAddress: String,
    isBeneficiary: Boolean,
    currency: String
  )

  private case class MetadataRow(
    id: Long,
    counterpartyId: String,
    counterpartyName: String,
    thisBankId: String,
    thisAccountId: String,
    publicAlias: String,
    privateAlias: String,
    moreInfo: String,
    url: String,
    imageUrl: String,
    openCorporatesUrl: String,
    physicalLocation: Option[Long],
    corporateLocation: Option[Long]
  )

  private case class WhereTagRow(
    id: Long,
    user: Long,
    date: Option[Timestamp],
    geoLatitude: Double,
    geoLongitude: Double
  )

  // ---------------------------------------------------------------------------
  // Trait implementations backed by the rows above
  // ---------------------------------------------------------------------------

  private case class DoobieGeoTag(row: WhereTagRow) extends GeoTag {
    override def datePosted: Date = row.date.map(t => new Date(t.getTime)).getOrElse(new Date(0L))
    override def postedBy: Box[User] = Users.users.vend.getUserByResourceUserId(row.user)
    override def latitude: Double = row.geoLatitude
    override def longitude: Double = row.geoLongitude
  }

  private case class DoobieCounterparty(row: CounterpartyRow) extends CounterpartyTrait {
    override def createdByUserId: String = row.createdByUserId
    override def name: String = row.name
    override def description: String = row.description
    override def thisBankId: String = row.thisBankId
    override def thisAccountId: String = row.thisAccountId
    override def thisViewId: String = row.thisViewId
    override def counterpartyId: String = row.counterpartyId
    override def otherAccountRoutingScheme: String = row.otherAccountRoutingScheme
    override def otherAccountRoutingAddress: String = row.otherAccountRoutingAddress
    override def otherAccountSecondaryRoutingScheme: String = row.otherAccountSecondaryRoutingScheme
    override def otherAccountSecondaryRoutingAddress: String = row.otherAccountSecondaryRoutingAddress
    override def otherBankRoutingScheme: String = row.otherBankRoutingScheme
    override def otherBankRoutingAddress: String = row.otherBankRoutingAddress
    override def otherBranchRoutingScheme: String = row.otherBranchRoutingScheme
    override def otherBranchRoutingAddress: String = row.otherBranchRoutingAddress
    override def isBeneficiary: Boolean = row.isBeneficiary
    override def currency: String = row.currency
    override def bespoke: List[CounterpartyBespoke] = findBespokesByCounterpartyPk(row.id)
  }

  // The CounterpartyMetadata trait exposes mutating function-values. No external caller uses them
  // (callers go through the Counterparties vend methods below), but they must be satisfied — they
  // delegate to the impl helpers keyed by counterpartyId.
  private case class DoobieCounterpartyMetadata(row: MetadataRow) extends CounterpartyMetadata {
    private val cpId = row.counterpartyId
    override def getCounterpartyId: String = row.counterpartyId
    override def getCounterpartyName: String = row.counterpartyName
    override def getPublicAlias: String = row.publicAlias
    override def getPrivateAlias: String = row.privateAlias
    override def getMoreInfo: String = row.moreInfo
    override def getUrl: String = row.url
    override def getImageURL: String = row.imageUrl
    override def getOpenCorporatesURL: String = row.openCorporatesUrl
    override def getCorporateLocation: Option[GeoTag] = row.corporateLocation.flatMap(findWhereTag)
    override def getPhysicalLocation: Option[GeoTag] = row.physicalLocation.flatMap(findWhereTag)

    override val addMoreInfo: String => Boolean = (x) => addMoreInfoImpl(cpId, x)
    override val addURL: String => Boolean = (x) => addUrlImpl(cpId, x)
    override val addImageURL: String => Boolean = (x) => addImageUrlImpl(cpId, x)
    override val addOpenCorporatesURL: String => Boolean = (x) => addOpenCorporatesUrlImpl(cpId, x)
    override val addPublicAlias: String => Boolean = (x) => addPublicAliasImpl(cpId, x)
    override val addPrivateAlias: String => Boolean = (x) => addPrivateAliasImpl(cpId, x)
    override val addCorporateLocation: (UserPrimaryKey, Date, Double, Double) => Boolean =
      (userId, date, long, lat) => setLocation(cpId, corporate = true, userId, date, long, lat)
    override val addPhysicalLocation: (UserPrimaryKey, Date, Double, Double) => Boolean =
      (userId, date, long, lat) => setLocation(cpId, corporate = false, userId, date, long, lat)
    override val deleteCorporateLocation: () => Boolean = () => deleteLocationImpl(cpId, corporate = true)
    override val deletePhysicalLocation: () => Boolean = () => deleteLocationImpl(cpId, corporate = false)
  }

  // ---------------------------------------------------------------------------
  // SELECT fragments
  // ---------------------------------------------------------------------------

  private val counterpartyCols: Fragment =
    fr"""SELECT id, mcounterpartyid, mthisbankid, mthisaccountid, mthisviewid, mname, mdescription,
         mcreatedbyuserid, motherbankroutingscheme, motherbankroutingaddress, motherbranchroutingscheme,
         motherbranchroutingaddress, motheraccountroutingscheme, motheraccountroutingaddress,
         motheraccountsecondaryroutingscheme, motheraccountsecondaryroutingaddress, misbeneficiary, mcurrency
         FROM mappedcounterparty"""

  private val metadataCols: Fragment =
    fr"""SELECT id, counterpartyid, counterpartyname, thisbankid, thisaccountid, publicalias, privatealias,
         moreinfo, url, imageurl, opencorporatesurl, physicallocation, corporatelocation
         FROM mappedcounterpartymetadata"""

  private val whereTagCols: Fragment =
    fr"SELECT id, user_c, date_c, geolatitude, geolongitude FROM mappedcounterpartywheretag"

  // ---------------------------------------------------------------------------
  // Low-level finders
  // ---------------------------------------------------------------------------

  private def findMetadataByCounterpartyId(counterpartyId: String): Option[MetadataRow] =
    DoobieUtil.runQuery(
      (metadataCols ++ fr"WHERE counterpartyid = ${nn(counterpartyId)} LIMIT 1").query[MetadataRow].option)

  private def findWhereTag(id: Long): Option[GeoTag] =
    DoobieUtil.runQuery((whereTagCols ++ fr"WHERE id = $id LIMIT 1").query[WhereTagRow].option)
      .map(DoobieGeoTag(_))

  private def findBespokesByCounterpartyPk(pk: Long): List[CounterpartyBespoke] =
    DoobieUtil.runQuery(
      sql"SELECT mkey, mvaule FROM mappedcounterpartybespoke WHERE mcounterparty = $pk"
        .query[(String, String)].to[List])
      .map { case (k, v) => CounterpartyBespoke(nn(k), nn(v)) }

  // ---------------------------------------------------------------------------
  // CounterpartyMetadata
  // ---------------------------------------------------------------------------

  override def getOrCreateMetadata(bankId: BankId, accountId: AccountId, counterpartyId0: String, counterpartyName0: String): Box[CounterpartyMetadata] = {
    val counterpartyId = nn(counterpartyId0)
    val counterpartyName = nn(counterpartyName0)
    var cacheKey = (UUID.randomUUID().toString, UUID.randomUUID().toString, UUID.randomUUID().toString)
    CacheKeyFromArguments.buildCacheKey {
      Caching.memoizeSyncWithProvider(Some(cacheKey.toString()))(MetadataTTL.second) {

        // A public alias unique among the metadatas of this OBP account.
        def newPublicAliasName(): String = {
          val firstAliasAttempt = "ALIAS_" + UUID.randomUUID.toString.toUpperCase.take(6)
          val existingAliases = DoobieUtil.runQuery(
            sql"""SELECT publicalias FROM mappedcounterpartymetadata
                  WHERE thisbankid = ${nn(bankId.value)} AND thisaccountid = ${nn(accountId.value)}"""
              .query[String].to[List]).toSet
          def isDuplicate(alias: String) = existingAliases.contains(alias)
          def appendUntilUnique(alias: String): String = {
            val next = alias + UUID.randomUUID.toString.toUpperCase.take(1)
            if (isDuplicate(next)) appendUntilUnique(next) else next
          }
          if (isDuplicate(firstAliasAttempt)) appendUntilUnique(firstAliasAttempt) else firstAliasAttempt
        }

        findMetadataByCounterpartyId(counterpartyId) match {
          case Some(row) =>
            logger.debug(s"getOrCreateMetadata--Get DoobieCounterpartyMetadata counterpartyId($counterpartyId)")
            Full(DoobieCounterpartyMetadata(row))
          case None =>
            logger.debug(s"getOrCreateMetadata--Create DoobieCounterpartyMetadata counterpartyId($counterpartyId)")
            val publicAlias = newPublicAliasName()
            tryo {
              DoobieUtil.runQuery(
                sql"""INSERT INTO mappedcounterpartymetadata
                      (counterpartyid, thisbankid, thisaccountid, counterpartyname, publicalias,
                       privatealias, moreinfo, url, imageurl, opencorporatesurl, createdat, updatedat)
                      VALUES (${counterpartyId}, ${nn(bankId.value)}, ${nn(accountId.value)}, ${counterpartyName},
                       ${publicAlias}, '', '', '', '', '', ${now}, ${now})""".update.run)
            }
            findMetadataByCounterpartyId(counterpartyId) match {
              case Some(row) => Full(DoobieCounterpartyMetadata(row))
              case None => Empty
            }
        }
      }
    }
  }

  override def getMetadatas(originalPartyBankId: BankId, originalPartyAccountId: AccountId): List[CounterpartyMetadata] =
    DoobieUtil.runQuery(
      (metadataCols ++ fr"""WHERE thisbankid = ${nn(originalPartyBankId.value)}
              AND thisaccountid = ${nn(originalPartyAccountId.value)}""").query[MetadataRow].to[List])
      .map(DoobieCounterpartyMetadata(_))

  override def getMetadata(originalPartyBankId: BankId, originalPartyAccountId: AccountId, counterpartyMetadataId: String): Box[CounterpartyMetadata] =
    DoobieUtil.runQuery(
      (metadataCols ++ fr"""WHERE thisbankid = ${nn(originalPartyBankId.value)}
              AND thisaccountid = ${nn(originalPartyAccountId.value)}
              AND counterpartyid = ${nn(counterpartyMetadataId)} LIMIT 1""").query[MetadataRow].option) match {
      case Some(row) => Full(DoobieCounterpartyMetadata(row))
      case None => Empty
    }

  override def deleteMetadata(originalPartyBankId: BankId, originalPartyAccountId: AccountId, counterpartyMetadataId: String): Box[Boolean] =
    findMetadataByCounterpartyId(counterpartyMetadataId) match {
      case Some(row) if row.thisBankId == nn(originalPartyBankId.value) && row.thisAccountId == nn(originalPartyAccountId.value) =>
        Full(DoobieUtil.runQuery(
          sql"DELETE FROM mappedcounterpartymetadata WHERE id = ${row.id}".update.run) > 0)
      case _ => Empty
    }

  // ---------------------------------------------------------------------------
  // Counterparty
  // ---------------------------------------------------------------------------

  override def getCounterparty(counterpartyId: String): Box[CounterpartyTrait] =
    DoobieUtil.runQuery((counterpartyCols ++ fr"WHERE mcounterpartyid = ${nn(counterpartyId)} LIMIT 1")
      .query[CounterpartyRow].option) match {
      case Some(row) => Full(DoobieCounterparty(row))
      case None => Empty
    }

  override def deleteCounterparty(counterpartyId: String): Box[Boolean] =
    DoobieUtil.runQuery((counterpartyCols ++ fr"WHERE mcounterpartyid = ${nn(counterpartyId)} LIMIT 1")
      .query[CounterpartyRow].option) match {
      case Some(row) =>
        Full(DoobieUtil.runQuery(
          sql"DELETE FROM mappedcounterparty WHERE id = ${row.id}".update.run) > 0)
      case None => Empty
    }

  // No unique constraint on IBAN; return the latest record (highest id), mirroring the Lift behaviour.
  override def getCounterpartyByIban(iban: String): Box[CounterpartyTrait] =
    DoobieUtil.runQuery((counterpartyCols ++ fr"""WHERE motheraccountsecondaryroutingaddress = ${nn(iban)}
            ORDER BY id DESC LIMIT 1""").query[CounterpartyRow].option) match {
      case Some(row) => Full(DoobieCounterparty(row))
      case None => Empty
    }

  override def getCounterpartyByIbanAndBankAccountId(iban: String, bankId: BankId, accountId: AccountId): Box[CounterpartyTrait] =
    DoobieUtil.runQuery((counterpartyCols ++ fr"""WHERE motheraccountsecondaryroutingaddress = ${nn(iban)}
            AND mthisbankid = ${nn(bankId.value)} AND mthisaccountid = ${nn(accountId.value)} LIMIT 1""")
      .query[CounterpartyRow].option) match {
      case Some(row) => Full(DoobieCounterparty(row))
      case None => Empty
    }

  override def getCounterpartyByRoutings(
    otherBankRoutingScheme: String,
    otherBankRoutingAddress: String,
    otherBranchRoutingScheme: String,
    otherBranchRoutingAddress: String,
    otherAccountRoutingScheme: String,
    otherAccountRoutingAddress: String
  ): Box[CounterpartyTrait] =
    DoobieUtil.runQuery((counterpartyCols ++ fr"""WHERE motherbankroutingscheme = ${nn(otherBankRoutingScheme)}
            AND motherbankroutingaddress = ${nn(otherBankRoutingAddress)}
            AND motherbranchroutingscheme = ${nn(otherBranchRoutingScheme)}
            AND motherbranchroutingaddress = ${nn(otherBranchRoutingAddress)}
            AND motheraccountroutingscheme = ${nn(otherAccountRoutingScheme)}
            AND motheraccountroutingaddress = ${nn(otherAccountRoutingAddress)} LIMIT 1""")
      .query[CounterpartyRow].option) match {
      case Some(row) => Full(DoobieCounterparty(row))
      case None => Empty
    }

  override def getCounterpartyBySecondaryRouting(
    otherAccountSecondaryRoutingScheme: String,
    otherAccountSecondaryRoutingAddress: String
  ): Box[CounterpartyTrait] =
    DoobieUtil.runQuery((counterpartyCols ++ fr"""WHERE motheraccountsecondaryroutingscheme = ${nn(otherAccountSecondaryRoutingScheme)}
            AND motheraccountsecondaryroutingaddress = ${nn(otherAccountSecondaryRoutingAddress)} LIMIT 1""")
      .query[CounterpartyRow].option) match {
      case Some(row) => Full(DoobieCounterparty(row))
      case None => Empty
    }

  override def getCounterparties(thisBankId: BankId, thisAccountId: AccountId, viewId: ViewId): Box[List[CounterpartyTrait]] =
    Full(DoobieUtil.runQuery((counterpartyCols ++ fr"""WHERE mthisaccountid = ${nn(thisAccountId.value)}
            AND mthisbankid = ${nn(thisBankId.value)} AND mthisviewid = ${nn(viewId.value)}""")
      .query[CounterpartyRow].to[List]).map(DoobieCounterparty(_)))

  override def createCounterparty(
    createdByUserId: String,
    thisBankId: String,
    thisAccountId: String,
    thisViewId: String,
    name: String,
    otherAccountRoutingScheme: String,
    otherAccountRoutingAddress: String,
    otherBankRoutingScheme: String,
    otherBankRoutingAddress: String,
    otherBranchRoutingScheme: String,
    otherBranchRoutingAddress: String,
    isBeneficiary: Boolean,
    otherAccountSecondaryRoutingScheme: String,
    otherAccountSecondaryRoutingAddress: String,
    description: String,
    currency: String,
    bespoke: List[CounterpartyBespoke]
  ): Box[CounterpartyTrait] = {
    val counterpartyId = APIUtil.createExplicitCounterpartyId
    val accountRoutingScheme = StringHelpers.snakify(nn(otherAccountRoutingScheme)).toUpperCase
    val bankRoutingScheme = StringHelpers.snakify(nn(otherBankRoutingScheme)).toUpperCase
    val branchRoutingScheme = StringHelpers.snakify(nn(otherBranchRoutingScheme)).toUpperCase
    val nName = nn(name)
    val nCreatedBy = nn(createdByUserId)
    val nBankId = nn(thisBankId)
    val nAccountId = nn(thisAccountId)
    val nViewId = nn(thisViewId)
    val nAccountAddress = nn(otherAccountRoutingAddress)
    val nBankAddress = nn(otherBankRoutingAddress)
    val nBranchAddress = nn(otherBranchRoutingAddress)
    val nDescription = nn(description)
    val nCurrency = nn(currency)
    val nSecondaryScheme = nn(otherAccountSecondaryRoutingScheme)
    val nSecondaryAddress = nn(otherAccountSecondaryRoutingAddress)

    tryo {
      DoobieUtil.runQuery(
        sql"""INSERT INTO mappedcounterparty
              (mcounterpartyid, mname, mcreatedbyuserid, mthisbankid, mthisaccountid, mthisviewid,
               motheraccountroutingscheme, motheraccountroutingaddress, motherbankroutingscheme,
               motherbankroutingaddress, motherbranchroutingaddress, motherbranchroutingscheme,
               misbeneficiary, mdescription, mcurrency, motheraccountsecondaryroutingscheme,
               motheraccountsecondaryroutingaddress, createdat, updatedat)
              VALUES ($counterpartyId, $nName, $nCreatedBy, $nBankId, $nAccountId, $nViewId,
               $accountRoutingScheme, $nAccountAddress, $bankRoutingScheme,
               $nBankAddress, $nBranchAddress, $branchRoutingScheme,
               $isBeneficiary, $nDescription, $nCurrency, $nSecondaryScheme,
               $nSecondaryAddress, $now, $now)""".update.run)

      // mcounterpartyid is unique, so re-read the generated primary key by it, then attach bespokes.
      // When inside an http4s request these queries share its connection/transaction via DoobieUtil.
      DoobieUtil.runQuery(
        sql"SELECT id FROM mappedcounterparty WHERE mcounterpartyid = $counterpartyId LIMIT 1"
          .query[Long].option).foreach { pk =>
        bespoke.foreach { b =>
          DoobieUtil.runQuery(
            sql"""INSERT INTO mappedcounterpartybespoke (mcounterparty, mkey, mvaule)
                  VALUES ($pk, ${nn(b.key)}, ${nn(b.value)})""".update.run)
        }
      }
    }.flatMap(_ => getCounterparty(counterpartyId))
  }

  override def checkCounterpartyExists(name: String, thisBankId: String, thisAccountId: String, thisViewId: String): Box[CounterpartyTrait] =
    DoobieUtil.runQuery((counterpartyCols ++ fr"""WHERE mname = ${nn(name)} AND mthisbankid = ${nn(thisBankId)}
            AND mthisaccountid = ${nn(thisAccountId)} AND mthisviewid = ${nn(thisViewId)} LIMIT 1""")
      .query[CounterpartyRow].option) match {
      case Some(row) => Full(DoobieCounterparty(row))
      case None => Empty
    }

  // ---------------------------------------------------------------------------
  // Metadata aliases / urls / locations (vend methods keyed by counterpartyId)
  // ---------------------------------------------------------------------------

  override def getPublicAlias(counterpartyId: String): Box[String] = metaField(counterpartyId)(_.publicAlias)
  override def getPrivateAlias(counterpartyId: String): Box[String] = metaField(counterpartyId)(_.privateAlias)
  override def getOpenCorporatesURL(counterpartyId: String): Box[String] = metaField(counterpartyId)(_.openCorporatesUrl)
  override def getImageURL(counterpartyId: String): Box[String] = metaField(counterpartyId)(_.imageUrl)
  override def getUrl(counterpartyId: String): Box[String] = metaField(counterpartyId)(_.url)
  override def getMoreInfo(counterpartyId: String): Box[String] = metaField(counterpartyId)(_.moreInfo)

  override def getPhysicalLocation(counterpartyId: String): Box[GeoTag] =
    findMetadataByCounterpartyId(counterpartyId).flatMap(_.physicalLocation).flatMap(findWhereTag) match {
      case Some(g) => Full(g); case None => Empty
    }

  override def getCorporateLocation(counterpartyId: String): Box[GeoTag] =
    findMetadataByCounterpartyId(counterpartyId).flatMap(_.corporateLocation).flatMap(findWhereTag) match {
      case Some(g) => Full(g); case None => Empty
    }

  override def addPublicAlias(counterpartyId: String, alias: String): Box[Boolean] = Full(addPublicAliasImpl(counterpartyId, alias))
  override def addPrivateAlias(counterpartyId: String, alias: String): Box[Boolean] = Full(addPrivateAliasImpl(counterpartyId, alias))
  override def addURL(counterpartyId: String, url: String): Box[Boolean] = Full(addUrlImpl(counterpartyId, url))
  override def addImageURL(counterpartyId: String, imageUrl: String): Box[Boolean] = Full(addImageUrlImpl(counterpartyId, imageUrl))
  override def addOpenCorporatesURL(counterpartyId: String, openCorporatesUrl: String): Box[Boolean] = Full(addOpenCorporatesUrlImpl(counterpartyId, openCorporatesUrl))
  override def addMoreInfo(counterpartyId: String, moreInfo: String): Box[Boolean] = Full(addMoreInfoImpl(counterpartyId, moreInfo))

  override def addPhysicalLocation(counterpartyId: String, userId: UserPrimaryKey, datePosted: Date, longitude: Double, latitude: Double): Box[Boolean] =
    Full(setLocation(counterpartyId, corporate = false, userId, datePosted, longitude, latitude))
  override def addCorporateLocation(counterpartyId: String, userId: UserPrimaryKey, datePosted: Date, longitude: Double, latitude: Double): Box[Boolean] =
    Full(setLocation(counterpartyId, corporate = true, userId, datePosted, longitude, latitude))

  override def deletePhysicalLocation(counterpartyId: String): Box[Boolean] =
    Full(deleteLocationImpl(counterpartyId, corporate = false))
  override def deleteCorporateLocation(counterpartyId: String): Box[Boolean] =
    Full(deleteLocationImpl(counterpartyId, corporate = true))

  override def bulkDeleteAllCounterparties(): Box[Boolean] =
    Full {
      DoobieUtil.runQuery(sql"DELETE FROM mappedcounterparty".update.run)
      true
    }

  // ---------------------------------------------------------------------------
  // Private helpers
  // ---------------------------------------------------------------------------

  private def metaField(counterpartyId: String)(f: MetadataRow => String): Box[String] =
    findMetadataByCounterpartyId(counterpartyId) match {
      case Some(row) => Full(f(row)); case None => Empty
    }

  private def updateMetaColumn(counterpartyId: String, set: Fragment): Boolean =
    DoobieUtil.runQuery(
      (fr"UPDATE mappedcounterpartymetadata SET" ++ set ++ fr"WHERE counterpartyid = ${nn(counterpartyId)}")
        .update.run) > 0

  private def addPublicAliasImpl(cpId: String, x: String): Boolean = updateMetaColumn(cpId, fr"publicalias = ${nn(x)}")
  private def addPrivateAliasImpl(cpId: String, x: String): Boolean = updateMetaColumn(cpId, fr"privatealias = ${nn(x)}")
  private def addUrlImpl(cpId: String, x: String): Boolean = updateMetaColumn(cpId, fr"url = ${nn(x)}")
  private def addImageUrlImpl(cpId: String, x: String): Boolean = updateMetaColumn(cpId, fr"imageurl = ${nn(x)}")
  private def addOpenCorporatesUrlImpl(cpId: String, x: String): Boolean = updateMetaColumn(cpId, fr"opencorporatesurl = ${nn(x)}")
  private def addMoreInfoImpl(cpId: String, x: String): Boolean = updateMetaColumn(cpId, fr"moreinfo = ${nn(x)}")

  // Create-or-update the where-tag for a counterparty's physical/corporate location, mirroring
  // MapperCounterpartyMetadata.setWhere: reuse the existing where-tag row if the FK is already set,
  // otherwise insert a new one and point the metadata FK column at it.
  private def setLocation(counterpartyId: String, corporate: Boolean, userId: UserPrimaryKey, datePosted: Date, longitude: Double, latitude: Double): Boolean =
    findMetadataByCounterpartyId(counterpartyId) match {
      case None => false
      case Some(meta) =>
        val existing = if (corporate) meta.corporateLocation else meta.physicalLocation
        val ts = new Timestamp(datePosted.getTime)
        existing match {
          case Some(whereId) =>
            DoobieUtil.runQuery(
              sql"""UPDATE mappedcounterpartywheretag
                    SET user_c = ${userId.value}, date_c = $ts, geolongitude = $longitude, geolatitude = $latitude
                    WHERE id = $whereId""".update.run) > 0
          case None =>
            val newId = DoobieUtil.runQuery(
              sql"""INSERT INTO mappedcounterpartywheretag (user_c, date_c, geolatitude, geolongitude, createdat, updatedat)
                    VALUES (${userId.value}, $ts, $latitude, $longitude, $now, $now)"""
                .update.withUniqueGeneratedKeys[Long]("id"))
            val column = if (corporate) fr"corporatelocation = $newId" else fr"physicallocation = $newId"
            updateMetaColumn(counterpartyId, column)
        }
    }

  private def deleteLocationImpl(counterpartyId: String, corporate: Boolean): Boolean =
    findMetadataByCounterpartyId(counterpartyId) match {
      case None => false
      case Some(meta) =>
        val existing = if (corporate) meta.corporateLocation else meta.physicalLocation
        existing match {
          case None => false
          case Some(whereId) =>
            DoobieUtil.runQuery(
              sql"DELETE FROM mappedcounterpartywheretag WHERE id = $whereId".update.run) > 0
        }
    }
}
