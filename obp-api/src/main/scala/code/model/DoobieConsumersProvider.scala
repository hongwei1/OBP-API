package code.model

import code.api.util.{APIUtil, CallContext, DoobieUtil, OBPAscending, OBPAzp, OBPConsumerId, OBPDescending, OBPFromDate, OBPIss, OBPLimit, OBPOffset, OBPOrdering, OBPQueryParam, OBPToDate}
import code.consumer.ConsumersProvider
import code.model.AppType.{Confidential, Public, Unknown}
import code.util.Helper.MdcLoggable
import code.util.HydraUtil._
import com.openbankproject.commons.ExecutionContext.Implicits.global
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Failure, Full, ParamFailure}
import net.liftweb.util.Helpers
import net.liftweb.util.Helpers.tryo
import org.apache.commons.lang3.StringUtils

import java.sql.Timestamp
import java.util.Date
import scala.concurrent.Future

object DoobieConsumersProvider extends ConsumersProvider with MdcLoggable {

  private case class ConsumerRow(
    id: Long,
    consumerId: Option[String],
    key: Option[String],
    secret: Option[String],
    azp: Option[String],
    aud: Option[String],
    iss: Option[String],
    sub: Option[String],
    isActive: Option[Boolean],
    name: Option[String],
    appType: Option[String],
    description: Option[String],
    developerEmail: Option[String],
    redirectUrl: Option[String],
    logoUrl: Option[String],
    userAuthenticationUrl: Option[String],
    createdByUserId: Option[String],
    perSecondCallLimit: Option[Long],
    perMinuteCallLimit: Option[Long],
    perHourCallLimit: Option[Long],
    perDayCallLimit: Option[Long],
    perWeekCallLimit: Option[Long],
    perMonthCallLimit: Option[Long],
    clientCertificate: Option[String],
    company: Option[String],
    createdAt: Option[Timestamp],
    updatedAt: Option[Timestamp]
  )

  private val selectCols: Fragment =
    fr"""SELECT id, consumerid, key_c, secret, azp, aud, iss, sub, isactive,
                name, apptype, description, developeremail, redirecturl, logourl,
                userauthenticationurl, createdbyuserid,
                persecondcalllimit, perminutecalllimit, perhourcalllimit,
                perdaycalllimit, perweekcalllimit, permonthcalllimit,
                clientcertificate, company, createdat, updatedat
         FROM consumer"""

  private def toEntity(row: ConsumerRow): Consumer = {
    val c = Consumer.create
    c.id(row.id)
    row.consumerId.foreach(c.consumerId(_))
    row.key.foreach(c.key(_))
    row.secret.foreach(c.secret(_))
    row.azp.foreach(c.azp(_))
    row.aud.foreach(c.aud(_))
    row.iss.foreach(c.iss(_))
    row.sub.foreach(c.sub(_))
    row.isActive.foreach(c.isActive(_))
    row.name.foreach(c.name(_))
    row.appType.foreach(c.appType(_))
    row.description.foreach(c.description(_))
    row.developerEmail.foreach(c.developerEmail(_))
    row.redirectUrl.foreach(c.redirectURL(_))
    row.logoUrl.foreach(c.logoUrl(_))
    row.userAuthenticationUrl.foreach(c.userAuthenticationURL(_))
    row.createdByUserId.foreach(c.createdByUserId(_))
    row.perSecondCallLimit.foreach(c.perSecondCallLimit(_))
    row.perMinuteCallLimit.foreach(c.perMinuteCallLimit(_))
    row.perHourCallLimit.foreach(c.perHourCallLimit(_))
    row.perDayCallLimit.foreach(c.perDayCallLimit(_))
    row.perWeekCallLimit.foreach(c.perWeekCallLimit(_))
    row.perMonthCallLimit.foreach(c.perMonthCallLimit(_))
    row.clientCertificate.foreach(c.clientCertificate(_))
    row.company.foreach(c.company(_))
    row.createdAt.foreach(ts => c.createdAt(new Date(ts.getTime)))
    row.updatedAt.foreach(ts => c.updatedAt(new Date(ts.getTime)))
    c
  }

