package code.kycchecks

import code.api.util.{APIUtil, DoobieUtil}
import com.openbankproject.commons.model.KycCheck
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}
import net.liftweb.util.Helpers.tryo

import java.sql.Timestamp
import java.util.Date
import scala.collection.immutable.List

object DoobieKycChecksProvider extends KycCheckProvider {

  private case class KycCheckRow(
    id: Option[Long],
    user_c: Option[Long],
    mbankid: Option[String],
    mcustomerid: Option[String],
    mid: Option[String],
    mcustomernumber: Option[String],
    mdate: Option[Timestamp],
    mhow: Option[String],
    mstaffuserid: Option[String],
    mstaffname: Option[String],
    msatisfied: Option[Boolean],
    mcomments: Option[String],
    createdat: Option[Timestamp],
    updatedat: Option[Timestamp]
  )

  private case class DoobieKycCheck(row: KycCheckRow) extends KycCheck {
    override def bankId: String = row.mbankid.getOrElse("")
    override def customerId: String = row.mcustomerid.getOrElse("")
    override def idKycCheck: String = row.mid.getOrElse("")
    override def customerNumber: String = row.mcustomernumber.getOrElse("")
    override def date: Date = row.mdate.map(ts => new Date(ts.getTime)).getOrElse(new Date())
    override def how: String = row.mhow.getOrElse("")
    override def staffUserId: String = row.mstaffuserid.getOrElse("")
    override def staffName: String = row.mstaffname.getOrElse("")
    override def satisfied: Boolean = row.msatisfied.getOrElse(false)
    override def comments: String = row.mcomments.getOrElse("")
  }

  private def nn(s: String): String = if (s == null) "" else s

  private val selectCols: Fragment =
    fr"SELECT id, user_c, mbankid, mcustomerid, mid, mcustomernumber, mdate, mhow, mstaffuserid, mstaffname, msatisfied, mcomments, createdat, updatedat FROM mappedkyccheck"

  override def getKycChecks(customerId: String): List[KycCheck] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"WHERE mcustomerid = ${nn(customerId)} ORDER BY updatedat DESC")
        .query[KycCheckRow].to[List]).map(DoobieKycCheck(_))

  override def addKycChecks(bankId: String, customerId: String, id: String, customerNumber: String, date: Date, how: String, staffUserId: String, mStaffName: String, mSatisfied: Boolean, comments: String): Box[KycCheck] =
    tryo {
      val dateTs = new Timestamp(date.getTime)
      val now = new Timestamp(System.currentTimeMillis())
      
      val existing = DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE mid = ${nn(id)} LIMIT 1")
          .query[KycCheckRow].option)

      existing match {
        case Some(row) =>
          // Update existing
          DoobieUtil.runQuery(
            sql"""UPDATE mappedkyccheck
                  SET mbankid = ${nn(bankId)}, mcustomerid = ${nn(customerId)}, mcustomernumber = ${nn(customerNumber)},
                      mdate = $dateTs, mhow = ${nn(how)}, mstaffuserid = ${nn(staffUserId)},
                      mstaffname = ${nn(mStaffName)}, msatisfied = $mSatisfied, mcomments = ${nn(comments)},
                      updatedat = $now
                  WHERE mid = ${nn(id)}""".update.run)
          DoobieKycCheck(row.copy(
            mbankid = Some(nn(bankId)),
            mcustomerid = Some(nn(customerId)),
            mcustomernumber = Some(nn(customerNumber)),
            mdate = Some(dateTs),
            mhow = Some(nn(how)),
            mstaffuserid = Some(nn(staffUserId)),
            mstaffname = Some(nn(mStaffName)),
            msatisfied = Some(mSatisfied),
            mcomments = Some(nn(comments)),
            updatedat = Some(now)
          ))
        case None =>
          // Insert new
          DoobieUtil.runQuery(
            sql"""INSERT INTO mappedkyccheck
                  (user_c, mbankid, mcustomerid, mid, mcustomernumber, mdate, mhow, mstaffuserid, mstaffname, msatisfied, mcomments, createdat, updatedat)
                  VALUES (null, ${nn(bankId)}, ${nn(customerId)}, ${nn(id)}, ${nn(customerNumber)}, $dateTs, ${nn(how)},
                          ${nn(staffUserId)}, ${nn(mStaffName)}, $mSatisfied, ${nn(comments)}, $now, $now)""".update.run)
          DoobieKycCheck(KycCheckRow(
            id = None,
            user_c = None,
            mbankid = Some(nn(bankId)),
            mcustomerid = Some(nn(customerId)),
            mid = Some(nn(id)),
            mcustomernumber = Some(nn(customerNumber)),
            mdate = Some(dateTs),
            mhow = Some(nn(how)),
            mstaffuserid = Some(nn(staffUserId)),
            mstaffname = Some(nn(mStaffName)),
            msatisfied = Some(mSatisfied),
            mcomments = Some(nn(comments)),
            createdat = Some(now),
            updatedat = Some(now)
          ))
      }
    }
}
