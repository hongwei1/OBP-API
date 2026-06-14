package code.transactionChallenge

import code.api.util.APIUtil.{allowedAnswerTransactionRequestChallengeAttempts, transactionRequestChallengeTtl}
import code.api.util.ErrorMessages.InvalidChallengeAnswer
import code.api.util.{APIUtil, DoobieUtil, ErrorMessages}
import com.openbankproject.commons.model.ChallengeTrait
import com.openbankproject.commons.model.enums.StrongCustomerAuthentication.SCA
import com.openbankproject.commons.model.enums.StrongCustomerAuthenticationStatus.SCAStatus
import com.openbankproject.commons.model.enums.{StrongCustomerAuthentication, StrongCustomerAuthenticationStatus}
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Failure, Full}
import net.liftweb.util.Helpers
import net.liftweb.util.Helpers.tryo
import org.mindrot.jbcrypt.BCrypt

import java.sql.Timestamp
import scala.collection.immutable.List
import scala.compat.Platform

object DoobieChallengeProvider extends ChallengeProvider {

  // Table name is the custom dbTableName "ExpectedChallengeAnswer" (case-insensitive in H2).
  // The boolean column is "successful_c" — Lift escapes the reserved word SUCCESSFUL with a "_c" suffix.
  private case class ChallengeRow(
    challengeid: Option[String],
    challengetype: Option[String],
    transactionrequestid: Option[String],
    expectedanswer: Option[String],
    expecteduserid: Option[String],
    salt: Option[String],
    successful: Option[Boolean],
    scamethod: Option[String],
    scastatus: Option[String],
    consentid: Option[String],
    basketid: Option[String],
    authenticationmethodid: Option[String],
    attemptcounter: Option[Int],
    challengepurpose: Option[String],
    challengecontexthash: Option[String],
    challengecontextstructure: Option[String],
    createdat: Option[Timestamp]
  )

  // Mirrors the MappedExpectedChallengeAnswer accessors exactly (including the withName-throws-on-empty behaviour).
  private case class DoobieChallenge(row: ChallengeRow) extends ChallengeTrait {
    override def challengeId: String = row.challengeid.getOrElse("")
    override def challengeType: String = row.challengetype.getOrElse("")
    override def transactionRequestId: String = row.transactionrequestid.getOrElse("")
    override def expectedAnswer: String = row.expectedanswer.getOrElse("")
    override def expectedUserId: String = row.expecteduserid.getOrElse("")
    override def salt: String = row.salt.getOrElse("")
    override def successful: Boolean = row.successful.getOrElse(false)
    override def consentId: Option[String] = Option(row.consentid.getOrElse(""))
    override def basketId: Option[String] = Option(row.basketid.getOrElse(""))
    override def scaMethod: Option[SCA] = Option(StrongCustomerAuthentication.withName(row.scamethod.getOrElse("")))
    override def scaStatus: Option[SCAStatus] = Option(StrongCustomerAuthenticationStatus.withName(row.scastatus.getOrElse("")))
    override def authenticationMethodId: Option[String] = Option(row.authenticationmethodid.getOrElse(""))
    override def attemptCounter: Int = row.attemptcounter.getOrElse(0)
    override def challengePurpose: Option[String] = Option(row.challengepurpose.getOrElse("")).filter(_.nonEmpty)
    override def challengeContextHash: Option[String] = Option(row.challengecontexthash.getOrElse("")).filter(_.nonEmpty)
    override def challengeContextStructure: Option[String] = Option(row.challengecontextstructure.getOrElse("")).filter(_.nonEmpty)
  }

  private def nn(s: String): String = if (s == null) "" else s

  private val selectCols: Fragment =
    fr"""SELECT challengeid, challengetype, transactionrequestid, expectedanswer, expecteduserid, salt,
                successful_c, scamethod, scastatus, consentid, basketid, authenticationmethodid,
                attemptcounter, challengepurpose, challengecontexthash, challengecontextstructure, createdat
         FROM expectedchallengeanswer"""

