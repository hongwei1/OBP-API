package code.consent

import code.api.util.{APIUtil, ConsentJWT, DoobieUtil, JwtUtil, OBPBankId, OBPConsentId, OBPConsumerId, OBPLimit, OBPOffset, OBPQueryParam, OBPSortBy, OBPStatus, OBPUserId, ProviderProviderId, SecureRandomUtil}
import code.consent.ConsentStatus.ConsentStatus
import code.model.Consumer
import code.model.dataAccess.ResourceUser
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.User
import com.openbankproject.commons.util.ApiStandards
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Failure, Full}
import net.liftweb.mapper.By
import net.liftweb.util.Helpers.tryo
import org.mindrot.jbcrypt.BCrypt

import java.sql.{Date => SqlDate, Timestamp}
import java.util.Date
import scala.collection.immutable.List

object DoobieConsentProvider extends ConsentProvider with MdcLoggable {

  private case class ConsentRow(
    consentId: String,
    consentReferenceId: String,
    userId: Option[String],
    secret: String,
    status: Option[String],
    challenge: Option[String],
    salt: Option[String],
    jsonWebToken: Option[String],
    consumerId: Option[String],
    consentRequestId: Option[String],
    apiStandard: Option[String],
    apiVersion: Option[String],
    recurringIndicator: Option[Boolean],
    validUntil: Option[SqlDate],
    frequencyPerDay: Option[Int],
    usesSoFarTodayCounter: Option[Int],
    usesSoFarTodayCounterUpdatedAt: Option[Timestamp],
    combinedServiceIndicator: Option[Boolean],
    lastActionDate: Option[SqlDate],
    expirationDateTime: Option[Timestamp],
    transactionFromDateTime: Option[Timestamp],
    transactionToDateTime: Option[Timestamp],
    statusUpdateDateTime: Option[Timestamp],
    note: Option[String],
    jsonWebTokenPayload: Option[String],
    jwtExpiresAt: Option[Timestamp],
    createdAt: Option[Timestamp]
  )

  private def nn(s: String): String = if (s == null) "" else s
  private def nnOpt(s: Option[String]): String = s.getOrElse("")

  private def toEntity(row: ConsentRow): MappedConsent =
    MappedConsent.create
      .mConsentId(row.consentId)
      .mConsentReferenceId(row.consentReferenceId)
      .mUserId(nnOpt(row.userId))
      .mSecret(row.secret)
      .mStatus(nnOpt(row.status))
      .mChallenge(nnOpt(row.challenge))
      .mSalt(nnOpt(row.salt))
      .mJsonWebToken(nnOpt(row.jsonWebToken))
      .mConsumerId(row.consumerId.getOrElse(null))
      .mConsentRequestId(row.consentRequestId.getOrElse(null))
      .mApiStandard(nnOpt(row.apiStandard))
      .mApiVersion(nnOpt(row.apiVersion))
      .mRecurringIndicator(row.recurringIndicator.getOrElse(false))
      .mValidUntil(row.validUntil.orNull)
      .mFrequencyPerDay(row.frequencyPerDay.getOrElse(0))
      .mUsesSoFarTodayCounter(row.usesSoFarTodayCounter.getOrElse(0))
      .mUsesSoFarTodayCounterUpdatedAt(row.usesSoFarTodayCounterUpdatedAt.orNull)
      .mCombinedServiceIndicator(row.combinedServiceIndicator.getOrElse(false))
      .mLastActionDate(row.lastActionDate.orNull)
      .mExpirationDateTime(row.expirationDateTime.orNull)
      .mTransactionFromDateTime(row.transactionFromDateTime.orNull)
      .mTransactionToDateTime(row.transactionToDateTime.orNull)
      .mStatusUpdateDateTime(row.statusUpdateDateTime.orNull)
      .mNote(nnOpt(row.note))
      .mJsonWebTokenPayload(nnOpt(row.jsonWebTokenPayload))
      .mJwtExpiresAt(row.jwtExpiresAt.orNull)
      .createdAt(row.createdAt.orNull)

  private val selectCols: Fragment =
    fr"""SELECT mconsentid, consent_reference_id, muserid, msecret, mstatus, mchallenge, msalt,
                mjsonwebtoken, mconsumerid, mconsentrequestid, mapistandard, mapiversion,
                mrecurringindicator, mvaliduntil, mfrequencyperday, musessofartodaycounter,
                musessofartodaycounterupdatedat, mcombinedserviceindicator, mlastactiondate,
                mexpirationdatetime, mtransactionfromdatetime, mtransactiontodatetime,
                mstatusupdatedatetime, mnote, mjsonwebtokenpayload, jwt_expires_at, createdat
         FROM mappedconsent"""

