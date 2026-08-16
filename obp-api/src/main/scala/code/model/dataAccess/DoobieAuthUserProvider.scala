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

  /** createdAt / updatedAt / validated for the authuser whose user_c == userPk. */
  case class AuthUserMetaRow(
    createdAt: Option[java.sql.Timestamp],
    updatedAt: Option[java.sql.Timestamp],
    validated: Option[Boolean]
  )

  def findMetaByUserFk(userPk: Long): Option[AuthUserMetaRow] =
    DoobieUtil.runQuery(
      fr"SELECT createdat, updatedat, validated FROM authuser WHERE user_c = $userPk LIMIT 1"
        .query[AuthUserMetaRow].option
    )

  /** Return (firstName, lastName) for the authuser whose user_c == userPk, or None if not found. */
  def getNamesByUserFk(userPk: Long): Option[(String, String)] =
    DoobieUtil.runQuery(
      fr"SELECT firstname, lastname FROM authuser WHERE user_c = $userPk LIMIT 1"
        .query[(Option[String], Option[String])].option
    ).map { case (fn, ln) => (fn.getOrElse(""), ln.getOrElse("")) }

  /** Validation-email fields: (email, validated, uniqueId) for a local-provider user. */
  case class ValidationAuthRow(email: Option[String], validated: Option[Boolean], uniqueId: Option[String])

  def findValidationInfoByUsernameAndProvider(username: String, provider: String): Option[ValidationAuthRow] =
    DoobieUtil.runQuery(
      fr"SELECT email, validated, uniqueid FROM authuser WHERE username = $username AND provider = $provider LIMIT 1"
        .query[ValidationAuthRow].option
    )

  /** Password-reset fields: id, username, email, validated for a user looked up by username. */
  case class PasswordResetAuthRow(id: Long, username: Option[String], email: Option[String], validated: Option[Boolean])

  def findPasswordResetInfoByUsername(username: String): Option[PasswordResetAuthRow] =
    DoobieUtil.runQuery(
      fr"SELECT id, username, email, validated FROM authuser WHERE username = $username LIMIT 1"
        .query[PasswordResetAuthRow].option
    )

  def findPasswordResetInfoByUsernameAndProvider(username: String, provider: String): Option[PasswordResetAuthRow] =
    DoobieUtil.runQuery(
      fr"SELECT id, username, email, validated FROM authuser WHERE username = $username AND provider = $provider LIMIT 1"
        .query[PasswordResetAuthRow].option
    )

  /** Update uniqueid for the authuser row with the given primary-key id. */
  def updateUniqueId(id: Long, newUniqueId: String): Unit =
    DoobieUtil.runQuery(
      sql"UPDATE authuser SET uniqueid = $newUniqueId WHERE id = $id".update.run
    )

  /**
   * Full row needed for the email-validation and password-reset-complete flows.
   * userId is sourced from the joined resourceuser row.
   */
  case class AuthFullRow(
    id: Long,
    userId: Option[String],   // resourceuser.userid_
    email: Option[String],
    username: Option[String],
    provider: Option[String],
    validated: Option[Boolean]
  )

  def findByUniqueId(uniqueId: String): Option[AuthFullRow] =
    DoobieUtil.runQuery(
      fr"""SELECT a.id, r.userid_, a.email, a.username, a.provider, a.validated
           FROM authuser a
           LEFT JOIN resourceuser r ON r.id = a.user_c
           WHERE a.uniqueid = $uniqueId LIMIT 1"""
        .query[AuthFullRow].option
    )

  /**
   * Set validated=true and rotate the uniqueid token (mirrors validateAndResetToken in Lift).
   * Returns the new uniqueid that was written.
   */
  def validateAndRotateToken(id: Long): String = {
    val newUniqueId = java.util.UUID.randomUUID().toString.replace("-", "")
    DoobieUtil.runQuery(
      sql"UPDATE authuser SET validated = true, uniqueid = $newUniqueId WHERE id = $id".update.run
    )
    newUniqueId
  }

  /**
   * Hash `newPassword` exactly as Lift's MappedPassword.set() does and persist it.
   * Also rotates the uniqueid so the reset link can only be used once.
   * Replicates: salt=BCrypt.gensalt(), hash=BCrypt.hashpw(pw,salt);
   *   password_pw = "b;" + hash.take(44), password_slt = hash.drop(44).
   */
  def updatePassword(id: Long, newPassword: String): Unit = {
    val salt = BCrypt.gensalt()
    val hash = BCrypt.hashpw(newPassword, salt)
    val pw   = "b;" + hash.take(44)
    val slt  = hash.drop(44)
    val newUniqueId = java.util.UUID.randomUUID().toString.replace("-", "")
    DoobieUtil.runQuery(
      sql"""UPDATE authuser
            SET password_pw = $pw, password_slt = $slt, uniqueid = $newUniqueId
            WHERE id = $id""".update.run
    )
  }

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