  private def findOne(where: Fragment): Box[Consumer] =
    DoobieUtil.runQuery((selectCols ++ where ++ fr"LIMIT 1").query[ConsumerRow].option) match {
      case Some(r) => Full(toEntity(r))
      case None    => Empty
    }

  private def findList(where: Fragment, ordering: Fragment, limitOffset: Fragment): List[Consumer] =
    DoobieUtil.runQuery((selectCols ++ where ++ ordering ++ limitOffset).query[ConsumerRow].to[List])
      .map(toEntity)

  override def getConsumerByPrimaryIdFuture(id: Long): Future[Box[Consumer]] =
    Future(getConsumerByPrimaryId(id))

  override def getConsumerByPrimaryId(id: Long): Box[Consumer] =
    findOne(fr"WHERE id = $id")

  override def getConsumerByConsumerKey(consumerKey: String): Box[Consumer] =
    findOne(fr"WHERE key_c = $consumerKey")

  override def getConsumerByConsumerKeyFuture(consumerKey: String): Future[Box[Consumer]] =
    Future(getConsumerByConsumerKey(consumerKey))

  override def getConsumerByPemCertificate(pem: String): Box[Consumer] =
    findOne(fr"WHERE clientcertificate = $pem")

  override def getConsumerByConsumerId(consumerId: String): Box[Consumer] =
    findOne(fr"WHERE consumerid = $consumerId")

  override def getConsumerByConsumerIdFuture(consumerId: String): Future[Box[Consumer]] =
    Future(getConsumerByConsumerId(consumerId))

  override def getConsumersByUserIdFuture(userId: String): Future[List[Consumer]] =
    Future(findList(fr"WHERE createdbyuserid = $userId", Fragment.empty, Fragment.empty))

  override def getConsumersFuture(httpParams: List[OBPQueryParam], callContext: Option[CallContext]): Future[List[Consumer]] =
    Future {
      val conditions = List(
        httpParams.collectFirst { case OBPFromDate(d)     => fr"createdat >= ${new Timestamp(d.getTime)}" },
        httpParams.collectFirst { case OBPToDate(d)       => fr"createdat <= ${new Timestamp(d.getTime)}" },
        httpParams.collectFirst { case OBPAzp(v)          => fr"azp = $v" },
        httpParams.collectFirst { case OBPIss(v)          => fr"iss = $v" },
        httpParams.collectFirst { case OBPConsumerId(v)   => fr"consumerid = $v" },
      ).flatten

      val whereClause = if (conditions.isEmpty) Fragment.empty
                        else fr"WHERE" ++ conditions.reduceLeft(_ ++ fr"AND" ++ _)

      val ordering = httpParams.collectFirst {
        case OBPOrdering(_, OBPAscending)  => fr"ORDER BY createdat ASC"
        case OBPOrdering(_, OBPDescending) => fr"ORDER BY createdat DESC"
      }.getOrElse(Fragment.empty)

      val limitFr  = httpParams.collectFirst { case OBPLimit(v)  => fr"LIMIT $v"  }.getOrElse(Fragment.empty)
      val offsetFr = httpParams.collectFirst { case OBPOffset(v) => fr"OFFSET $v" }.getOrElse(Fragment.empty)

      findList(whereClause, ordering, limitFr ++ offsetFr)
    }

  override def createConsumer(
    key: Option[String],
    secret: Option[String],
    isActive: Option[Boolean],
    name: Option[String],
    appType: Option[AppType],
    description: Option[String],
    developerEmail: Option[String],
    redirectURL: Option[String],
    createdByUserId: Option[String],
    clientCertificate: Option[String],
    company: Option[String],
    logoURL: Option[String]
  ): Box[Consumer] = tryo {
    // Bind nullable text columns as Option[String] — Doobie's Put[String] rejects a
    // raw null ("Expected non-nullable param"), so .orNull would blow up whenever the
    // caller passes None (e.g. appType = None in every test consumer).
    val actualName: Option[String] = name.map { n =>
      val count = DoobieUtil.runQuery(sql"SELECT COUNT(*) FROM consumer WHERE name = $n".query[Int].unique)
      if (count == 0) n else s"${n}_${Helpers.randomString(10).toLowerCase}"
    }
    val appTypeStr: Option[String] = appType.map(_.toString)
    val isActiveVal = isActive.getOrElse(APIUtil.getPropsAsBoolValue("consumers_enabled_by_default", false))
    val certVal: Option[String] = clientCertificate.filter(StringUtils.isNotBlank)
    val now         = new Timestamp(System.currentTimeMillis())

    val generatedId = DoobieUtil.runQuery(
      sql"""INSERT INTO consumer
              (consumerid, key_c, secret, isactive, name, apptype, description,
               developeremail, redirecturl, logourl, createdbyuserid, clientcertificate,
               company, createdat, updatedat)
            VALUES
              (${APIUtil.generateUUID()}, $key, $secret, $isActiveVal, $actualName,
               $appTypeStr, $description, $developerEmail, $redirectURL, $logoURL,
               $createdByUserId, $certVal, $company, $now, $now)"""
        .update.withUniqueGeneratedKeys[Long]("id")
    )
    findOne(fr"WHERE id = $generatedId").openOrThrowException("Consumer not found after insert")
  }

