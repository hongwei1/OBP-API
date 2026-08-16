package code.scheduler

import code.api.berlin.group.v1_3.model.TransactionStatus
import code.api.util.{APIUtil, DoobieUtil}
import code.util.Helper.MdcLoggable
import doobie._
import doobie.implicits._

import java.sql.Timestamp
import scala.util.{Failure, Success, Try}


object TransactionScheduler extends MdcLoggable {

  private case class TransactionRow(id: Long, mStatus: String)

  // Starts multiple scheduled tasks with different intervals
  def startAll(): Unit = {
    var initialDelay = 0

    // Berlin Group
    APIUtil.getPropsAsIntValue("berlin_group_outdated_transactions_interval_in_seconds") match {
      case net.liftweb.common.Full(interval) if interval > 0 =>
        val time = APIUtil.getPropsAsIntValue("berlin_group_outdated_transactions_time_in_seconds", 300)
        SchedulerUtil.startTask(interval = interval, () => outdatedBerlinGroupTransactions(time)) // Runs periodically
        initialDelay = initialDelay + 10
      case _ =>
        logger.warn("|---> Skipping outdatedBerlinGroupTransactions task: berlin_group_outdated_transactions_interval_in_seconds not set or invalid")
    }
  }

  private def outdatedBerlinGroupTransactions(seconds: Int): Unit = {
    Try {
      logger.debug("|---> Checking for OUTDATED Berlin Group TRANSACTIONS...")

      val cutoff = new Timestamp(SchedulerUtil.someSecondsAgo(seconds).getTime)
      val rcvdStatus = TransactionStatus.RCVD.toString

      val outdatedTransactions: List[TransactionRow] = DoobieUtil.runQuery(
        fr"""SELECT id, mstatus FROM mappedtransactionrequest
             WHERE mstatus = $rcvdStatus AND updatedat < $cutoff"""
          .query[TransactionRow].to[List]
      )

      logger.debug(s"|---> Found ${outdatedTransactions.size} outdated transactions")

      val rjctStatus = TransactionStatus.RJCT.toString
      outdatedTransactions.foreach { transaction =>
        Try {
          val now = new Timestamp(System.currentTimeMillis())
          DoobieUtil.runQuery(
            sql"""UPDATE mappedtransactionrequest
                  SET mstatus = $rjctStatus, updatedat = $now
                  WHERE id = ${transaction.id}""".update.run
          )
          logger.warn(s"|---> Changed status to $rjctStatus for transaction ID: ${transaction.id}")
        } match {
          case Failure(ex) => logger.error(s"Failed to update transaction ID: ${transaction.id}", ex)
          case Success(_) => // Already logged
        }
      }
    } match {
      case Failure(ex) => logger.error("Error in outdatedBerlinGroupTransactions!", ex)
      case Success(_) => logger.debug("|---> Task executed successfully")
    }
  }

}