  private def findOne(where: Fragment): Box[MappedConsent] =
    DoobieUtil.runQuery((selectCols ++ where ++ fr"LIMIT 1").query[ConsentRow].option) match {
      case Some(r) => Full(toEntity(r))
      case None    => Empty
    }

  private def findList(where: Fragment): List[MappedConsent] =
    DoobieUtil.runQuery((selectCols ++ where).query[ConsentRow].to[List]).map(toEntity)

  override def getConsentByConsentId(consentId: String): Box[MappedConsent] =
    findOne(fr"WHERE mconsentid = ${nn(consentId)}")

  override def getConsentByConsentRequestId(consentRequestId: String): Box[MappedConsent] =
    findOne(fr"WHERE mconsentrequestid = ${nn(consentRequestId)}")

  override def getConsentsByUser(userId: String): List[MappedConsent] =
    findList(fr"WHERE muserid = ${nn(userId)}")

  override def getConsents(queryParams: List[OBPQueryParam]): (List[MappedConsent], Long) = {
    val limit     = queryParams.collectFirst { case OBPLimit(v)  => v }.getOrElse(50)
    val offset    = queryParams.collectFirst { case OBPOffset(v) => v }.getOrElse(0)

    val sortFr: Fragment = queryParams.collectFirst { case OBPSortBy(v) => v }
      .flatMap { spec =>
        val parts = spec.split(":").map(_.trim.toLowerCase)
        val dir = if (parts.lift(1).contains("desc")) "DESC" else "ASC"
        parts(0) match {
          case "created_date" => Some(Fragment.const(s"ORDER BY createdat $dir"))
          case "status"       => Some(Fragment.const(s"ORDER BY mstatus $dir"))
          case "consumer_id"  => Some(Fragment.const(s"ORDER BY mconsumerid $dir"))
          case _              => None
        }
      }.getOrElse(fr"")

    val consumerIdCond: Option[Fragment] = queryParams.collectFirst { case OBPConsumerId(v) => fr"mconsumerid = $v" }
    val consentIdCond:  Option[Fragment] = queryParams.collectFirst { case OBPConsentId(v)  => fr"mconsentid = $v" }

    val userIdCond: Option[Fragment] = queryParams.collectFirst { case OBPUserId(v) => fr"muserid = $v" }
      .orElse {
        queryParams.collectFirst {
          case ProviderProviderId(v) =>
            val parts = v.split("\\|") match { case Array(a, b) => (a, b); case _ => ("", "") }
            ResourceUser.findAll(By(ResourceUser.provider_, parts._1), By(ResourceUser.providerId, parts._2)) match {
              case x :: Nil => Some(fr"muserid = ${x.userId}")
              case _        => None
            }
        }.flatten
      }

    val statusCond: Option[Fragment] = queryParams.collectFirst {
      case OBPStatus(v) =>
        val statuses = v.split(",").toList.map(_.trim).distinct.flatMap(s => List(s.toLowerCase, s.toUpperCase)).distinct
        val inList   = statuses.map(s => fr"$s").reduce(_ ++ fr"," ++ _)
        fr"mstatus IN (" ++ inList ++ fr")"
    }

    val conditions = List(consumerIdCond, consentIdCond, userIdCond, statusCond).flatten
    val whereQ = if (conditions.isEmpty) fr"" else fr"WHERE" ++ conditions.reduce(_ ++ fr" AND " ++ _)

    val totalCount = DoobieUtil.runQuery(
      (fr"SELECT COUNT(*) FROM mappedconsent" ++ whereQ).query[Long].unique
    )
    val data = findList(whereQ ++ sortFr ++ fr"LIMIT $limit OFFSET $offset")

    val bankId: Option[String] = queryParams.collectFirst { case OBPBankId(v) => v }
    if (bankId.isDefined)
      (code.api.util.Consent.filterStrictlyByBank(data, bankId.get), totalCount)
    else
      (data, totalCount)
  }

  override def updateConsentStatus(consentId: String, status: ConsentStatus): Box[MappedConsent] = {
    findOne(fr"WHERE mconsentid = ${nn(consentId)}") match {
      case Full(consent) =>
        code.api.util.Consent.expireAllPreviousValidBerlinGroupConsents(consent, status)
        val now = new SqlDate(System.currentTimeMillis())
        tryo {
          DoobieUtil.runQuery(
            sql"""UPDATE mappedconsent
                  SET mstatus = ${status.toString}, mlastactiondate = $now
                  WHERE mconsentid = ${nn(consentId)}""".update.run
          )
          findOne(fr"WHERE mconsentid = ${nn(consentId)}").openOrThrowException("Consent disappeared after status update")
        }
      case Empty   => Empty ?~! code.api.util.ErrorMessages.ConsentNotFound
      case f: Failure => f
      case _       => Failure(code.api.util.ErrorMessages.UnknownError)
    }
  }

