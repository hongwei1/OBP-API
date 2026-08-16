package code.views

import code.api.util.DoobieUtil
import code.views.system.AccountAccess
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}

import java.sql.Timestamp

/**
 * Doobie-backed writes for the `accountaccess` table, replacing the Lift Mapper
 * grant/revoke/bulk-delete operations. The grant (idempotent INSERT) and the
 * bank/account bulk-delete are pure writes — they don't fetch-then-mutate a Lift
 * entity, so they translate cleanly to Doobie SQL.
 */
object DoobieAccountAccessProvider {

  private case class AccessRow(
    bankId: String,
    accountId: String,
    viewId: String,
    userFk: Long,
    consumerId: String
  )

  private val selectCols: Fragment =
    fr"SELECT bank_id, account_id, view_id, user_fk, consumer_id FROM accountaccess"

  private def toAccountAccess(row: AccessRow): AccountAccess =
    AccountAccess.create
      .bank_id(row.bankId)
      .account_id(row.accountId)
      .view_id(row.viewId)
      .user_fk(row.userFk)
      .consumer_id(row.consumerId)

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

  /** Delete accountaccess rows by bank+account+user (for revokeAllAccountAccess). */
  def deleteByBankAccountUser(bankId: String, accountId: String, userPk: Long): Int =
    DoobieUtil.runQuery(
      sql"DELETE FROM accountaccess WHERE bank_id = $bankId AND account_id = $accountId AND user_fk = $userPk"
        .update.run)

  /** Count accountaccess rows for bank+account+view (for canRevokeOwnerAccess). */
  def countByBankAccountView(bankId: String, accountId: String, viewId: String): Long =
    DoobieUtil.runQuery(
      sql"""SELECT COUNT(*) FROM accountaccess
            WHERE bank_id = $bankId AND account_id = $accountId AND view_id = $viewId"""
        .query[Long].unique)

  /** Find one AccountAccess by bank+account+view+user (returns Box). */
  def findByBankAccountViewUser(bankId: String, accountId: String, viewId: String, userPk: Long): Box[AccountAccess] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE bank_id = $bankId AND account_id = $accountId AND view_id = $viewId AND user_fk = $userPk LIMIT 1")
        .query[AccessRow].option)
      .map(r => Full(toAccountAccess(r))).getOrElse(Empty)

  /** Find all AccountAccess rows for bank+account (for assignedViewsForAccount). */
  def findAllByBankAccount(bankId: String, accountId: String): List[AccountAccess] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE bank_id = $bankId AND account_id = $accountId")
        .query[AccessRow].to[List])
      .map(toAccountAccess)

  /** Find all AccountAccess for a system view by view_id only (no bank/account). */
  def findAllBySystemViewId(viewId: String): List[AccountAccess] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE view_id = $viewId")
        .query[AccessRow].to[List])
      .map(toAccountAccess)

  /** Find AccountAccess rows whose view_id is in the given list. */
  def findAllByViewIds(viewIds: List[String]): List[AccountAccess] = {
    if (viewIds.isEmpty) Nil
    else {
      val inFr = viewIds.map(v => fr"$v").reduceLeft(_ ++ fr"," ++ _)
      DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE view_id IN (" ++ inFr ++ fr")")
          .query[AccessRow].to[List])
        .map(toAccountAccess)
    }
  }

  /** Find AccountAccess for custom views, matching (bank_id, account_id, view_id) tuples exactly. */
  def findAllByBankAccountViewTuples(tuples: List[(String, String, String)]): List[AccountAccess] = {
    if (tuples.isEmpty) Nil
    else {
      val orFr = tuples.map { case (b, a, v) =>
        fr"(bank_id = $b AND account_id = $a AND view_id = $v)"
      }.reduceLeft(_ ++ fr"OR" ++ _)
      DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE" ++ orFr)
          .query[AccessRow].to[List])
        .map(toAccountAccess)
    }
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

  /** Delete all rows from accountaccess (used by bulkDeleteAll). */
  def deleteAll(): Unit =
    DoobieUtil.runQuery(sql"DELETE FROM accountaccess".update.run)
}
