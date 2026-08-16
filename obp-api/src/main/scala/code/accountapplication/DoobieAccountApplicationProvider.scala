package code.accountapplication

import code.api.util.{APIUtil, DoobieUtil}
import com.openbankproject.commons.ExecutionContext.Implicits.global
import com.openbankproject.commons.model.{AccountApplication, ProductCode}
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full, Failure}
import net.liftweb.util.Helpers.tryo

import java.sql.Timestamp
import java.util.Date
import scala.concurrent.Future

object DoobieAccountApplicationProvider extends AccountApplicationProvider {

  private case class AcctAppRow(
    id: Option[Long],
    maccountapplicationid: Option[String],
    mcode: Option[String],
    muserid: Option[String],
    mcustomerid: Option[String],
    mstatus: Option[String],
    createdat: Option[Timestamp],
    updatedat: Option[Timestamp]
  )

  private case class DoobieAccountApplication(row: AcctAppRow) extends AccountApplication {
    override def accountApplicationId: String = row.maccountapplicationid.getOrElse("")
    override def productCode: ProductCode     = ProductCode(row.mcode.getOrElse(""))
    override def userId: String               = row.muserid.getOrElse("")
    override def customerId: String           = row.mcustomerid.getOrElse("")
    override def dateOfApplication: Date      = row.createdat.map(ts => new Date(ts.getTime)).getOrElse(new Date())
    override def status: String                = row.mstatus.getOrElse("")
  }

  private def nn(s: String): String = if (s == null) "" else s

  private val selectCols: Fragment =
    fr"SELECT id, maccountapplicationid, mcode, muserid, mcustomerid, mstatus, createdat, updatedat FROM mappedaccountapplication"

  override def getAll(): Future[Box[List[AccountApplication]]] =
    Future {
      tryo {
        DoobieUtil.runQuery(
          selectCols
            .query[AcctAppRow].to[List]).map(DoobieAccountApplication(_))
      }
    }

  override def getById(accountApplicationId: String): Future[Box[AccountApplication]] =
    Future {
      DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE maccountapplicationid = ${nn(accountApplicationId)} LIMIT 1")
          .query[AcctAppRow].option).map(DoobieAccountApplication(_)) match {
        case Some(a) => Full(a)
        case None    => Empty
      }
    }

  override def createAccountApplication(
    productCode: ProductCode,
    userId: Option[String],
    customerId: Option[String]
  ): Future[Box[AccountApplication]] =
    Future {
      tryo {
        val newId = APIUtil.generateUUID()
        val now = new Timestamp(System.currentTimeMillis())
        DoobieUtil.runQuery(
          sql"""INSERT INTO mappedaccountapplication
                  (maccountapplicationid, mcode, muserid, mcustomerid, mstatus, createdat, updatedat)
                VALUES ($newId, ${nn(productCode.value)}, ${nn(userId.orNull)}, ${nn(customerId.orNull)}, 'REQUESTED', $now, $now)""".update.run)
        DoobieAccountApplication(AcctAppRow(
          None, Some(newId), Some(nn(productCode.value)),
          userId.map(nn), customerId.map(nn), Some("REQUESTED"), Some(now), Some(now)
        ))
      }
    }

  override def updateStatus(accountApplicationId: String, status: String): Future[Box[AccountApplication]] =
    Future {
      DoobieUtil.runQuery(
        (selectCols ++ fr"WHERE maccountapplicationid = ${nn(accountApplicationId)} LIMIT 1")
          .query[AcctAppRow].option) match {
        case Some(existing) if existing.mstatus.getOrElse("") == "ACCEPTED" =>
          Failure(s"${code.api.util.ErrorMessages.AccountApplicationAlreadyAccepted} Current Account-Application-Id($accountApplicationId)")
        case Some(existing) =>
          tryo {
            val now = new Timestamp(System.currentTimeMillis())
            DoobieUtil.runQuery(
              sql"""UPDATE mappedaccountapplication
                    SET mstatus = ${nn(status)}, updatedat = $now
                    WHERE maccountapplicationid = ${nn(accountApplicationId)}""".update.run)
            DoobieAccountApplication(existing.copy(mstatus = Some(nn(status)), updatedat = Some(now)))
          }
        case None =>
          Failure(s"${code.api.util.ErrorMessages.AccountApplicationNotFound} Current Account-Application-Id($accountApplicationId)")
      }
    }

}