  override def saveChallenge(
    challengeId: String,
    transactionRequestId: String, // Note: consentId and transactionRequestId and basketId are exclusive here.
    salt: String,
    expectedAnswer: String,
    expectedUserId: String,
    scaMethod: Option[SCA],
    scaStatus: Option[SCAStatus],
    consentId: Option[String], // Note: consentId and transactionRequestId and basketId are exclusive here.
    basketId: Option[String], // Note: consentId and transactionRequestId and basketId are exclusive here.
    authenticationMethodId: Option[String],
    challengeType: String,
    // PSD2 Dynamic Linking fields
    challengePurpose: Option[String] = None,
    challengeContextHash: Option[String] = None,
    challengeContextStructure: Option[String] = None
  ): Box[ChallengeTrait] =
    tryo {
      val scaMethodStr = nn(scaMethod.map(_.toString).getOrElse(""))
      val scaStatusStr = nn(scaStatus.map(_.toString).getOrElse(""))
      val consentIdStr = nn(consentId.getOrElse(""))
      val basketIdStr = nn(basketId.getOrElse(""))
      // Mirrors the Mapper: AuthenticationMethodId is stored as expectedUserId (preserve the original behaviour).
      val authMethodIdStr = nn(expectedUserId)
      val purposeStr = nn(challengePurpose.getOrElse(""))
      val contextHashStr = nn(challengeContextHash.getOrElse(""))
      val contextStructureStr = nn(challengeContextStructure.getOrElse(""))
      val now = new Timestamp(System.currentTimeMillis())
      DoobieUtil.runQuery(
        sql"""INSERT INTO expectedchallengeanswer
                (challengeid, challengetype, transactionrequestid, salt, expectedanswer, expecteduserid,
                 scamethod, scastatus, consentid, basketid, authenticationmethodid,
                 challengepurpose, challengecontexthash, challengecontextstructure,
                 successful_c, attemptcounter, createdat, updatedat)
              VALUES (${nn(challengeId)}, ${nn(challengeType)}, ${nn(transactionRequestId)}, ${nn(salt)},
                      ${nn(expectedAnswer)}, ${nn(expectedUserId)}, $scaMethodStr, $scaStatusStr,
                      $consentIdStr, $basketIdStr, $authMethodIdStr,
                      $purposeStr, $contextHashStr, $contextStructureStr,
                      false, 0, $now, $now)""".update.run)
      DoobieChallenge(ChallengeRow(
        Some(nn(challengeId)), Some(nn(challengeType)), Some(nn(transactionRequestId)),
        Some(nn(expectedAnswer)), Some(nn(expectedUserId)), Some(nn(salt)),
        Some(false), Some(scaMethodStr), Some(scaStatusStr), Some(consentIdStr), Some(basketIdStr),
        Some(authMethodIdStr), Some(0), Some(purposeStr), Some(contextHashStr), Some(contextStructureStr),
        Some(now)))
    }

