package code.nonce

import code.api.util.DoobieUtil
import code.model.Nonce
import com.openbankproject.commons.ExecutionContext.Implicits.global
import doobie._
import doobie.implicits._
import net.liftweb.common.Box
import net.liftweb.util.Helpers.tryo

import java.sql.Timestamp
import java.util.Date
import scala.concurrent.Future

object DoobieNonceProvider extends NoncesProvider {

  // Table "nonce": columns value, id (identity), timestamp_c (TIMESTAMP reserved → _c escape),
  // consumerkey, tokenkey. The Nonce Lift entity stays in Boot.ToSchemify.models during coexistence.
  private def nn(s: String): String = if (s == null) "" else s

  override def createNonce(id: Option[Long],
                           consumerKey: Option[String],
                           tokenKey: Option[String],
                           timestamp: Option[Date],
                           value: Option[String]): Box[Nonce] =
    tryo {
      val consumerKeyStr = nn(consumerKey.getOrElse(""))
      val tokenKeyStr = nn(tokenKey.getOrElse(""))
      val valueStr = nn(value.getOrElse(""))
      val ts: Option[Timestamp] = timestamp.map(d => new Timestamp(d.getTime))
      id match {
        case Some(v) =>
          DoobieUtil.runQuery(
            sql"""INSERT INTO nonce (id, consumerkey, tokenkey, timestamp_c, value)
                  VALUES ($v, $consumerKeyStr, $tokenKeyStr, $ts, $valueStr)""".update.run)
        case None =>
          DoobieUtil.runQuery(
            sql"""INSERT INTO nonce (consumerkey, tokenkey, timestamp_c, value)
                  VALUES ($consumerKeyStr, $tokenKeyStr, $ts, $valueStr)""".update.run)
      }
      // Return an in-memory Nonce holder mirroring the saved values (no Lift DB write).
      // id (MappedLongIndex) is intentionally not set — no caller consumes the returned id.
      val n = Nonce.create
      consumerKey.foreach(n.consumerkey(_))
      tokenKey.foreach(n.tokenKey(_))
      timestamp.foreach(n.timestamp(_))
      value.foreach(n.`value`(_))
      n
    }

  override def deleteExpiredNonces(currentDate: Date): Boolean = {
    val cutoff = new Timestamp(currentDate.getTime)
    DoobieUtil.runQuery(
      sql"DELETE FROM nonce WHERE timestamp_c < $cutoff".update.run) >= 0
  }

  override def countNonces(consumerKey: String,
                           tokenKey: String,
                           timestamp: Date,
                           value: String): Long = {
    val ts = new Timestamp(timestamp.getTime)
    DoobieUtil.runQuery(
      sql"""SELECT COUNT(*) FROM nonce
            WHERE value = ${nn(value)} AND tokenkey = ${nn(tokenKey)}
              AND consumerkey = ${nn(consumerKey)} AND timestamp_c = $ts""".query[Long].unique)
  }

  override def countNoncesFuture(consumerKey: String,
                                 tokenKey: String,
                                 timestamp: Date,
                                 value: String): Future[Long] =
    Future { countNonces(consumerKey, tokenKey, timestamp, value) }
}
