package code.transaction.internalMapping

import code.api.util.DoobieUtil
import com.openbankproject.commons.model.TransactionId
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import java.sql.Timestamp

object DoobieTransactionIdMappingProvider extends TransactionIdMappingProvider {

  private case class TxnIdMapRow(
    id: Option[Long],
    transactionid: Option[String],
    transactionplaintextreference: Option[String],
    createdat: Option[Timestamp],
    updatedat: Option[Timestamp]
  )

  private def nn(s: String): String = if (s == null) "" else s

  private val selectCols: Fragment =
    fr"SELECT id, transactionid, transactionplaintextreference, createdat, updatedat FROM transactionidmapping"

  override def getOrCreateTransactionId(transactionPlainTextReference: String): Box[TransactionId] =
    tryo {
      val existing = DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE transactionplaintextreference = ${nn(transactionPlainTextReference)} LIMIT 1")
          .query[TxnIdMapRow].option)
      existing match {
        case Some(row) =>
          Full(TransactionId(row.transactionid.getOrElse("")))
        case None =>
          val newTransactionId = code.api.util.APIUtil.generateUUID()
          val now = new Timestamp(System.currentTimeMillis())
          DoobieUtil.runQuery(
            sql"""INSERT INTO transactionidmapping (transactionid, transactionplaintextreference, createdat, updatedat)
                   VALUES ($newTransactionId, ${nn(transactionPlainTextReference)}, $now, $now)""".update.run)
          Full(TransactionId(newTransactionId))
      }
    }.getOrElse(Empty)

  override def getTransactionPlainTextReference(transactionId: TransactionId): Box[String] =
    tryo {
      DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE transactionid = ${nn(transactionId.value)} LIMIT 1")
          .query[TxnIdMapRow].option) match {
        case Some(row) =>
          Full(row.transactionplaintextreference.getOrElse(""))
        case None =>
          Empty
      }
    }.getOrElse(Empty)
}
