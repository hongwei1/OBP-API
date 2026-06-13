package code.views

import code.api.util.DoobieUtil
import doobie._
import doobie.implicits._

import java.sql.Timestamp

/**
 * Doobie-backed writes for the `account_access` table, replacing the Lift Mapper
 * grant/revoke/bulk-delete operations. The grant (idempotent INSERT) and the
 * bank/account bulk-delete are pure writes — they don't fetch-then-mutate a Lift
 * entity, so they translate cleanly to Doobie SQL.
 *
 * Note: the `AccountAccess.find*` lookups that return a Lift entity whose
 * `.delete_!` is later called (the per-row revoke paths) intentionally stay on
 * Lift Mapper during coexistence — both paths read/write the same table.
 */
object DoobieAccountAccessProvider {

  /** Existence check on the full unique index (bank, account, view, user, consumer). */
  def existsByUniqueIndex(bankId: String, accountId: String, viewId: String,
                          userPrimaryKey: Long, consumerId: String): Boolean =
    DoobieUtil.runQuery(
      sql"""SELECT COUNT(*) FROM accountaccess
            WHERE bank_id = $bankId AND account_id = $accountId AND view_id = $viewId
              AND user_fk = $userPrimaryKey AND consumer_id = $consumerId"""
        .query[Long].unique) > 0L

  /** Insert one account_access grant. Returns true if a row was written. */
  def grant(userPrimaryKey: Long, bankId: String, accountId: String,
            viewId: String, consumerId: String): Boolean = {
    val now = new Timestamp(System.currentTimeMillis())
    DoobieUtil.runQuery(
      sql"""INSERT INTO accountaccess (user_fk, bank_id, account_id, view_id, consumer_id, createdat, updatedat)
            VALUES ($userPrimaryKey, $bankId, $accountId, $viewId, $consumerId, $now, $now)"""
        .update.run) > 0
  }

  /** Delete all account_access rows for an account (mirrors bulkDelete by bank+account). */
  def deleteAllByBankAccount(bankId: String, accountId: String): Boolean = {
    DoobieUtil.runQuery(
      sql"DELETE FROM accountaccess WHERE bank_id = $bankId AND account_id = $accountId".update.run)
    true
  }

  // ── per-row revoke: existence check (for the CannotFindAccountAccess error) then delete ──

  def existsByUserPrimaryKey(bankId: String, accountId: String, viewId: String, userPrimaryKey: Long): Boolean =
    DoobieUtil.runQuery(
      sql"""SELECT COUNT(*) FROM accountaccess
            WHERE bank_id = $bankId AND account_id = $accountId AND view_id = $viewId AND user_fk = $userPrimaryKey"""
        .query[Long].unique) > 0L

  def deleteByUserPrimaryKey(bankId: String, accountId: String, viewId: String, userPrimaryKey: Long): Boolean =
    DoobieUtil.runQuery(
      sql"""DELETE FROM accountaccess
            WHERE bank_id = $bankId AND account_id = $accountId AND view_id = $viewId AND user_fk = $userPrimaryKey"""
        .update.run) > 0

  def existsByConsumerId(bankId: String, accountId: String, viewId: String, consumerId: String): Boolean =
    DoobieUtil.runQuery(
      sql"""SELECT COUNT(*) FROM accountaccess
            WHERE bank_id = $bankId AND account_id = $accountId AND view_id = $viewId AND consumer_id = $consumerId"""
        .query[Long].unique) > 0L

  def deleteByConsumerId(bankId: String, accountId: String, viewId: String, consumerId: String): Boolean =
    DoobieUtil.runQuery(
      sql"""DELETE FROM accountaccess
            WHERE bank_id = $bankId AND account_id = $accountId AND view_id = $viewId AND consumer_id = $consumerId"""
        .update.run) > 0
}