  override def deleteConsumer(consumer: Consumer): Boolean = {
    if (integrateWithHydra) hydraAdmin.deleteOAuth2Client(consumer.key.get)
    val count = DoobieUtil.runQuery(sql"DELETE FROM consumer WHERE id = ${consumer.id.get}".update.run)
    count > 0
  }

  override def updateConsumer(
    id: Long,
    key: Option[String],
    secret: Option[String],
    isActive: Option[Boolean],
    name: Option[String],
    appType: Option[AppType],
    description: Option[String],
    developerEmail: Option[String],
    redirectURL: Option[String],
    createdByUserId: Option[String],
    LogoURL: Option[String],
    certificate: Option[String]
  ): Box[Consumer] = {
    findOne(fr"WHERE id = $id") match {
      case Full(_) =>
        tryo {
          val setClauses = List(
            key.map(v              => fr"key_c = $v"),
            secret.map(v           => fr"secret = $v"),
            isActive.map(v         => fr"isactive = $v"),
            name.map(v             => fr"name = $v"),
            appType.map(v          => fr"apptype = ${v.toString}"),
            description.map(v      => fr"description = $v"),
            developerEmail.map(v   => fr"developeremail = $v"),
            redirectURL.map(v      => fr"redirecturl = $v"),
            LogoURL.map(v          => fr"logourl = $v"),
            createdByUserId.map(v  => fr"createdbyuserid = $v"),
            certificate.map(v      => fr"clientcertificate = $v"),
            Some(fr"updatedat = ${new Timestamp(System.currentTimeMillis())}"),
          ).flatten

          if (setClauses.nonEmpty) {
            val setFrag = setClauses.reduceLeft(_ ++ fr"," ++ _)
            DoobieUtil.runQuery((fr"UPDATE consumer SET" ++ setFrag ++ fr"WHERE id = $id").update.run)
          }

          if (integrateWithHydra && isActive.isDefined) {
            val updated = findOne(fr"WHERE id = $id").openOrThrowException("Consumer disappeared after update")
            val clientId = updated.key.get
            val existsClient = Box.tryo(hydraAdmin.getOAuth2Client(clientId)).filter(null.!=)
            if (isActive == Some(false) && existsClient.isDefined) {
              existsClient.map { c => c.setClientSecretExpiresAt(System.currentTimeMillis()); hydraAdmin.updateOAuth2Client(clientId, c) }
            }
            if (isActive == Some(true) && existsClient.isDefined) {
              existsClient.map { c => c.setClientSecretExpiresAt(0L); hydraAdmin.updateOAuth2Client(clientId, c) }
            }
          }

          findOne(fr"WHERE id = $id").openOrThrowException("Consumer not found after update")
        }
      case other => other
    }
  }

