package code.ratelimiting

import code.api.Constant._
import code.api.cache.Caching
import code.api.util.{APIUtil, DoobieUtil}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full, Logger}
import net.liftweb.util.Helpers.tryo

import java.sql.Timestamp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Date
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.language.postfixOps

object DoobieRateLimitingProvider extends RateLimitingProviderTrait with Logger {

  // Table "ratelimiting". The trait returns the concrete RateLimiting Lift class, reused here purely as an
  // in-memory return holder (no second RateLimitingTrait impl → no equality regression).
  // CRITICAL: bankid/apiname/apiversion are stored as SQL NULL when absent (Lift `.orNull`), and the lookups
  // use `IS NULL` (Lift `NullRef`) vs `= ?` (Lift `By`). Reproduced exactly below.
  private case class RlRow(
    ratelimitingid: Option[String],
    consumerid: Option[String],
    bankid: Option[String],
    apiname: Option[String],
    apiversion: Option[String],
    persecond: Option[Long],
    perminute: Option[Long],
    perhour: Option[Long],
    perday: Option[Long],
    perweek: Option[Long],
    permonth: Option[Long],
    fromdate: Option[Timestamp],
    todate: Option[Timestamp]
  )

  private def nn(s: String): String = if (s == null) "" else s

  // MappedLong defaults come from props; reproduced for perX columns left unset on INSERT.
  private def perDef(prop: String): Long = APIUtil.getPropsAsLongValue(prop, -1)
  private val PS = "rate_limiting_per_second"
  private val PM = "rate_limiting_per_minute"
  private val PH = "rate_limiting_per_hour"
  private val PD = "rate_limiting_per_day"
  private val PW = "rate_limiting_per_week"
  private val PMo = "rate_limiting_per_month"

  private def toEntity(row: RlRow): RateLimiting =
    RateLimiting.create
      .RateLimitingId(row.ratelimitingid.getOrElse(""))
      .ConsumerId(row.consumerid.getOrElse(""))
      .BankId(row.bankid.getOrElse(""))      // null/empty → accessor returns None (matches Lift)
      .ApiName(row.apiname.getOrElse(""))
      .ApiVersion(row.apiversion.getOrElse(""))
      .PerSecondCallLimit(row.persecond.getOrElse(perDef(PS)))
      .PerMinuteCallLimit(row.perminute.getOrElse(perDef(PM)))
      .PerHourCallLimit(row.perhour.getOrElse(perDef(PH)))
      .PerDayCallLimit(row.perday.getOrElse(perDef(PD)))
      .PerWeekCallLimit(row.perweek.getOrElse(perDef(PW)))
      .PerMonthCallLimit(row.permonth.getOrElse(perDef(PMo)))
      .FromDate(row.fromdate.orNull)
      .ToDate(row.todate.orNull)

  private val selectCols: Fragment =
    fr"""SELECT ratelimitingid, consumerid, bankid, apiname, apiversion,
                persecondcalllimit, perminutecalllimit, perhourcalllimit, perdaycalllimit, perweekcalllimit, permonthcalllimit,
                fromdate, todate
         FROM ratelimiting"""

  // Lift `By(col, v)` → `col = ?`; Lift `NullRef(col)` (used when the Option is None) → `col IS NULL`.
  private def eqOrNull(col: String, v: Option[String]): Fragment = v match {
    case Some(x) => Fragment.const(col) ++ fr"= $x"
    case None    => Fragment.const(col) ++ fr"IS NULL"
  }

  private def findOne(where: Fragment): Box[RateLimiting] =
    DoobieUtil.runQuery((selectCols ++ where ++ fr"LIMIT 1").query[RlRow].option) match {
      case Some(r) => Full(toEntity(r))
      case None    => Empty
    }

  private def findList(where: Fragment): List[RateLimiting] =
    DoobieUtil.runQuery((selectCols ++ where).query[RlRow].to[List]).map(toEntity)

  private def findRow(rateLimitingId: String): Box[RlRow] =
    DoobieUtil.runQuery((selectCols ++ fr"WHERE ratelimitingid = ${nn(rateLimitingId)} LIMIT 1").query[RlRow].option) match {
      case Some(r) => Full(r)
      case None    => Empty
    }

