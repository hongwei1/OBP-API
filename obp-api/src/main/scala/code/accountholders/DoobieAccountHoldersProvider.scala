package code.accountholders

import code.api.util.DoobieUtil
import code.users.Users
import com.openbankproject.commons.model.{AccountId, BankId, BankIdAccountId, User}
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

object DoobieAccountHoldersProvider extends AccountHolders {

  private case class Row(
    id: Long,
    userPk: Long,
    bankPermalink: String,
    accountPermalink: String,
    source: Option[String]
  )

  private val selectCols: Fragment =
    fr"""SELECT id, user_c, accountbankpermalink, accountpermalink, source
         FROM mapperaccountholders"""

  // Build an in-memory MapperAccountHolders holder (no saveMe) for returning to callers.
  // Callers either discard the result or check existence; none call .saveMe() on it.
  private def toHolder(row: Row): MapperAccountHolders =
    MapperAccountHolders.create
      .accountBankPermalink(row.bankPermalink)
      .accountPermalink(row.accountPermalink)
      .user(row.userPk)
      .source(row.source.orNull)

  override def getAccountHolders(bankId: BankId, accountId: AccountId): Set[User] = {
    val rows = DoobieUtil.runQuery(
      (selectCols ++ fr"""WHERE accountbankpermalink = ${bankId.value}
                            AND accountpermalink = ${accountId.value}""")
        .query[Row].to[List]
    )
    rows.flatMap { row =>
      Users.users.vend.getUserByResourceUserId(row.userPk).toOption
    }.toSet
  }

  override def getAccountsHeld(bankId: BankId, user: User): Set[BankIdAccountId] = {
    val userPk = user.userPrimaryKey.value
    val rows = DoobieUtil.runQuery(
      (selectCols ++ fr"""WHERE accountbankpermalink = ${bankId.value}
                            AND user_c = $userPk""")
        .query[Row].to[List]
    )
    rows.map(r => BankIdAccountId(BankId(r.bankPermalink), AccountId(r.accountPermalink))).toSet
  }

  override def getAccountsHeldByUser(user: User, source: Option[String] = None): Set[BankIdAccountId] = {
    val userPk = user.userPrimaryKey.value
    val sourceFilter: Fragment = source match {
      case None                               => Fragment.empty
      case Some(s) if s == null || s.isEmpty  => fr"AND source IS NULL"
      case Some(s)                            => fr"AND source = $s"
    }
    val rows = DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE user_c = $userPk" ++ sourceFilter)
        .query[Row].to[List]
    )
    rows.map(r => BankIdAccountId(BankId(r.bankPermalink), AccountId(r.accountPermalink))).toSet
  }

  override def getOrCreateAccountHolder(user: User, bankIdAccountId: BankIdAccountId, source: Option[String] = None): Box[MapperAccountHolders] = {
    val userPk     = user.userPrimaryKey.value
    val bankStr    = bankIdAccountId.bankId.value
    val accountStr = bankIdAccountId.accountId.value
    val safeSource: Option[String] = source.flatMap(s => Option(s))

    val existing = DoobieUtil.runQuery(
      (selectCols ++ fr"""WHERE user_c = $userPk
                          AND accountbankpermalink = $bankStr
                          AND accountpermalink = $accountStr
                          LIMIT 1""")
        .query[Row].option
    )
    existing match {
      case Some(row) =>
        Full(toHolder(row))
      case None =>
        tryo {
          DoobieUtil.runQuery(
            sql"""INSERT INTO mapperaccountholders
                    (user_c, accountbankpermalink, accountpermalink, source)
                  VALUES ($userPk, $bankStr, $accountStr, $safeSource)
               """.update.run
          )
          val row = DoobieUtil.runQuery(
            (selectCols ++ fr"""WHERE user_c = $userPk
                                AND accountbankpermalink = $bankStr
                                AND accountpermalink = $accountStr
                                LIMIT 1""")
              .query[Row].unique
          )
          toHolder(row)
        }
    }
  }

  override def deleteAccountHolder(user: User, bankIdAccountId: BankIdAccountId): Box[Boolean] =
    tryo {
      val userPk     = user.userPrimaryKey.value
      val bankStr    = bankIdAccountId.bankId.value
      val accountStr = bankIdAccountId.accountId.value
      val rows = DoobieUtil.runQuery(
        sql"""DELETE FROM mapperaccountholders
              WHERE user_c = $userPk
              AND accountbankpermalink = $bankStr
              AND accountpermalink = $accountStr
           """.update.run
      )
      rows > 0
    }

  override def bulkDeleteAllAccountHolders(): Box[Boolean] =
    tryo {
      DoobieUtil.runQuery(sql"DELETE FROM mapperaccountholders".update.run)
      true
    }
}