  @deprecated("Use RateLimitingDI.rateLimiting.vend methods instead", "v5.0.0")
  override def updateConsumerCallLimits(
    id: Long, perSecond: Option[String], perMinute: Option[String],
    perHour: Option[String], perDay: Option[String],
    perWeek: Option[String], perMonth: Option[String]
  ): Future[Box[Consumer]] = Future {
    val now = new Timestamp(System.currentTimeMillis())
    val setClauses = List(
      perSecond.map(v => fr"persecondcalllimit = ${v.toLong}"),
      perMinute.map(v => fr"perminutecalllimit = ${v.toLong}"),
      perHour.map(v   => fr"perhourcalllimit = ${v.toLong}"),
      perDay.map(v    => fr"perdaycalllimit = ${v.toLong}"),
      perWeek.map(v   => fr"perweekcalllimit = ${v.toLong}"),
      perMonth.map(v  => fr"permonthcalllimit = ${v.toLong}"),
      Some(fr"updatedat = $now"),
    ).flatten

    if (setClauses.nonEmpty) {
      val setFrag = setClauses.reduceLeft(_ ++ fr"," ++ _)
      DoobieUtil.runQuery((fr"UPDATE consumer SET" ++ setFrag ++ fr"WHERE id = $id").update.run)
    }
    findOne(fr"WHERE id = $id")
  }

