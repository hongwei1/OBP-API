package code.kycstatuses

import java.sql.Timestamp
import java.util.Date

import code.api.util.DoobieUtil
import com.openbankproject.commons.model.KycStatus
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Full}
import net.liftweb.util.Helpers.tryo

object DoobieKycStatusesProvider extends KycStatusProvider {

  private case class KycStatusRow(
    id: Option[Long],
    user_c: Option[Long],
    mbankid: Option[String],
    mcustomerid: Option[String],
    mcustomernumber: Option[String],
    mok: Option[Boolean],
    mdate: Option[Timestamp],
    createdat: Option[Timestamp],
    updatedat: Option[Timestamp]
  )

  private case class DoobieKycStatus(row: KycStatusRow) extends KycStatus {
    override def bankId: String = row.mbankid.getOrElse("")
    override def customerId: String = row.mcustomerid.getOrElse("")
    override def customerNumber: String = row.mcustomernumber.getOrElse("")
    override def ok: Boolean = row.mok.getOrElse(false)
    override def date: Date = row.mdate.map(ts => new Date(ts.getTime)).getOrElse(new Date())
  }

  private def nn(s: String): String = if (s == null) "" else s

  private val selectCols: Fragment =
    fr"SELECT id, user_c, mbankid, mcustomerid, mcustomernumber, mok, mdate, createdat, updatedat FROM mappedkycstatus"

  override def getKycStatuses(customerId: String): List[KycStatus] = {
    tryo {
      DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE mcustomerid = ${nn(customerId)} ORDER BY updatedat DESC")
          .query[KycStatusRow].to[List]).map(DoobieKycStatus(_))
    }.getOrElse(List())
  }

  override def addKycStatus(bankId: String, customerId: String, customerNumber: String, ok: Boolean, date: Date): Box[KycStatus] = {
    tryo {
      val timestamp = new Timestamp(date.getTime)
      val now = new Timestamp(System.currentTimeMillis())

      // Check if record exists
      val existingOpt = DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE mbankid = ${nn(bankId)} AND mcustomerid = ${nn(customerId)} LIMIT 1")
          .query[KycStatusRow].option)

      existingOpt match {
        case Some(existing) =>
          // Update existing record
          DoobieUtil.runQuery(
            sql"""UPDATE mappedkycstatus
                   SET mcustomernumber = ${nn(customerNumber)}, mok = $ok, mdate = $timestamp, updatedat = $now
                   WHERE mbankid = ${nn(bankId)} AND mcustomerid = ${nn(customerId)}""".update.run)
          DoobieKycStatus(existing.copy(
            mcustomernumber = Some(nn(customerNumber)),
            mok = Some(ok),
            mdate = Some(timestamp),
            updatedat = Some(now)
          ))
        case None =>
          // Insert new record
          DoobieUtil.runQuery(
            sql"""INSERT INTO mappedkycstatus
                   (mbankid, mcustomerid, mcustomernumber, mok, mdate, createdat, updatedat)
                   VALUES (${nn(bankId)}, ${nn(customerId)}, ${nn(customerNumber)}, $ok, $timestamp, $now, $now)""".update.run)
          DoobieKycStatus(KycStatusRow(
            id = None,
            user_c = None,
            mbankid = Some(nn(bankId)),
            mcustomerid = Some(nn(customerId)),
            mcustomernumber = Some(nn(customerNumber)),
            mok = Some(ok),
            mdate = Some(timestamp),
            createdat = Some(now),
            updatedat = Some(now)
          ))
      }
    }
  }
}
