package code.kycmedias

import java.sql.Timestamp
import java.util.Date

import code.api.util.DoobieUtil
import com.openbankproject.commons.model.KycMedia
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Full}
import net.liftweb.util.Helpers.tryo

object DoobieKycMediasProvider extends KycMediaProvider {

  private case class KycMediaRow(
    id: Option[Long],
    mbankid: Option[String],
    mcustomernumber: Option[String],
    createdat: Option[Timestamp],
    updatedat: Option[Timestamp],
    mid: Option[String],
    mcustomerid: Option[String],
    mtype: Option[String],
    murl: Option[String],
    mdate: Option[Timestamp],
    mrelatestokycdocumentid: Option[String],
    mrelatestokyccheckid: Option[String]
  )

  private case class DoobieKycMedia(row: KycMediaRow) extends KycMedia {
    override def bankId: String = row.mbankid.getOrElse("")
    override def customerId: String = row.mcustomerid.getOrElse("")
    override def idKycMedia: String = row.mid.getOrElse("")
    override def customerNumber: String = row.mcustomernumber.getOrElse("")
    override def `type`: String = row.mtype.getOrElse("")
    override def url: String = row.murl.getOrElse("")
    override def date: Date = row.mdate.map(ts => new Date(ts.getTime)).getOrElse(new Date())
    override def relatesToKycDocumentId: String = row.mrelatestokycdocumentid.getOrElse("")
    override def relatesToKycCheckId: String = row.mrelatestokyccheckid.getOrElse("")
  }

  private def nn(s: String): String = if (s == null) "" else s

  private val selectCols: Fragment =
    fr"SELECT id, mbankid, mcustomernumber, createdat, updatedat, mid, mcustomerid, mtype, murl, mdate, mrelatestokycdocumentid, mrelatestokyccheckid FROM mappedkycmedia"

  override def getKycMedias(customerId: String): List[KycMedia] =
    tryo {
      DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE mcustomerid = ${nn(customerId)} ORDER BY updatedat DESC")
          .query[KycMediaRow].to[List]).map(DoobieKycMedia(_))
    }.openOr(List())

  override def addKycMedias(bankId: String, customerId: String, id: String, customerNumber: String, `type`: String, url: String, date: Date, relatesToKycDocumentId: String, relatesToKycCheckId: String): Box[KycMedia] =
    tryo {
      val existing = DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE mid = ${nn(id)} LIMIT 1")
          .query[KycMediaRow].option)
      val now = new Timestamp(System.currentTimeMillis())
      existing match {
        case Some(row) =>
          DoobieUtil.runQuery(
            sql"""UPDATE mappedkycmedia
                  SET mbankid = ${nn(bankId)}, mcustomerid = ${nn(customerId)},
                      mcustomernumber = ${nn(customerNumber)}, mtype = ${nn(`type`)},
                      murl = ${nn(url)}, mdate = ${new Timestamp(date.getTime)},
                      mrelatestokycdocumentid = ${nn(relatesToKycDocumentId)},
                      mrelatestokyccheckid = ${nn(relatesToKycCheckId)}, updatedat = $now
                  WHERE mid = ${nn(id)}""".update.run)
          DoobieKycMedia(row.copy(
            mbankid = Some(nn(bankId)),
            mcustomerid = Some(nn(customerId)),
            mcustomernumber = Some(nn(customerNumber)),
            mtype = Some(nn(`type`)),
            murl = Some(nn(url)),
            mdate = Some(new Timestamp(date.getTime)),
            mrelatestokycdocumentid = Some(nn(relatesToKycDocumentId)),
            mrelatestokyccheckid = Some(nn(relatesToKycCheckId)),
            updatedat = Some(now)
          ))
        case None =>
          DoobieUtil.runQuery(
            sql"""INSERT INTO mappedkycmedia
                  (mbankid, mcustomerid, mcustomernumber, mtype, murl, mdate, mrelatestokycdocumentid, mrelatestokyccheckid, createdat, updatedat, mid)
                  VALUES (${nn(bankId)}, ${nn(customerId)}, ${nn(customerNumber)}, ${nn(`type`)},
                          ${nn(url)}, ${new Timestamp(date.getTime)}, ${nn(relatesToKycDocumentId)}, ${nn(relatesToKycCheckId)}, $now, $now, ${nn(id)})""".update.run)
          DoobieKycMedia(KycMediaRow(
            None,
            Some(nn(bankId)),
            Some(nn(customerNumber)),
            Some(now),
            Some(now),
            Some(nn(id)),
            Some(nn(customerId)),
            Some(nn(`type`)),
            Some(nn(url)),
            Some(new Timestamp(date.getTime)),
            Some(nn(relatesToKycDocumentId)),
            Some(nn(relatesToKycCheckId))
          ))
      }
    }
}