  override def getOrCreateConsumer(
    consumerId: Option[String],
    key: Option[String],
    secret: Option[String],
    aud: Option[String],
    azp: Option[String],
    iss: Option[String],
    sub: Option[String],
    isActive: Option[Boolean],
    name: Option[String],
    appType: Option[AppType],
    description: Option[String],
    developerEmail: Option[String],
    redirectURL: Option[String],
    createdByUserId: Option[String],
    certificate: Option[String],
    logoUrl: Option[String]
  ): Box[Consumer] = {
    logger.info(s"getOrCreateConsumer says: BEGIN lookup. Input: consumerId=${consumerId.getOrElse("None")}, azp=${azp.getOrElse("None")}, iss=${iss.getOrElse("None")}, sub=${sub.getOrElse("None")}")

    // 1st try: find by consumerId
    val byConsumerId = findOne(fr"WHERE consumerid = ${consumerId.getOrElse("None")}")
    val consumer: Box[Consumer] = if (byConsumerId.isDefined) {
      val c = byConsumerId.openOrThrowException("checked isDefined")
      logger.info(s"getOrCreateConsumer says: MATCH on lookup 1 (by consumerId). Found consumer: consumerId=${c.consumerId.get}, key=${c.key.get}")
      byConsumerId
    } else {
      logger.info(s"getOrCreateConsumer says: MISS on lookup 1 (by consumerId=${consumerId.getOrElse("None")}). Trying lookup 2 (by Consumer.key matching azp)...")

      // 2nd try: find by consumer key matching azp
      val byKeyMatchingAzp = findOne(fr"WHERE key_c = ${azp.getOrElse("None")}")
      if (byKeyMatchingAzp.isDefined) {
        val c = byKeyMatchingAzp.openOrThrowException("checked isDefined")
        logger.info(s"getOrCreateConsumer says: MATCH on lookup 2 (by Consumer.key matching azp). Found pre-registered consumer: consumerId=${c.consumerId.get}, key=${c.key.get}")

        // Transitional cleanup: clear stale conflicting consumer's azp/sub
        val conflicting = findOne(fr"WHERE azp = ${azp.getOrElse("None")} AND iss = ${iss.getOrElse("None")}")
        for (stale <- conflicting if stale.id.get != c.id.get) {
          val newAzp = APIUtil.generateUUID()
          val newSub = APIUtil.generateUUID()
          val ts = new Timestamp(System.currentTimeMillis())
          logger.info(s"getOrCreateConsumer says: Found CONFLICTING auto-created consumer. Clearing azp/sub. Stale consumer id=${stale.id.get}")
          DoobieUtil.runQuery(
            sql"UPDATE consumer SET azp = $newAzp, sub = $newSub, updatedat = $ts WHERE id = ${stale.id.get}".update.run
          )
          logger.info(s"getOrCreateConsumer says: Cleared stale consumer.")
        }

        // Populate azp, iss, sub on the pre-registered consumer. Bind Option[String]
        // directly — .orNull would make Doobie's Put[String] reject a null when iss/sub is None.
        val ts = new Timestamp(System.currentTimeMillis())
        logger.info(s"getOrCreateConsumer says: Updating azp/iss/sub on pre-registered consumer...")
        DoobieUtil.runQuery(
          sql"UPDATE consumer SET azp = $azp, iss = $iss, sub = $sub, updatedat = $ts WHERE id = ${c.id.get}".update.run
        )
        logger.info(s"getOrCreateConsumer says: Updated pre-registered consumer.")
        findOne(fr"WHERE id = ${c.id.get}")
      } else {
        logger.info(s"getOrCreateConsumer says: MISS on lookup 2 (no consumer has key=${azp.getOrElse("None")}). Trying lookup 3 (by azp+iss pair)...")

        // 3rd try: find by (azp, iss) pair
        val byAzpIss = findOne(fr"WHERE azp = ${azp.getOrElse("None")} AND iss = ${iss.getOrElse("None")}")
        if (byAzpIss.isDefined) {
          val c = byAzpIss.openOrThrowException("checked isDefined")
          logger.info(s"getOrCreateConsumer says: MATCH on lookup 3 (by azp+iss). Found auto-created consumer: consumerId=${c.consumerId.get}, key=${c.key.get}")
          byAzpIss
        } else {
          logger.info(s"getOrCreateConsumer says: MISS on all 3 lookups. Will CREATE a new consumer. Searched: consumerId=${consumerId.getOrElse("None")}, key=${azp.getOrElse("None")}, (azp=${azp.getOrElse("None")}, iss=${iss.getOrElse("None")})")
          Empty
        }
      }
    }

    consumer match {
      case Full(c)               => Full(c)
      case Failure(msg, t, c)    => Failure(msg, t, c)
      case ParamFailure(x, y, z, q) => ParamFailure(x, y, z, q)
      case Empty =>
        tryo {
          val actualKey      = key.getOrElse(Helpers.randomString(40).toLowerCase)
          val actualSecret   = secret.getOrElse(Helpers.randomString(40).toLowerCase)
          val actualConsumerId = consumerId.getOrElse {
            azp match {
              case Some(value) if APIUtil.checkIfStringIsUUID(value) => value
              case Some(value) => s"${value}_${APIUtil.generateUUID()}"
              case None        => APIUtil.generateUUID()
            }
          }
          // Bind nullable text columns as Option[String] — Doobie's Put[String] rejects a
          // raw null, so .orNull would blow up whenever the caller passes None (common on
          // the OIDC auto-create path: aud/azp/iss/sub/description/etc. are frequently None).
          val actualName: Option[String] = name.map { n =>
            val count = DoobieUtil.runQuery(sql"SELECT COUNT(*) FROM consumer WHERE name = $n".query[Int].unique)
            if (count == 0) n else s"${n}_${Helpers.randomString(10).toLowerCase}"
          }
          val appTypeStr: Option[String] = appType.map(_.toString)
          val isActiveVal = isActive.getOrElse(APIUtil.getPropsAsBoolValue("consumers_enabled_by_default", false))
          val now         = new Timestamp(System.currentTimeMillis())

          val generatedId = DoobieUtil.runQuery(
            sql"""INSERT INTO consumer
                    (consumerid, key_c, secret, aud, azp, iss, sub, isactive, name, apptype,
                     description, developeremail, redirecturl, createdbyuserid, clientcertificate,
                     logourl, createdat, updatedat)
                  VALUES
                    ($actualConsumerId, $actualKey, $actualSecret, $aud, $azp, $iss, $sub,
                     $isActiveVal, $actualName, $appTypeStr, $description, $developerEmail, $redirectURL,
                     $createdByUserId, $certificate, $logoUrl, $now, $now)"""
              .update.withUniqueGeneratedKeys[Long]("id")
          )
          val createdConsumer = findOne(fr"WHERE id = $generatedId")
            .openOrThrowException("Consumer not found after insert")
          if (integrateWithHydra) createHydraClient(createdConsumer)
          createdConsumer
        }
    }
  }

  override def populateMissingUUIDs(): Boolean = {
    logger.warn("Executed script: DoobieConsumersProvider.populateMissingUUIDs")
    val ids = DoobieUtil.runQuery(
      sql"SELECT id FROM consumer WHERE consumerid IS NULL OR consumerid = ''".query[Long].to[List]
    )
    ids.map { id =>
      val uuid = APIUtil.generateUUID()
      val count = DoobieUtil.runQuery(sql"UPDATE consumer SET consumerid = $uuid WHERE id = $id".update.run)
      count > 0
    }.forall(_ == true)
  }
}
