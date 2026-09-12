package code.api.util

import cats.effect.IO
import code.api.util.APIUtil.ResourceDoc
import org.http4s.Request

/**
 * The endpoints that exist only because somebody compiled them.
 *
 * A Dynamic Resource Doc is a method body a privileged user POSTs; the server compiles it and the
 * result becomes a live endpoint under `/obp/dynamic-endpoint`. The core has to know about those
 * endpoints in two places — the resource-doc aggregation that answers `/resource-docs`, and the
 * router that dispatches a request to one — but neither place should need the compiler.
 *
 * Both `ResourceDoc` and the compiled handler it carries (`dynamicHttp4sFunction`) are core types
 * already, so the whole compiled artefact crosses this seam as data the core can hold without
 * being able to produce.
 *
 * With nothing installed there are no such endpoints: `docs` is empty and `find` matches nothing.
 * That is the accurate answer for an instance with no compiler, not a degraded one — an endpoint
 * that was never compiled does not exist.
 */
trait CompiledEndpointSource {

  /** Every currently compiled endpoint, as resource docs. */
  def docs: List[ResourceDoc]

  /** The compiled endpoint this request addresses, if any. */
  def find(req: Request[IO]): Option[ResourceDoc]
}

object CompiledEndpoints {

  @volatile private var installed: Option[CompiledEndpointSource] = None

  /** Called once, during boot, by whichever module supplies the compiler. */
  def install(source: CompiledEndpointSource): Unit = installed = Some(source)

  /** Test hook: restore the no-source state. */
  def uninstall(): Unit = installed = None

  def isAvailable: Boolean = installed.isDefined

  def docs: List[ResourceDoc] = installed.map(_.docs).getOrElse(Nil)

  def find(req: Request[IO]): Option[ResourceDoc] = installed.flatMap(_.find(req))
}