  override def getAll(): Future[List[RateLimiting]] = Future(findList(Fragment.empty))

  override def getAllByConsumerId(consumerId: String, date: Option[Date] = None): Future[List[RateLimiting]] = Future {
    date match {
      case None => findList(fr"WHERE consumerid = ${nn(consumerId)}")
      case Some(d) =>
        val ts = new Timestamp(d.getTime)
        findList(fr"WHERE consumerid = ${nn(consumerId)} AND fromdate < $ts AND todate > $ts")
    }
  }

  override def getByConsumerId(consumerId: String,
                               apiVersion: String,
                               apiName: String,
                               date: Option[Date] = None): Future[Box[RateLimiting]] = Future {
    val cid = nn(consumerId)
    date match {
      case None =>
        findOne(fr"WHERE consumerid = $cid AND apiversion = ${nn(apiVersion)} AND apiname = ${nn(apiName)} AND bankid IS NULL")
          .or(findOne(fr"WHERE consumerid = $cid AND apiname = ${nn(apiName)} AND bankid IS NULL AND apiversion IS NULL"))
          .or(findOne(fr"WHERE consumerid = $cid AND apiversion = ${nn(apiVersion)} AND bankid IS NULL AND apiname IS NULL"))
          .or(findOne(fr"WHERE consumerid = $cid AND bankid IS NULL AND apiversion IS NULL AND apiname IS NULL"))
      case Some(d) =>
        val ts = new Timestamp(d.getTime)
        val dateFr = fr"AND fromdate < $ts AND todate > $ts"
        findOne(fr"WHERE consumerid = $cid AND apiversion = ${nn(apiVersion)} AND apiname = ${nn(apiName)} AND bankid IS NULL" ++ dateFr)
          .or(findOne(fr"WHERE consumerid = $cid AND apiname = ${nn(apiName)} AND bankid IS NULL AND apiversion IS NULL" ++ dateFr))
          .or(findOne(fr"WHERE consumerid = $cid AND apiversion = ${nn(apiVersion)} AND bankid IS NULL AND apiname IS NULL" ++ dateFr))
          .or(findOne(fr"WHERE consumerid = $cid AND bankid IS NULL AND apiversion IS NULL AND apiname IS NULL" ++ dateFr))
    }
  }

  override def findMostRecentRateLimit(consumerId: String,
                                       bankId: Option[String],
                                       apiVersion: Option[String],
                                       apiName: Option[String]): Future[Option[RateLimiting]] = Future {
    findMostRecentRateLimitCommon(consumerId, bankId, apiVersion, apiName)
  }

  private def findMostRecentRateLimitCommon(consumerId: String,
                                            bankId: Option[String],
                                            apiVersion: Option[String],
                                            apiName: Option[String]): Option[RateLimiting] = {
    val where =
      fr"WHERE consumerid = ${nn(consumerId)} AND " ++
        eqOrNull("bankid", bankId) ++ fr"AND" ++
        eqOrNull("apiversion", apiVersion) ++ fr"AND" ++
        eqOrNull("apiname", apiName) ++
        fr"ORDER BY updatedat DESC"
    findOne(where).toOption
  }

  override def createConsumerCallLimits(consumerId: String, fromDate: Date, toDate: Date,
                                        apiVersion: Option[String], apiName: Option[String], bankId: Option[String],
                                        perSecond: Option[String], perMinute: Option[String], perHour: Option[String],
                                        perDay: Option[String], perWeek: Option[String], perMonth: Option[String]): Future[Box[RateLimiting]] = Future {
    val result = insertRateLimit(consumerId, fromDate, toDate, apiVersion, apiName, bankId,
      perSecond, perMinute, perHour, perDay, perWeek, perMonth)
    result.foreach(_ => Caching.invalidateRateLimitCache(consumerId))
    result
  }

