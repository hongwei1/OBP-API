package code.migration

import code.api.util.DoobieUtil
import doobie._
import doobie.implicits._
import net.liftweb.util.Helpers.tryo

import java.sql.Timestamp

object DoobieMigrationScriptLogProvider extends MigrationScriptLogProvider {

  // Table: migrationscriptlog. UniqueIndex(Name, IsSuccessful) → one success + one failure row per name.
  private case class LogRow(
    migrationscriptlogid: Option[String],
    name: Option[String],
    commitid: Option[String],
    issuccessful: Option[Boolean],
    startdate: Option[Long],
    enddate: Option[Long],
    remark: Option[String]
  )

  private case class DoobieLogEntry(row: LogRow) extends MigrationScriptLogTrait {
    override def primaryKey: Long = 0L
    override def migrationScriptLogId: String = row.migrationscriptlogid.getOrElse("")
    override def name: String = row.name.getOrElse("")
    override def commitId: String = row.commitid.getOrElse("")
    override def isSuccessful: Boolean = row.issuccessful.getOrElse(false)
    override def startDate: Long = row.startdate.getOrElse(0L)
    override def endDate: Long = row.enddate.getOrElse(0L)
    override def remark: String = row.remark.getOrElse("")
  }

  private def nn(s: String): String = if (s == null) "" else s

  private val selectCols: Fragment =
    fr"SELECT migrationscriptlogid, name, commitid, issuccessful, startdate, enddate, remark FROM migrationscriptlog"

  override def saveLog(name: String, commitId: String, isSuccessful: Boolean,
                       startDate: Long, endDate: Long, comment: String): Boolean =
    tryo {
      val now = new Timestamp(System.currentTimeMillis())
      val existing = DoobieUtil.runQuery(
        fr"SELECT migrationscriptlogid FROM migrationscriptlog WHERE name = ${nn(name)} AND issuccessful = $isSuccessful LIMIT 1"
          .query[String].option)
      existing match {
        case Some(existingId) =>
          DoobieUtil.runQuery(
            sql"""UPDATE migrationscriptlog
                  SET commitid = ${nn(commitId)}, startdate = $startDate, enddate = $endDate,
                      remark = ${nn(comment)}, updatedat = $now
                  WHERE migrationscriptlogid = $existingId""".update.run) > 0
        case None =>
          val newId = java.util.UUID.randomUUID().toString
          DoobieUtil.runQuery(
            sql"""INSERT INTO migrationscriptlog
                    (migrationscriptlogid, name, commitid, issuccessful, startdate, enddate, remark, createdat, updatedat)
                  VALUES ($newId, ${nn(name)}, ${nn(commitId)}, $isSuccessful, $startDate, $endDate, ${nn(comment)}, $now, $now)""".update.run) > 0
      }
    }.getOrElse(false)

  override def isExecuted(name: String): Boolean =
    DoobieUtil.runQuery(
      fr"SELECT COUNT(*) FROM migrationscriptlog WHERE name = ${nn(name)} AND issuccessful = ${true}"
        .query[Long].unique) > 0

  override def getMigrationScriptLogs(): List[MigrationScriptLogTrait] =
    DoobieUtil.runQuery(
      (selectCols ++ fr"ORDER BY createdat DESC").query[LogRow].to[List])
      .map(DoobieLogEntry(_))
}
