package code.metrics

import java.sql.Timestamp
import java.util.Date

import code.api.util.DoobieUtil
import code.util.MappedUUID
import doobie._
import doobie.implicits._
import net.liftweb.mapper._

/**
 * Result row read from / written to the `metricsarchiverun` table via Doobie.
 *
 * Table columns (created by Lift Schemifier, kept for backward compat):
 *   id, runid, apiinstanceid, startedat, endedat, durationms,
 *   rowsmovedtoarchive, rowsdeletedfromarchive, success, remark
 */
case class MetricsArchiveRunRow(
  runId: String,
  apiInstanceId: String,
  startedAt: Timestamp,
  endedAt: Timestamp,
  durationMs: Long,
  rowsMovedToArchive: Int,
  rowsDeletedFromArchive: Int,
  success: Boolean,
  remark: String
)

/**
 * Append-only audit log of `MetricsArchiveScheduler` runs.
 *
 * One row is written at the end of every scheduler tick that actually did work
 * (i.e. was not skipped because a previous run was still in progress). It records
 * how many rows were moved `metric` -> `metricarchive`, how many outdated archive
 * rows were deleted, the wall-clock duration, and whether the run succeeded.
 *
 * This is durable history. Contrast with the `jobscheduler` table, which holds
 * only a transient lock row that exists during a run and is deleted on completion.
 *
 * The log is self-capped: only the most recent [[MetricsArchiveRun.maxRowsToKeep]]
 * rows are retained. Each write prunes anything older, so the table stays small.
 *
 * Naming: this is a DB entity, so the class must not start with `Mapped` and the
 * column objects must not start with `m` + uppercase (see `MappedClassNameTest`).
 */
class MetricsArchiveRun extends LongKeyedMapper[MetricsArchiveRun] with IdPK {

  def getSingleton = MetricsArchiveRun

  object RunId extends MappedUUID(this)
  object ApiInstanceId extends MappedString(this, 100)
  object StartedAt extends MappedDateTime(this)
  object EndedAt extends MappedDateTime(this)
  object DurationMs extends MappedLong(this)
  object RowsMovedToArchive extends MappedInt(this)
  object RowsDeletedFromArchive extends MappedInt(this)
  object Success extends MappedBoolean(this)
  object Remark extends MappedText(this)
}

object MetricsArchiveRun extends MetricsArchiveRun with LongKeyedMetaMapper[MetricsArchiveRun] {

  override def dbIndexes: List[BaseIndex[MetricsArchiveRun]] =
    UniqueIndex(RunId) :: Index(StartedAt) :: super.dbIndexes

  /** Keep only the most recent N runs; older rows are pruned on every write. */
  val maxRowsToKeep: Int = 1000

  /**
   * Persist one completed run, then prune the log back to the most recent
   * [[maxRowsToKeep]] rows. The scheduler's own retention applies to `metric` /
   * `metricarchive`; this cap keeps the run log itself bounded.
   */
  def recordRun(runId: String,
                apiInstanceId: String,
                startedAt: Date,
                endedAt: Date,
                rowsMovedToArchive: Int,
                rowsDeletedFromArchive: Int,
                success: Boolean,
                remark: Option[String]): MetricsArchiveRunRow = {
    val startedAtTs = new Timestamp(startedAt.getTime)
    val endedAtTs   = new Timestamp(endedAt.getTime)
    val durationMs  = endedAt.getTime - startedAt.getTime
    val remarkStr   = remark.getOrElse("")
    DoobieUtil.runQuery(
      sql"""INSERT INTO metricsarchiverun
              (runid, apiinstanceid, startedat, endedat, durationms,
               rowsmovedtoarchive, rowsdeletedfromarchive, success, remark)
            VALUES
              ($runId, $apiInstanceId, $startedAtTs, $endedAtTs, $durationMs,
               $rowsMovedToArchive, $rowsDeletedFromArchive, $success, $remarkStr)"""
        .update.run
    )
    pruneToMostRecent(maxRowsToKeep)
    MetricsArchiveRunRow(runId, apiInstanceId, startedAtTs, endedAtTs,
      durationMs, rowsMovedToArchive, rowsDeletedFromArchive, success, remarkStr)
  }

  /**
   * Delete all but the most recent `keep` rows (by primary key, which is
   * monotonic). No-op when the table holds `keep` or fewer rows.
   */
  def pruneToMostRecent(keep: Int): Unit = {
    val ids = DoobieUtil.runQuery(
      fr"SELECT id FROM metricsarchiverun ORDER BY id DESC LIMIT $keep"
        .query[Long].to[List]
    )
    ids.lastOption.foreach { minId =>
      DoobieUtil.runQuery(
        sql"DELETE FROM metricsarchiverun WHERE id < $minId".update.run
      )
    }
  }

  /** Most recent run by start time, if any. */
  def lastRun: Option[MetricsArchiveRunRow] = DoobieUtil.runQuery(
    fr"""SELECT runid, apiinstanceid, startedat, endedat, durationms,
                rowsmovedtoarchive, rowsdeletedfromarchive, success, remark
         FROM metricsarchiverun ORDER BY startedat DESC LIMIT 1"""
      .query[MetricsArchiveRunRow].option
  )

  /** Most recent successful run by start time, if any. */
  def lastSuccessfulRun: Option[MetricsArchiveRunRow] = DoobieUtil.runQuery(
    fr"""SELECT runid, apiinstanceid, startedat, endedat, durationms,
                rowsmovedtoarchive, rowsdeletedfromarchive, success, remark
         FROM metricsarchiverun WHERE success = true ORDER BY startedat DESC LIMIT 1"""
      .query[MetricsArchiveRunRow].option
  )
}