  override def createOrUpdateConsumerCallLimits(consumerId: String, fromDate: Date, toDate: Date,
                                                apiVersion: Option[String], apiName: Option[String], bankId: Option[String],
                                                perSecond: Option[String], perMinute: Option[String], perHour: Option[String],
                                                perDay: Option[String], perWeek: Option[String], perMonth: Option[String]): Future[Box[RateLimiting]] = Future {
    findMostRecentRateLimitCommon(consumerId, bankId, apiVersion, apiName) match {
      case Some(existing) =>
        updateRateLimitRow(existing.rateLimitingId, fromDate, toDate, apiVersion, apiName, bankId,
          perSecond, perMinute, perHour, perDay, perWeek, perMonth)
      case None =>
        insertRateLimit(consumerId, fromDate, toDate, apiVersion, apiName, bankId,
          perSecond, perMinute, perHour, perDay, perWeek, perMonth)
    }
  }

  override def updateConsumerCallLimits(rateLimitingId: String, fromDate: Date, toDate: Date,
                                        apiVersion: Option[String], apiName: Option[String], bankId: Option[String],
                                        perSecond: Option[String], perMinute: Option[String], perHour: Option[String],
                                        perDay: Option[String], perWeek: Option[String], perMonth: Option[String]): Future[Box[RateLimiting]] = Future {
    val result = updateRateLimitRow(rateLimitingId, fromDate, toDate, apiVersion, apiName, bankId,
      perSecond, perMinute, perHour, perDay, perWeek, perMonth)
    result.foreach(rl => Caching.invalidateRateLimitCache(rl.consumerId))
    result
  }

  // INSERT: perX unset → props default (matches the MappedLong default on RateLimiting.create).
  private def insertRateLimit(consumerId: String, fromDate: Date, toDate: Date,
                              apiVersion: Option[String], apiName: Option[String], bankId: Option[String],
                              perSecond: Option[String], perMinute: Option[String], perHour: Option[String],
                              perDay: Option[String], perWeek: Option[String], perMonth: Option[String]): Box[RateLimiting] =
    tryo {
      val newId = APIUtil.generateUUID()
      val now = new Timestamp(System.currentTimeMillis())
      val from = new Timestamp(fromDate.getTime)
      val to = new Timestamp(toDate.getTime)
      val ps = perSecond.map(_.toLong).getOrElse(perDef(PS))
      val pm = perMinute.map(_.toLong).getOrElse(perDef(PM))
      val ph = perHour.map(_.toLong).getOrElse(perDef(PH))
      val pd = perDay.map(_.toLong).getOrElse(perDef(PD))
      val pw = perWeek.map(_.toLong).getOrElse(perDef(PW))
      val pmo = perMonth.map(_.toLong).getOrElse(perDef(PMo))
      DoobieUtil.runQuery(
        sql"""INSERT INTO ratelimiting
                (ratelimitingid, consumerid, bankid, apiname, apiversion,
                 persecondcalllimit, perminutecalllimit, perhourcalllimit, perdaycalllimit, perweekcalllimit, permonthcalllimit,
                 fromdate, todate, createdat, updatedat)
              VALUES ($newId, ${nn(consumerId)}, $bankId, $apiName, $apiVersion,
                      $ps, $pm, $ph, $pd, $pw, $pmo, $from, $to, $now, $now)""".update.run)
      toEntity(RlRow(Some(newId), Some(nn(consumerId)), bankId, apiName, apiVersion,
        Some(ps), Some(pm), Some(ph), Some(pd), Some(pw), Some(pmo), Some(from), Some(to)))
    }