  override def updateConsentUser(consentId: String, user: User): Box[MappedConsent] = {
    val now = new SqlDate(System.currentTimeMillis())
    tryo {
      DoobieUtil.runQuery(
        sql"""UPDATE mappedconsent
              SET muserid = ${user.userId}, mlastactiondate = $now
              WHERE mconsentid = ${nn(consentId)}""".update.run
      )
      findOne(fr"WHERE mconsentid = ${nn(consentId)}").openOrThrowException("Consent disappeared after user update")
    }
  }

  override def createObpConsent(user: User, challengeAnswer: String, consentRequestId: Option[String], consumer: Option[Consumer] = None): Box[MappedConsent] = {
    tryo {
      val consentId        = APIUtil.generateUUID()
      val secret           = APIUtil.generateUUID()
      val consentRefId     = APIUtil.generateUUID()
      val salt             = BCrypt.gensalt()
      val challengeHashed  = BCrypt.hashpw(challengeAnswer, salt).substring(0, 44)
      val now              = new Timestamp(System.currentTimeMillis())
      val lastActionDate   = new SqlDate(System.currentTimeMillis())
      val userId           = user.userId
      val consumerId       = consumer.map(_.consumerId.get).getOrElse(null)
      val reqId            = consentRequestId.getOrElse(null)
      val apiStd           = ApiStandards.obp.toString

      DoobieUtil.runQuery(
        sql"""INSERT INTO mappedconsent
                (mconsentid, consent_reference_id, muserid, msecret, mstatus, mchallenge, msalt,
                 mconsumerid, mconsentrequestid, mapistandard,
                 mrecurringindicator, mfrequencyperday, musessofartodaycounter,
                 musessofartodaycounterupdatedat, mlastactiondate, createdat, updatedat)
              VALUES ($consentId, $consentRefId, $userId, $secret, ${ConsentStatus.INITIATED.toString},
                      $challengeHashed, $salt, $consumerId, $reqId, $apiStd,
                      true, 100, 0, $now, $lastActionDate, $now, $now)""".update.run
      )
      findOne(fr"WHERE mconsentid = $consentId").openOrThrowException("Consent not found after insert")
    }
  }

  override def createBerlinGroupConsent(
    user: Option[User],
    consumer: Option[Consumer],
    recurringIndicator: Boolean,
    validUntil: Date,
    frequencyPerDay: Int,
    combinedServiceIndicator: Boolean,
    apiStandard: Option[String],
    apiVersion: Option[String]
  ): Box[ConsentTrait] = tryo {
    val consentId        = APIUtil.generateUUID()
    val secret           = APIUtil.generateUUID()
    val consentRefId     = APIUtil.generateUUID()
    val challenge        = SecureRandomUtil.csprng.nextInt(99999999).toString
    val salt             = BCrypt.gensalt()
    val now              = new Timestamp(System.currentTimeMillis())
    val lastActionDate   = new SqlDate(System.currentTimeMillis())
    val validUntilSql    = new SqlDate(validUntil.getTime)
    val userId           = user.map(_.userId).getOrElse(null)
    val consumerId       = consumer.map(_.consumerId.get).getOrElse(null)
    val apiStd           = apiStandard.getOrElse(null)
    val apiVer           = apiVersion.getOrElse(null)

    DoobieUtil.runQuery(
      sql"""INSERT INTO mappedconsent
              (mconsentid, consent_reference_id, muserid, msecret, mstatus, mchallenge, msalt,
               mconsumerid, mapistandard, mapiversion,
               mrecurringindicator, mvaliduntil, mfrequencyperday, musessofartodaycounter,
               musessofartodaycounterupdatedat, mcombinedserviceindicator, mlastactiondate, createdat, updatedat)
            VALUES ($consentId, $consentRefId, $userId, $secret, ${ConsentStatus.received.toString},
                    $challenge, $salt, $consumerId, $apiStd, $apiVer,
                    $recurringIndicator, $validUntilSql, $frequencyPerDay, 0,
                    $now, $combinedServiceIndicator, $lastActionDate, $now, $now)""".update.run
    )
    findOne(fr"WHERE mconsentid = $consentId").openOrThrowException("Consent not found after BG insert")
  }

