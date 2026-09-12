package code.api.util.http4s

import cats.effect.IO
import code.api.util.APIUtil
import com.openbankproject.commons.util.ScannedApiVersion
import org.http4s.HttpRoutes

/**
 * Whole-version gates for the http4s request chain: `api_disabled_versions` /
 * `api_enabled_versions` decide, once at object-init time, whether a standard's entire route tree
 * is wired in at all. The per-endpoint checks (`api_disabled_endpoints` / `api_enabled_endpoints`)
 * are finer-grained and still run inside ResourceDocMiddleware.
 *
 * Deliberately a small object of its own rather than a private method on `Http4sApp`: touching
 * `Http4sApp` initialises every version's routes and needs a database, so a decision living there
 * could only be exercised by a full server-backed suite. Here it is a pure function.
 */
object VersionGate {

  /**
   * Wire `routes` in only when `version` is allowed.
   *
   * `routes` is BY NAME, and that is the point. It used to be by value, so a version excluded by
   * `api_disabled_versions` still had its whole object initialised — every ResourceDoc registered,
   * every route built — and the result then thrown away. Disabling a version cost exactly as much
   * boot work as keeping it; by name, an excluded version is never touched.
   */
  def apply(version: ScannedApiVersion, routes: => HttpRoutes[IO]): HttpRoutes[IO] =
    when(APIUtil.versionIsAllowed(version), routes)

  /** The decision itself, taking the verdict rather than looking it up, so both halves of it —
    * empty when excluded, and never forcing the excluded routes — are directly testable. */
  def when(allowed: Boolean, routes: => HttpRoutes[IO]): HttpRoutes[IO] =
    if (allowed) routes else HttpRoutes.empty[IO]
}
