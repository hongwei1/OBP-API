package code.model

import code.api.util.DoobieUtil
import code.token.TokensProvider
import com.openbankproject.commons.ExecutionContext.Implicits.global
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.{randomInt, tryo}

import java.sql.Timestamp
import java.util.Date
import scala.concurrent.Future

object DoobieTokenProvider extends TokensProvider {

  private case class TokenRow(
    id: Long,
    key: Option[String],
    duration: Option[Long],
    insertDate: Option[Timestamp],
    tokenType: Option[String],
    userForeignKey: Option[Long],
    callbackUrl: Option[String],
    verifier: Option[String],
    expirationDate: Option[Timestamp],
    thirdPartyApplicationSecret: Option[String],
    consumerId: Option[Long],
    secret: Option[String]
  )

  private val selectCols: Fragment =
    fr"""SELECT id, key_c, duration, insertdate, tokentype, userforeignkey,
                callbackurl, verifier, expirationdate, thirdpartyapplicationsecret,
                consumerid, secret
         FROM token"""

  private def toEntity(row: TokenRow): Token = {
    val t = Token.create
    // Token.id is MappedLongIndex with writePermission_? = false; runSafe enables
    // safe_? = true so the primary-key field can be set, matching how Lift Mapper
    // populates entities when loading from the database.
    t.runSafe {
      t.id(row.id)
        .key(row.key.getOrElse(""))
        .duration(row.duration.getOrElse(0L))
        .insertDate(row.insertDate.map(ts => new Date(ts.getTime)).orNull)
        .tokenType(row.tokenType.getOrElse(""))
        .userForeignKey(row.userForeignKey.getOrElse(0L))
        .callbackURL(row.callbackUrl.getOrElse(""))
        .verifier(row.verifier.getOrElse(""))
        .expirationDate(row.expirationDate.map(ts => new Date(ts.getTime)).orNull)
        .thirdPartyApplicationSecret(row.thirdPartyApplicationSecret.getOrElse(""))
        .consumerId(row.consumerId.getOrElse(0L))
        .secret(row.secret.getOrElse(""))
    }
    t
  }

  private def findOne(where: Fragment): Box[Token] =
    DoobieUtil.runQuery((selectCols ++ where ++ fr"LIMIT 1").query[TokenRow].option) match {
      case Some(r) => Full(toEntity(r))
      case None    => Empty
    }

  override def getTokenByKey(key: String): Box[Token] =
    findOne(fr"WHERE key_c = $key")

  override def getTokenByKeyFuture(key: String): Future[Box[Token]] =
    Future(getTokenByKey(key))

  override def getTokenByKeyAndType(key: String, tokenType: TokenType): Box[Token] =
    findOne(fr"WHERE key_c = $key AND tokentype = ${tokenType.toString}")

  override def getTokenByKeyAndTypeFuture(key: String, tokenType: TokenType): Future[Box[Token]] =
    Future(getTokenByKeyAndType(key, tokenType))

  override def createToken(
    tokenType: TokenType,
    consumerId: Option[Long],
    userId: Option[Long],
    key: Option[String],
    secret: Option[String],
    duration: Option[Long],
    expirationDate: Option[Date],
    insertDate: Option[Date],
    callbackURL: Option[String]
  ): Box[Token] = tryo {
    // Bind nullable timestamp columns as Option — Doobie's Put[Timestamp] rejects a raw null.
    val expirationTs: Option[Timestamp] = expirationDate.map(d => new Timestamp(d.getTime))
    val insertTs: Option[Timestamp]     = insertDate.map(d => new Timestamp(d.getTime))
    val tokenTypeStr = tokenType.toString

    val generatedId = DoobieUtil.runQuery(
      sql"""INSERT INTO token
              (tokentype, consumerid, userforeignkey, key_c, secret, duration, expirationdate, insertdate, callbackurl)
            VALUES
              ($tokenTypeStr, $consumerId, $userId, $key, $secret, $duration, $expirationTs, $insertTs, $callbackURL)"""
        .update.withUniqueGeneratedKeys[Long]("id")
    )
    findOne(fr"WHERE id = $generatedId")
      .openOrThrowException("Token not found after insert")
  }

  override def gernerateVerifier(id: Long): String = {
    val existing = DoobieUtil.runQuery(
      fr"SELECT COALESCE(verifier, '') FROM token WHERE id = $id".query[String].option
    )
    existing match {
      case Some(v) if v.nonEmpty => v
      case _ =>
        def r() = randomInt(9).toString
        val generated = (1 to 5).map(_ => r()).mkString("")
        DoobieUtil.runQuery(
          sql"UPDATE token SET verifier = $generated WHERE id = $id".update.run
        )
        generated
    }
  }

  override def updateToken(id: Long, userId: Long): Boolean = {
    val count = DoobieUtil.runQuery(
      sql"UPDATE token SET userforeignkey = $userId WHERE id = $id".update.run
    )
    count > 0
  }

  override def deleteToken(id: Long): Boolean = {
    val count = DoobieUtil.runQuery(
      sql"DELETE FROM token WHERE id = $id".update.run
    )
    count > 0
  }

  override def deleteExpiredTokens(currentDate: Date): Boolean = {
    val ts = new Timestamp(currentDate.getTime)
    DoobieUtil.runQuery(
      sql"DELETE FROM token WHERE expirationdate < $ts".update.run
    )
    true
  }
}