  override def updateBerlinGroupConsent(consentId: String, usesSoFarTodayCounter: Int): Box[ConsentTrait] = {
    val now = new Timestamp(System.currentTimeMillis())
    tryo {
      DoobieUtil.runQuery(
        sql"""UPDATE mappedconsent
              SET musessofartodaycounter = $usesSoFarTodayCounter,
                  musessofartodaycounterupdatedat = $now
              WHERE mconsentid = ${nn(consentId)}""".update.run
      )
      findOne(fr"WHERE mconsentid = ${nn(consentId)}").openOrThrowException("Consent disappeared after BG update")
    }
  }

  override def saveUKConsent(
    user: Option[User],
    bankId: Option[String],
    accountIds: Option[List[String]],
    consumerId: Option[String],
    permissions: List[String],
    expirationDateTime: Date,
    transactionFromDateTime: Date,
    transactionToDateTime: Date,
    apiStandard: Option[String],
    apiVersion: Option[String]
  ): Box[ConsentTrait] = tryo {
    val consentId       = APIUtil.generateUUID()
    val secret          = APIUtil.generateUUID()
    val consentRefId    = APIUtil.generateUUID()
    val challenge       = SecureRandomUtil.csprng.nextInt(99999999).toString
    val salt            = BCrypt.gensalt()
    val now             = new Timestamp(System.currentTimeMillis())
    val statusUpdateTs  = new Timestamp(System.currentTimeMillis())
    val expirationTs    = new Timestamp(expirationDateTime.getTime)
    val fromTs          = new Timestamp(transactionFromDateTime.getTime)
    val toTs            = new Timestamp(transactionToDateTime.getTime)
    val userId          = user.map(_.userId).getOrElse(null)
    val cId             = consumerId.getOrElse(null)
    val apiStd          = apiStandard.getOrElse(null)
    val apiVer          = apiVersion.getOrElse(null)

    DoobieUtil.runQuery(
      sql"""INSERT INTO mappedconsent
              (mconsentid, consent_reference_id, muserid, msecret, mstatus, mchallenge, msalt,
               mconsumerid, mapistandard, mapiversion,
               mexpirationdatetime, mtransactionfromdatetime, mtransactiontodatetime,
               mstatusupdatedatetime, createdat, updatedat)
            VALUES ($consentId, $consentRefId, $userId, $secret, ${ConsentStatus.AWAITINGAUTHORISATION.toString},
                    $challenge, $salt, $cId, $apiStd, $apiVer,
                    $expirationTs, $fromTs, $toTs, $statusUpdateTs, $now, $now)""".update.run
    )
    val insertedConsent = findOne(fr"WHERE mconsentid = $consentId")
      .openOrThrowException("Consent not found after UK insert")
    val jwt = code.api.util.Consent.createUKConsentJWT(
      user, bankId, accountIds, permissions,
      expirationDateTime, transactionFromDateTime, transactionToDateTime,
      secret = insertedConsent.secret,
      consentId = insertedConsent.consentId,
      consumerId
    )
    setJsonWebToken(insertedConsent.consentId, jwt)
      .openOrThrowException("Failed to set UK consent JWT")
  }

  override def setJsonWebToken(consentId: String, jwt: String): Box[MappedConsent] = {
    findOne(fr"WHERE mconsentid = ${nn(consentId)}") match {
      case Full(consent) =>
        val payload = code.api.util.JwtUtil.getSignedPayloadAsJson(jwt).openOr(null)
        val consentJWTParsed: Option[ConsentJWT] =
          if (payload != null) {
            try {
              import org.json4s._
              import com.openbankproject.commons.util.JsonAliases._
              implicit val formats: DefaultFormats.type = DefaultFormats
              Some(parse(payload).extract[ConsentJWT])
            } catch {
              case e: Exception =>
                logger.error(s"setJsonWebToken: failed to parse JWT for consent $consentId: ${e.getMessage}")
                None
            }
          } else None

        val jwtExpiresAt: Option[Timestamp] = consentJWTParsed.map(j => new Timestamp(j.exp * 1000L))

        tryo {
          DoobieUtil.runQuery(
            sql"""UPDATE mappedconsent
                  SET mjsonwebtoken = $jwt, mjsonwebtokenpayload = $payload, jwt_expires_at = $jwtExpiresAt
                  WHERE mconsentid = ${nn(consentId)}""".update.run
          )
          consentJWTParsed.foreach { jwtParsed =>
            try {
              DoobieConsentQueries.insertConsentItems(consent.consentReferenceId, jwtParsed)
            } catch {
              case e: Exception =>
                logger.error(s"setJsonWebToken: failed to populate consent_item for $consentId: ${e.getMessage}")
            }
          }
          findOne(fr"WHERE mconsentid = ${nn(consentId)}").openOrThrowException("Consent disappeared after JWT update")
        }
      case Empty   => Empty ?~! code.api.util.ErrorMessages.ConsentNotFound
      case f: Failure => f
      case _       => Failure(code.api.util.ErrorMessages.UnknownError)
    }
  }

