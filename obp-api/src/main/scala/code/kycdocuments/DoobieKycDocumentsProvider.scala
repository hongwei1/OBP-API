package code.kycdocuments

import java.util.Date
import java.sql.Timestamp

import code.api.util.DoobieUtil
import com.openbankproject.commons.model.KycDocument
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Full}
import net.liftweb.util.Helpers.tryo

object DoobieKycDocumentsProvider extends KycDocumentProvider {

  private case class KycDocRow(
    id: Option[Long],
    user_c: Option[Long],
    mbankid: Option[String],
    mcustomerid: Option[String],
    mid: Option[String],
    mcustomernumber: Option[String],
    mtype: Option[String],
    mnumber: Option[String],
    missuedate: Option[Timestamp],
    missueplace: Option[String],
    mexpirydate: Option[Timestamp],
    createdat: Option[Timestamp],
    updatedat: Option[Timestamp]
  )

  private case class DoobieKycDocument(row: KycDocRow) extends KycDocument {
    override def bankId: String        = row.mbankid.getOrElse("")
    override def customerId: String    = row.mcustomerid.getOrElse("")
    override def idKycDocument: String = row.mid.getOrElse("")
    override def customerNumber: String = row.mcustomernumber.getOrElse("")
    override def `type`: String        = row.mtype.getOrElse("")
    override def number: String        = row.mnumber.getOrElse("")
    override def issueDate: Date       = row.missuedate.map(ts => new Date(ts.getTime)).getOrElse(new Date())
    override def issuePlace: String    = row.missueplace.getOrElse("")
    override def expiryDate: Date      = row.mexpirydate.map(ts => new Date(ts.getTime)).getOrElse(new Date())
  }

  private def nn(s: String): String = if (s == null) "" else s

  private val selectCols: Fragment =
    fr"SELECT id, user_c, mbankid, mcustomerid, mid, mcustomernumber, mtype, mnumber, missuedate, missueplace, mexpirydate, createdat, updatedat FROM mappedkycdocument"

  override def getKycDocuments(customerId: String): List[KycDocument] =
    tryo {
      DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE mcustomerid = ${nn(customerId)} ORDER BY updatedat DESC")
          .query[KycDocRow].to[List]).map(DoobieKycDocument(_))
    }.getOrElse(List())

  override def addKycDocuments(
    bankId: String,
    customerId: String,
    id: String,
    customerNumber: String,
    `type`: String,
    number: String,
    issueDate: Date,
    issuePlace: String,
    expiryDate: Date
  ): Box[KycDocument] = {
    tryo {
      val issueDateTs = if (issueDate != null) new Timestamp(issueDate.getTime) else null
      val expiryDateTs = if (expiryDate != null) new Timestamp(expiryDate.getTime) else null
      val now = new Timestamp(System.currentTimeMillis())

      // Check if document already exists
      val existing = DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE mid = ${nn(id)} LIMIT 1")
          .query[KycDocRow].option)

      existing match {
        case Some(row) =>
          // Update existing
          DoobieUtil.runQuery(
            sql"""UPDATE mappedkycdocument
                   SET mbankid = ${nn(bankId)}, mcustomerid = ${nn(customerId)}, 
                       mcustomernumber = ${nn(customerNumber)}, mtype = ${nn(`type`)},
                       mnumber = ${nn(number)}, missuedate = $issueDateTs, 
                       missueplace = ${nn(issuePlace)}, mexpirydate = $expiryDateTs,
                       updatedat = $now
                   WHERE mid = ${nn(id)}""".update.run)
          DoobieKycDocument(row.copy(
            mbankid = Some(nn(bankId)),
            mcustomerid = Some(nn(customerId)),
            mcustomernumber = Some(nn(customerNumber)),
            mtype = Some(nn(`type`)),
            mnumber = Some(nn(number)),
            missuedate = Option(issueDateTs),
            missueplace = Some(nn(issuePlace)),
            mexpirydate = Option(expiryDateTs),
            updatedat = Some(now)
          ))
        case None =>
          // Insert new
          DoobieUtil.runQuery(
            sql"""INSERT INTO mappedkycdocument
                   (mbankid, mcustomerid, mid, mcustomernumber, mtype, mnumber, 
                    missuedate, missueplace, mexpirydate, createdat, updatedat)
                   VALUES (${nn(bankId)}, ${nn(customerId)}, ${nn(id)}, ${nn(customerNumber)},
                           ${nn(`type`)}, ${nn(number)}, $issueDateTs, ${nn(issuePlace)},
                           $expiryDateTs, $now, $now)""".update.run)
          DoobieKycDocument(KycDocRow(
            id = None,
            user_c = None,
            mbankid = Some(nn(bankId)),
            mcustomerid = Some(nn(customerId)),
            mid = Some(nn(id)),
            mcustomernumber = Some(nn(customerNumber)),
            mtype = Some(nn(`type`)),
            mnumber = Some(nn(number)),
            missuedate = Option(issueDateTs),
            missueplace = Some(nn(issuePlace)),
            mexpirydate = Option(expiryDateTs),
            createdat = Some(now),
            updatedat = Some(now)
          ))
      }
    }
  }
}