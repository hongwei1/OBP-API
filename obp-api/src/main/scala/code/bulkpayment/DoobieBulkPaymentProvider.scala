package code.bulkpayment

import code.api.util.DoobieUtil
import com.openbankproject.commons.ExecutionContext.Implicits.global
import doobie._
import doobie.implicits._
import net.liftweb.common.Box
import net.liftweb.util.Helpers.tryo

object DoobieBulkPaymentProvider extends BulkPaymentProvider {

  // Table "bulkpayment" (Lift dbTableName = "BulkPayment", lowercased → bulkpayment).
  // Columns: id (BIGINT identity), transactionrequestid, transactionid, routingscheme, endtoendid,
  // failurereason (nullable), description, address, itemindex, currency, amount, status.
  // failurereason/transactionid are the only nullable columns (Mapped dbNotNull_? = false); all other
  // String columns are required. The Lift entity stays in Boot.ToSchemify.models during coexistence.
  private case class BulkPaymentRow(
    transactionrequestid: Option[String],
    itemindex: Option[Int],
    endtoendid: Option[String],
    routingscheme: Option[String],
    address: Option[String],
    currency: Option[String],
    amount: Option[String],
    description: Option[String],
    status: Option[String],
    failurereason: Option[String],
    transactionid: Option[String]
  )

  private case class DoobieBulkPayment(row: BulkPaymentRow) extends BulkPaymentTrait {
    override def transactionRequestId: String = row.transactionrequestid.getOrElse("")
    override def itemIndex: Int = row.itemindex.getOrElse(0)
    override def endToEndId: String = row.endtoendid.getOrElse("")
    override def routingScheme: String = row.routingscheme.getOrElse("")
    override def address: String = row.address.getOrElse("")
    override def currency: String = row.currency.getOrElse("")
    override def amount: String = row.amount.getOrElse("")
    override def description: String = row.description.getOrElse("")
    override def status: String = row.status.getOrElse("")
    // Mapped exposed Option(FailureReason.get) / Option(TransactionId.get): None when the column is NULL.
    override def failureReason: Option[String] = row.failurereason
    override def transactionId: Option[String] = row.transactionid
  }

  private def nn(s: String): String = if (s == null) "" else s

  private val selectCols: Fragment =
    fr"""SELECT transactionrequestid, itemindex, endtoendid, routingscheme, address, currency, amount,
                description, status, failurereason, transactionid
         FROM bulkpayment"""

  override def createBulkPayment(
    transactionRequestId: String,
    itemIndex: Int,
    endToEndId: String,
    routingScheme: String,
    address: String,
    currency: String,
    amount: String,
    description: String,
    status: String,
    failureReason: Option[String],
    transactionId: Option[String]
  ): Box[BulkPaymentTrait] = tryo {
    // Mapped wrote failureReason.orNull / transactionId.orNull → keep Option to preserve NULL semantics.
    DoobieUtil.runQuery(
      sql"""INSERT INTO bulkpayment
              (transactionrequestid, itemindex, endtoendid, routingscheme, address, currency, amount,
               description, status, failurereason, transactionid)
            VALUES (${nn(transactionRequestId)}, $itemIndex, ${nn(endToEndId)}, ${nn(routingScheme)},
                    ${nn(address)}, ${nn(currency)}, ${nn(amount)}, ${nn(description)}, ${nn(status)},
                    $failureReason, $transactionId)""".update.run)
    DoobieBulkPayment(BulkPaymentRow(
      Some(nn(transactionRequestId)), Some(itemIndex), Some(nn(endToEndId)), Some(nn(routingScheme)),
      Some(nn(address)), Some(nn(currency)), Some(nn(amount)), Some(nn(description)), Some(nn(status)),
      failureReason, transactionId))
  }

  override def getBulkPaymentsForTransactionRequest(transactionRequestId: String): List[BulkPaymentTrait] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE transactionrequestid = ${nn(transactionRequestId)} ORDER BY itemindex ASC")
        .query[BulkPaymentRow].to[List]).map(DoobieBulkPayment(_))

  override def isBatchReferenceUsed(fromBankId: String, fromAccountId: String, batchReference: String): Boolean =
    DoobieUtil.runQuery(
      sql"""SELECT COUNT(*) FROM bulkbatchreference
            WHERE frombankid = ${nn(fromBankId)} AND fromaccountid = ${nn(fromAccountId)}
              AND batchreference = ${nn(batchReference)}""".query[Long].unique) > 0

  override def claimBatchReference(fromBankId: String, fromAccountId: String, batchReference: String,
                                   transactionRequestId: String): Box[Unit] =
    tryo {
      // Mapped saveMe() on the UniqueIndex(frombankid, fromaccountid, batchreference) row; a duplicate
      // throws and tryo converts it to Failure, matching the Mapped Box[Unit] success/failure semantics.
      DoobieUtil.runQuery(
        sql"""INSERT INTO bulkbatchreference (frombankid, fromaccountid, batchreference, transactionrequestid)
              VALUES (${nn(fromBankId)}, ${nn(fromAccountId)}, ${nn(batchReference)}, ${nn(transactionRequestId)})""".update.run)
      ()
    }
}