  override def setValidUntil(consentId: String, validUntil: Date): Box[MappedConsent] = {
    val validUntilSql = new SqlDate(validUntil.getTime)
    tryo {
      DoobieUtil.runQuery(
        sql"""UPDATE mappedconsent
              SET mvaliduntil = $validUntilSql
              WHERE mconsentid = ${nn(consentId)}""".update.run
      )
      findOne(fr"WHERE mconsentid = ${nn(consentId)}").openOrThrowException("Consent disappeared after validUntil update")
    }
  }

  override def revoke(consentId: String): Box[MappedConsent] = {
    findOne(fr"WHERE mconsentid = ${nn(consentId)}") match {
      case Full(consent) if consent.status == ConsentStatus.REVOKED.toString =>
        Failure(code.api.util.ErrorMessages.ConsentAlreadyRevoked)
      case Full(_) =>
        val now = new SqlDate(System.currentTimeMillis())
        tryo {
          DoobieUtil.runQuery(
            sql"""UPDATE mappedconsent
                  SET mstatus = ${ConsentStatus.REVOKED.toString}, mlastactiondate = $now
                  WHERE mconsentid = ${nn(consentId)}""".update.run
          )
          findOne(fr"WHERE mconsentid = ${nn(consentId)}").openOrThrowException("Consent disappeared after revoke")
        }
      case Empty   => Empty ?~! code.api.util.ErrorMessages.ConsentNotFound
      case f: Failure => f
      case _       => Failure(code.api.util.ErrorMessages.UnknownError)
    }
  }

  override def revokeBerlinGroupConsent(consentId: String): Box[MappedConsent] = {
    findOne(fr"WHERE mconsentid = ${nn(consentId)}") match {
      case Full(consent) if consent.status == ConsentStatus.terminatedByTpp.toString =>
        Failure(code.api.util.ErrorMessages.ConsentAlreadyRevoked)
      case Full(_) =>
        val now = new SqlDate(System.currentTimeMillis())
        tryo {
          DoobieUtil.runQuery(
            sql"""UPDATE mappedconsent
                  SET mstatus = ${ConsentStatus.terminatedByTpp.toString}, mlastactiondate = $now
                  WHERE mconsentid = ${nn(consentId)}""".update.run
          )
          findOne(fr"WHERE mconsentid = ${nn(consentId)}").openOrThrowException("Consent disappeared after BG revoke")
        }
      case Empty   => Empty ?~! code.api.util.ErrorMessages.ConsentNotFound
      case f: Failure => f
      case _       => Failure(code.api.util.ErrorMessages.UnknownError)
    }
  }

  override def checkAnswer(consentId: String, challengeAnswer: String): Box[MappedConsent] = {
    def isAnswerCorrect(expectedAnswerHashed: String, answer: String, salt: String): Boolean = {
      val challengeAnswerHashed = BCrypt.hashpw(answer, salt).substring(0, 44)
      val scaEnabled = APIUtil.getPropsAsBoolValue("consents.sca.enabled", true)
      if (scaEnabled) expectedAnswerHashed == challengeAnswerHashed else true
    }
    findOne(fr"WHERE mconsentid = ${nn(consentId)}") match {
      case Full(consent) =>
        consent.status match {
          case value if value == ConsentStatus.INITIATED.toString =>
            val status =
              if (isAnswerCorrect(consent.challenge, challengeAnswer, consent.mSalt.get)) ConsentStatus.ACCEPTED.toString
              else ConsentStatus.REJECTED.toString
            val now = new SqlDate(System.currentTimeMillis())
            tryo {
              DoobieUtil.runQuery(
                sql"""UPDATE mappedconsent
                      SET mstatus = $status, mlastactiondate = $now
                      WHERE mconsentid = ${nn(consentId)}""".update.run
              )
              findOne(fr"WHERE mconsentid = ${nn(consentId)}").openOrThrowException("Consent disappeared after answer check")
            }
          case _ =>
            Full(consent)
        }
      case Empty   => Empty ?~! code.api.util.ErrorMessages.ConsentNotFound
      case f: Failure => f
      case _       => Failure(code.api.util.ErrorMessages.UnknownError)
    }
  }
}