  // UPDATE: perX unset → keep existing value (Lift's `.foreach`); bankid/apiname/apiversion always set from param (orNull).
  private def updateRateLimitRow(rateLimitingId: String, fromDate: Date, toDate: Date,
                                 apiVersion: Option[String], apiName: Option[String], bankId: Option[String],
                                 perSecond: Option[String], perMinute: Option[String], perHour: Option[String],
                                 perDay: Option[String], perWeek: Option[String], perMonth: Option[String]): Box[RateLimiting] =
    findRow(rateLimitingId).flatMap { row =>
      tryo {
        val now = new Timestamp(System.currentTimeMillis())
        val from = new Timestamp(fromDate.getTime)
        val to = new Timestamp(toDate.getTime)
        val ps = perSecond.map(_.toLong).getOrElse(row.persecond.getOrElse(perDef(PS)))
        val pm = perMinute.map(_.toLong).getOrElse(row.perminute.getOrElse(perDef(PM)))
        val ph = perHour.map(_.toLong).getOrElse(row.perhour.getOrElse(perDef(PH)))
        val pd = perDay.map(_.toLong).getOrElse(row.perday.getOrElse(perDef(PD)))
        val pw = perWeek.map(_.toLong).getOrElse(row.perweek.getOrElse(perDef(PW)))
        val pmo = perMonth.map(_.toLong).getOrElse(row.permonth.getOrElse(perDef(PMo)))
        DoobieUtil.runQuery(
          sql"""UPDATE ratelimiting
                SET fromdate = $from, todate = $to,
                    persecondcalllimit = $ps, perminutecalllimit = $pm, perhourcalllimit = $ph,
                    perdaycalllimit = $pd, perweekcalllimit = $pw, permonthcalllimit = $pmo,
                    bankid = $bankId, apiname = $apiName, apiversion = $apiVersion, updatedat = $now
                WHERE ratelimitingid = ${nn(rateLimitingId)}""".update.run)
        toEntity(row.copy(
          bankid = bankId, apiname = apiName, apiversion = apiVersion,
          persecond = Some(ps), perminute = Some(pm), perhour = Some(ph), perday = Some(pd), perweek = Some(pw), permonth = Some(pmo),
          fromdate = Some(from), todate = Some(to)))
      }
    }

  override def getByRateLimitingId(rateLimitingId: String): Future[Box[RateLimiting]] = Future {
    findOne(fr"WHERE ratelimitingid = ${nn(rateLimitingId)}")
  }

  override def deleteByRateLimitingId(rateLimitingId: String): Future[Box[Boolean]] = Future {
    val rl = findOne(fr"WHERE ratelimitingid = ${nn(rateLimitingId)}")
    val result = rl.map { _ =>
      DoobieUtil.runQuery(
        sql"DELETE FROM ratelimiting WHERE ratelimitingid = ${nn(rateLimitingId)}".update.run) > 0
    }
    rl.foreach(r => Caching.invalidateRateLimitCache(r.consumerId))
    result
  }

  private def getActiveCallLimitsByConsumerIdAtDateCached(consumerId: String, dateWithHour: String): List[RateLimiting] = {
    val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH")
    val localDateTime = LocalDateTime.parse(dateWithHour, formatter)

    val startOfHour = localDateTime.withMinute(0).withSecond(0)
    val startInstant = startOfHour.atZone(java.time.ZoneOffset.UTC).toInstant()
    val startDate = Date.from(startInstant)

    val endOfHour = localDateTime.withMinute(59).withSecond(59)
    val endInstant = endOfHour.atZone(java.time.ZoneOffset.UTC).toInstant()
    val endDate = Date.from(endInstant)

    val cacheKey = s"${RATE_LIMIT_ACTIVE_PREFIX}${consumerId}_${dateWithHour}"
    Caching.memoizeSyncWithProvider(Some(cacheKey))(RATE_LIMIT_ACTIVE_CACHE_TTL second) {
      // Active if: fromDate <= endOfHour AND toDate >= startOfHour
      val start = new Timestamp(startDate.getTime)
      val end = new Timestamp(endDate.getTime)
      findList(fr"WHERE consumerid = ${nn(consumerId)} AND fromdate <= $end AND todate >= $start")
    }
  }

  override def getActiveCallLimitsByConsumerIdAtDate(consumerId: String, dateUtc: Date): Future[List[RateLimiting]] = Future {
    def dateWithHour: String = {
      val instant = dateUtc.toInstant()
      val localDateTime = LocalDateTime.ofInstant(instant, java.time.ZoneOffset.UTC)
      val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd-HH")
      localDateTime.format(formatter)
    }
    getActiveCallLimitsByConsumerIdAtDateCached(consumerId, dateWithHour)
  }
}
