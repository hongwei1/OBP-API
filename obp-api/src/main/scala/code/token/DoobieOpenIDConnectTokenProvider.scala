package code.token

import code.api.util.DoobieUtil
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty}
import net.liftweb.util.Helpers.tryo

import java.sql.Timestamp

object DoobieOpenIDConnectTokenProvider extends OpenIDConnectTokensProvider {

  // Table: openidconnecttoken (no dbTableName override → lowercase class name).
  // Cols: accesstoken, idtoken, refreshtoken, scope, tokentype, expiresin, authuseryprimarykey, createdat, updatedat.
  private case class OIDCTokenRow(
    accesstoken: Option[String],
    idtoken: Option[String],
    refreshtoken: Option[String],
    scope: Option[String],
    tokentype: Option[String],
    expiresin: Option[Long],
    authuserprimarykey: Option[Long]
  )

  private def nn(s: String): String = if (s == null) "" else s

  private def toHolder(row: OIDCTokenRow): OpenIDConnectToken =
    OpenIDConnectToken.create
      .AccessToken(row.accesstoken.getOrElse(""))
      .IDToken(row.idtoken.getOrElse(""))
      .RefreshToken(row.refreshtoken.getOrElse(""))
      .Scope(row.scope.getOrElse(""))
      .TokenType(row.tokentype.getOrElse(""))
      .ExpiresIn(row.expiresin.getOrElse(0L))
      .AuthUserPrimaryKey(row.authuserprimarykey.getOrElse(0L))

  private val selectCols: Fragment =
    fr"SELECT accesstoken, idtoken, refreshtoken, scope, tokentype, expiresin, authuserprimarykey FROM openidconnecttoken"

  override def createToken(tokenType: String, accessToken: String, idToken: String,
                           refreshToken: String, scope: String, expiresIn: Long,
                           authUserPrimaryKey: Long): Box[OpenIDConnectToken] =
    tryo {
      val now = new Timestamp(System.currentTimeMillis())
      DoobieUtil.runQuery(
        sql"""INSERT INTO openidconnecttoken
                (accesstoken, idtoken, refreshtoken, scope, tokentype, expiresin, authuserprimarykey, createdat, updatedat)
              VALUES (${nn(accessToken)}, ${nn(idToken)}, ${nn(refreshToken)}, ${nn(scope)},
                      ${nn(tokenType)}, $expiresIn, $authUserPrimaryKey, $now, $now)""".update.run)
      toHolder(OIDCTokenRow(Some(nn(accessToken)), Some(nn(idToken)), Some(nn(refreshToken)),
        Some(nn(scope)), Some(nn(tokenType)), Some(expiresIn), Some(authUserPrimaryKey)))
    }

  override def getOpenIDConnectTokenByAuthUser(authUserPrimaryKey: Long): Box[OpenIDConnectToken] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE authuserprimarykey = $authUserPrimaryKey ORDER BY createdat DESC LIMIT 1")
        .query[OIDCTokenRow].option) match {
      case Some(r) => net.liftweb.common.Full(toHolder(r))
      case None    => Empty
    }
}
