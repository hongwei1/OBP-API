package code.scheduler

import code.util.MappedUUID
import net.liftweb.common.Box
import net.liftweb.mapper._
import net.liftweb.util.Helpers.tryo

class JobScheduler extends JobSchedulerTrait with LongKeyedMapper[JobScheduler] with IdPK with CreatedUpdated {

  def getSingleton = JobScheduler

  object JobId extends MappedUUID(this)
  object Name extends MappedString(this, 100)
  object ApiInstanceId extends MappedString(this, 100)

  override def primaryKey: Long = id.get
  override def jobId: String = JobId.get
  override def name: String = Name.get
  override def apiInstanceId: String = ApiInstanceId.get

}

object JobScheduler extends JobScheduler with LongKeyedMetaMapper[JobScheduler] {
  /**
   * `UniqueIndex(Name)` is what makes this table an actual lock.
   *
   * A job takes the lock by INSERTing a row named after itself, so with only
   * `UniqueIndex(JobId)` (every insert generates a fresh UUID, so it never collides)
   * "find no row, then create one" was a plain check-then-act race: two JVMs — or two
   * threads in one JVM — can both see no row and both insert, and both then believe they
   * hold the lock and run the job concurrently. Single-replica deployments never noticed;
   * a second replica turns it into duplicated work on the same rows.
   *
   * With the index the database arbitrates: exactly one INSERT for a given job name wins
   * and every other one is rejected, which is precisely the "somebody else got there
   * first" signal `tryAcquire` reports.
   */
  override def dbIndexes: List[BaseIndex[JobScheduler]] =
    UniqueIndex(JobId) :: UniqueIndex(Name) :: super.dbIndexes

  /**
   * Take the lock for `jobName`, or report that somebody else holds it.
   *
   * `Full(job)` means this caller owns the lock and MUST delete the returned row when the
   * job finishes (the schedulers do this in a `finally`). Anything else means the caller
   * must not run the job.
   *
   * The insert is wrapped rather than left to throw because losing the race is an expected
   * outcome here, not an error: `UniqueIndex(Name)` rejecting the INSERT IS the mutual
   * exclusion, and the rejection has to read as a verdict, not as a crashed scheduler tick.
   *
   * Caveat for callers that run inside an HTTP request transaction (PostgreSQL): a rejected
   * INSERT leaves that transaction aborted, so any statement issued afterwards within the
   * same transaction fails too. Such callers should read the current lock holder BEFORE
   * attempting to acquire — `MetricsArchiveScheduler.runOnce` does — and treat a read after
   * a failed acquire as best-effort.
   */
  def tryAcquire(jobName: String, apiInstanceId: String, jobId: String): Box[JobScheduler] =
    tryo {
      JobScheduler.create
        .JobId(jobId)
        .Name(jobName)
        .ApiInstanceId(apiInstanceId)
        .saveMe()
    }

  /**
   * The most recent scheduler-lock rows, newest first, capped at `limit`.
   *
   * Note: `jobscheduler` is a lock table, not a job-history log — a row exists
   * only while a job holds the lock and is deleted when the job finishes. In
   * healthy operation this returns an empty list; any rows present are either
   * currently running or stale locks left by a dead JVM.
   */
  def mostRecent(limit: Int): List[JobScheduler] =
    findAll(OrderBy(JobScheduler.createdAt, Descending), MaxRows(limit))

  /** Delete the lock row with the given JobId; returns true if a row was removed. */
  def deleteByJobId(jobId: String): Boolean =
    find(By(JobScheduler.JobId, jobId)) match {
      case net.liftweb.common.Full(job) => delete_!(job)
      case _                            => false
    }
}
