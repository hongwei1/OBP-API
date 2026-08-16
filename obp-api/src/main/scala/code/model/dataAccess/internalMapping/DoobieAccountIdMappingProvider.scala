package code.model.dataAccess.internalMapping

import code.api.util.DoobieUtil
import com.openbankproject.commons.model.AccountId
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import java.sql.Timestamp

object DoobieAccountIdMappingProvider extends AccountIdMappingProvider {

  private case class AcctIdMapRow(
    id: Option[Long],
    maccountid: Option[String],
    maccountplaintextreference: Option[String],
    createdat: Option[Timestamp],
    updatedat: Option[Timestamp]
  )

  private case class DoobieAccountIdMapping(row: AcctIdMapRow) {
    def accountId: AccountId = AccountId(row.maccountid.getOrElse(""))
    def accountPlainTextReference: String = row.maccountplaintextreference.getOrElse("")
  }

  private def nn(s: String): String = if (s == null) "" else s

  private val selectCols: Fragment =
    fr"SELECT id, maccountid, maccountplaintextreference, createdat, updatedat FROM accountidmapping"

  override def getOrCreateAccountId(accountPlainTextReference: String): Box[AccountId] =
    tryo {
      val existing = DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE maccountplaintextreference = ${nn(accountPlainTextReference)} LIMIT 1")
          .query[AcctIdMapRow].option)
      existing match {
        case Some(row) =>
          Full(row.maccountid.map(AccountId(_)).getOrElse(AccountId("")))
        case None =>
          val newAccountId = java.util.UUID.randomUUID().toString
          val now = new Timestamp(System.currentTimeMillis())
          DoobieUtil.runQuery(
            sql"""INSERT INTO accountidmapping (maccountid, maccountplaintextreference, createdat, updatedat)
                   VALUES ($newAccountId, ${nn(accountPlainTextReference)}, $now, $now)""".update.run)
          Full(AccountId(newAccountId))
      }
    }.getOrElse(Empty)

  override def getAccountPlainTextReference(accountId: AccountId): Box[String] =
    tryo {
      DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE maccountid = ${nn(accountId.value)} LIMIT 1")
          .query[AcctIdMapRow].option) match {
        case Some(row) =>
          Full(row.maccountplaintextreference.getOrElse(""))
        case None =>
          Empty
      }
    }.getOrElse(Empty)
}