  private def findRowByChallengeId(challengeId: String): Box[ChallengeRow] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE challengeid = ${nn(challengeId)} LIMIT 1").query[ChallengeRow].option) match {
      case Some(r) => Full(r)
      case None    => Empty
    }

  override def getChallenge(challengeId: String): Box[ChallengeTrait] =
    findRowByChallengeId(challengeId).map(DoobieChallenge(_))

  override def getChallengesByTransactionRequestId(transactionRequestId: String): Box[List[ChallengeTrait]] =
    Full(
      DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE transactionrequestid = ${nn(transactionRequestId)}")
          .query[ChallengeRow].to[List]).map(DoobieChallenge(_)))

  override def getChallengesByConsentId(consentId: String): Box[List[ChallengeTrait]] =
    Full(
      DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE consentid = ${nn(consentId)}")
          .query[ChallengeRow].to[List]).map(DoobieChallenge(_)))

  override def getChallengesByBasketId(basketId: String): Box[List[ChallengeTrait]] =
    Full(
      DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE basketid = ${nn(basketId)}")
          .query[ChallengeRow].to[List]).map(DoobieChallenge(_)))

  override def validateChallenge(
    challengeId: String,
    challengeAnswer: String,
    userId: Option[String]
  ): Box[ChallengeTrait] = {
    val maxAttempts = APIUtil.allowedAnswerTransactionRequestChallengeAttempts
    for {
      row <- findRowByChallengeId(challengeId) ?~! s"${ErrorMessages.InvalidTransactionRequestChallengeId}"
      currentAttemptCounterValue = row.attemptcounter.getOrElse(0)
      // Atomically increment the counter only when still under the limit.
      // Using DB-side `attemptcounter + 1` prevents stale-read under-counting under
      // concurrent requests (two threads both reading N would both write N+1 if a
      // client-side value were used). The WHERE guard means the UPDATE returns 0 rows
      // when the limit is already reached, so concurrent excess attempts are rejected
      // without incrementing the counter further.
      updatedRows = DoobieUtil.runQuery(
        sql"""UPDATE expectedchallengeanswer
              SET attemptcounter = attemptcounter + 1, updatedat = ${new Timestamp(System.currentTimeMillis())}
              WHERE challengeid = ${nn(challengeId)} AND attemptcounter < $maxAttempts""".update.run)
      _ <- if (updatedRows > 0) Full(())
           else Failure(s"${ErrorMessages.AllowedAttemptsUsedUp}")
      createDateTime = row.createdat.getOrElse(new Timestamp(0L))
      challengeTTL: Long = Helpers.seconds(APIUtil.transactionRequestChallengeTtl)
      expiredDateTime: Long = createDateTime.getTime + challengeTTL
      currentTime: Long = Platform.currentTime
      challenge <- if (expiredDateTime > currentTime) {
        val currentHashedAnswer = BCrypt.hashpw(challengeAnswer, row.salt.getOrElse("")).substring(0, 44)
        val expectedHashedAnswer = row.expectedanswer.getOrElse("")
        userId match {
          case None =>
            if (currentHashedAnswer == expectedHashedAnswer) {
              tryo { markSuccessful(challengeId); successfulChallenge(row, currentAttemptCounterValue + 1) }
            } else {
              Failure(s"${
                s"${
                  InvalidChallengeAnswer
                    .replace("answer may be expired.", s"answer may be expired (${transactionRequestChallengeTtl} seconds).")
                    .replace("up your allowed attempts.", s"up your allowed attempts (${allowedAnswerTransactionRequestChallengeAttempts} times).")
                }"}")
            }
          case Some(id) =>
            if (currentHashedAnswer == expectedHashedAnswer && id == row.expecteduserid.getOrElse("")) {
              tryo { markSuccessful(challengeId); successfulChallenge(row, currentAttemptCounterValue + 1) }
            } else {
              Failure(s"${
                s"${
                  InvalidChallengeAnswer
                    .replace("answer may be expired.", s"answer may be expired (${transactionRequestChallengeTtl} seconds).")
                    .replace("up your allowed attempts.", s"up your allowed attempts (${allowedAnswerTransactionRequestChallengeAttempts} times).")
                }"}")
            }
        }
      } else {
        Failure(s"${ErrorMessages.OneTimePasswordExpired} Current expiration time is $transactionRequestChallengeTtl seconds")
      }
    } yield {
      challenge
    }
  }

  private def markSuccessful(challengeId: String): Unit =
    DoobieUtil.runQuery(
      sql"""UPDATE expectedchallengeanswer
            SET successful_c = true, scastatus = ${StrongCustomerAuthenticationStatus.finalised.toString},
                updatedat = ${new Timestamp(System.currentTimeMillis())}
            WHERE challengeid = ${nn(challengeId)}""".update.run)

  // Reproduces the in-memory state of the saved Mapper entity after a successful validation:
  // attemptCounter incremented, successful=true, scaStatus=finalised.
  private def successfulChallenge(row: ChallengeRow, newAttemptCounter: Int): ChallengeTrait =
    DoobieChallenge(row.copy(
      successful = Some(true),
      scastatus = Some(StrongCustomerAuthenticationStatus.finalised.toString),
      attemptcounter = Some(newAttemptCounter)))
}
