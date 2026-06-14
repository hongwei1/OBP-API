package code.scheduler

import code.actorsystem.ObpActorSystem
import code.api.Constant
import code.api.util.{APIUtil, DoobieUtil}
import code.nonce.Nonces
import code.token.Tokens
import code.util.Helper.MdcLoggable
import doobie._
import doobie.implicits._

import java.sql.Timestamp
import java.util.concurrent.TimeUnit
import java.util.Date
import scala.concurrent.duration._


object DataBaseCleanerScheduler extends MdcLoggable {

  private lazy val actorSystem = ObpActorSystem.localActorSystem
  implicit lazy val executor = actorSystem.dispatcher
  private lazy val scheduler = actorSystem.scheduler
  private val oneDayInMillis: Long = 86400000
  //in scala DataBaseCleanerScheduler.getClass.getSimpleName ==> DataBaseCleanerScheduler$
  private val jobName = DataBaseCleanerScheduler.getClass.getSimpleName.replace("$", "")
  private val apiInstanceId = Constant.ApiInstanceId

  private case class JobRow(id: Long, jobId: String)

  def start(intervalInSeconds: Long): Unit = {
    logger.info(s"Hello from $jobName.start")

    logger.info(s"--------- Clean up Jobs ---------")
    logger.info(s"Delete all Jobs created by api_instance_id=$apiInstanceId")
    // Preserve original query (By Name = apiInstanceId) for bug-for-bug compatibility.
    DoobieUtil.runQuery(
      sql"DELETE FROM jobscheduler WHERE name = $apiInstanceId".update.run
    )

    logger.info(s"Delete all Jobs older than 5 days")
    val fiveDaysAgo: Date = new Date(new Date().getTime - (oneDayInMillis * 5))
    val fiveDaysAgoTs = new Timestamp(fiveDaysAgo.getTime)
    DoobieUtil.runQuery(
      sql"DELETE FROM jobscheduler WHERE createdat <= $fiveDaysAgoTs".update.run
    )

    scheduler.schedule(
      initialDelay = Duration(intervalInSeconds, TimeUnit.SECONDS),
      interval = Duration(intervalInSeconds, TimeUnit.SECONDS),
      runnable = new Runnable {
        def run(): Unit = {
          DoobieUtil.runQuery(
            fr"SELECT id, jobid FROM jobscheduler WHERE name = $jobName LIMIT 1"
              .query[JobRow].option
          ) match {
            case Some(job) =>
              logger.info(s"Cannot start $jobName.start.run due to ongoing job. Job ID: ${job.jobId}")
            case None =>
              val uniqueId = APIUtil.generateUUID()
              val now = new Timestamp(System.currentTimeMillis())
              val insertedId: Long = DoobieUtil.runQuery(
                sql"""INSERT INTO jobscheduler (jobid, name, apiinstanceid, createdat, updatedat)
                      VALUES ($uniqueId, $jobName, $apiInstanceId, $now, $now)"""
                  .update.withUniqueGeneratedKeys[Long]("id")
              )
              logger.info(s"Starting $jobName.Job ID: $uniqueId")
              deleteExpiredTokensAndNonces()
              DoobieUtil.runQuery(sql"DELETE FROM jobscheduler WHERE id = $insertedId".update.run)
              logger.info(s"End of $jobName.Job ID: $uniqueId")
          }
        }
      }
    )
    logger.info(s"Bye from $jobName.start")
  }

  def deleteExpiredTokensAndNonces() = {
    //looks for expired tokens and nonces and deletes them
    val currentDate = new Date()
    //delete expired tokens and nonces
    Tokens.tokens.vend.deleteExpiredTokens(currentDate)
    Nonces.nonces.vend.deleteExpiredNonces(currentDate)
  }


}
