package code.model.dataAccess

import code.api.util.DoobieUtil
import doobie._
import doobie.implicits._
import net.liftweb.util.Helpers
import org.mindrot.jbcrypt.BCrypt

/**
 * Doobie-backed data access for the `authuser` table, replacing the Lift Mapper
 * queries used on the authentication hot-path in [[AuthUser]].
 *
 * The AuthUser entity (a Lift MegaProtoUser) is intentionally retained
 * (Boot.ToSchemify, signup/session/web-form scaffolding) during the coexistence
 * phase — both paths read and write the same table.
 *
 * Password verification reproduces Lift's `MappedPassword.match_?` exactly:
 *   - bcrypt path (the only path for users created since the bcrypt switch): the
 *     60-char bcrypt hash was split on save into `password_pw = "b;" + hash.take(44)`
 *     and `password_slt = hash.drop(44)`. To verify we re-assemble the hash as
 *     `password_pw.substring(2) + password_slt` and call `BCrypt.checkpw`.
 *   - legacy salted-SHA fallback: `Helpers.hash("{" + candidate + "} salt={" + salt + "}")`.
 */
object DoobieAuthUserProvider {

  /** The fields the login hot-path needs from a single authuser row. */
  case class AuthRow(
    userC: Option[Long],          // FK to resourceuser.id (AuthUser.user)
    validated: Option[Boolean],
    passwordPw: Option[String],   // password_pw column ("b;" + first 44 of the bcrypt hash)
    passwordSlt: Option[String]   // password_slt column (last 16 of the bcrypt hash)
  )

  private val selectAuth: Fragment =
    fr"SELECT user_c, validated, password_pw, password_slt FROM authuser"

  /** Find a single authuser by username + provider (mirrors find(By(username), By(provider))). */
  def findByUsernameAndProvider(username: String, provider: String): Option[AuthRow] =
    DoobieUtil.runQuery(
      (selectAuth ++ fr"WHERE username = $username AND provider = $provider LIMIT 1")
        .query[AuthRow].option)

  /**
   * Verify a candidate password against the stored hash + salt, exactly as
   * Lift's `MappedPassword.match_?` does.
   */
  def matchPassword(candidate: String, passwordPw: String, passwordSlt: String): Boolean = {
    val pw  = if (passwordPw == null) "" else passwordPw
    val slt = if (passwordSlt == null) "" else passwordSlt
    if (pw.startsWith("b;")) BCrypt.checkpw(candidate, pw.substring(2) + slt)
    else Helpers.hash("{" + candidate + "} salt={" + slt + "}") == pw
  }
}
