package code.api.dynamic.endpoint.helper

import cats.effect.IO
import code.api.util.APIUtil.ResourceDoc
import code.api.util.{CompiledEndpointSource, CompiledEndpoints, DynamicCode, DynamicCodeCompiler, DynamicCodeProblem, DynamicUtil}
import org.http4s.Request
import code.api.util.ErrorMessages.ConnectorMethodBodyCompileFail
import code.bankconnectors.{DynamicConnector, InternalConnector}
import code.util.Helper.MdcLoggable
import net.liftweb.common.Failure
import org.json4s.JValue

/**
 * The compiler behind `code.api.util.DynamicCode`.
 *
 * Everything that needs a Scala toolbox lives on this side: `CompiledObjects`, `InternalConnector`
 * and `DynamicConnector` are all reached from here and nowhere in the core. That is what allows
 * the compiler, javassist and the GraalVM engines to move into an optional module later — the
 * management endpoints go on calling `DynamicCode`, and an instance without the module answers
 * `DynamicCodeExecutionDisabled` exactly as one with `allow_user_generated_scala_code=false` does.
 *
 * Each check compiles and then validates dependencies, in that order, matching what the endpoints
 * did inline before. Dependency rejection is left to propagate as the `JsonResponseException` it
 * has always been: it already carries its own 400 and message, and converting it into a Left here
 * would change what the client sees.
 */
object DynamicCodeCompilerImpl extends DynamicCodeCompiler with CompiledEndpointSource with MdcLoggable {

  // ── CompiledEndpointSource: the endpoints this compiler produced ────────────────────────────
  override def docs: List[ResourceDoc] = DynamicEndpoints.dynamicResourceDocs
  override def find(req: Request[IO]): Option[ResourceDoc] = DynamicEndpoints.findEndpoint(req)


  override def isEnabled: Boolean = DynamicUtil.dynamicCodeExecutionEnabled

  override def checkConnectorMethod(methodName: String, methodBody: String, programmingLang: String): Either[String, Unit] = {
    val compiled = InternalConnector.createFunction(methodName, methodBody, programmingLang)
    if (compiled.isEmpty)
      Left(s"$ConnectorMethodBodyCompileFail ${compiled.asInstanceOf[Failure].msg}")
    else {
      DynamicUtil.Validation.validateDependency(compiled.head)
      Right(())
    }
  }

  override def checkDynamicMessageDoc(programmingLang: String, methodBody: String): Either[String, Unit] = {
    val compiled = DynamicConnector.createFunction(programmingLang, methodBody)
    if (compiled.isEmpty)
      Left(s"$ConnectorMethodBodyCompileFail ${compiled.asInstanceOf[Failure].msg}")
    else {
      DynamicUtil.Validation.validateDependency(compiled.orNull)
      Right(())
    }
  }

  override def checkDynamicResourceDoc(
    exampleRequestBody: Option[JValue],
    successResponseBody: Option[JValue],
    methodBody: String
  ): Either[String, Unit] = {
    // The CompiledObjects constructor is what compiles; validateDependency then inspects the
    // bytecode it produced. Both stay here.
    CompiledObjects(exampleRequestBody, successResponseBody, methodBody).validateDependency()
    Right(())
  }

  override def compileProblems(
    exampleRequestBody: Option[JValue],
    successResponseBody: Option[JValue],
    methodBody: String
  ): List[DynamicCodeProblem] =
    CompiledObjects.compileProblems(exampleRequestBody, successResponseBody, methodBody)
      .map(p => DynamicCodeProblem(p.line, p.column, p.severity, p.message))

  /**
   * Called from Boot. Idempotent, and deliberately survives the compiler not being there.
   *
   * Everything this installs is reached through a seam whose no-provider default is "dynamic code
   * is disabled", so an operator can make that real by deleting the toolbox from the deployment:
   * remove scala-compiler-*.jar (Scala bodies) and the graalvm polyglot / js-language / truffle /
   * regex / icu4j jars (JS and Java bodies) from lib/. Touching this object then raises
   * NoClassDefFoundError, which is caught here — nothing installs, every seam answers
   * DynamicCodeExecutionDisabled, and the instance starts and serves normally without a Scala
   * compiler in its image. On JDK 24+ that is the only way to actually contain this code: the
   * SecurityManager sandbox DynamicUtil tries to install cannot be created at all, and
   * doPrivileged degrades to a pass-through.
   *
   * The default is unchanged — the jars ship as before, so an existing deployment keeps working.
   * LinkageError rather than Exception is the catch, because a missing class on a lazily-resolved
   * reference surfaces as NoClassDefFoundError, which is an Error and not covered by NonFatal.
   */
  def install(): Unit = {
    try {
      // Touch the toolbox FIRST. Reading isEnabled forces DynamicUtil, so an absent compiler
      // raises here, before anything has been installed — rather than partway through, which is
      // how the "internal" connector once stayed advertised on an instance that could not use it.
      val enabled = isEnabled
      DynamicCode.install(this)
      CompiledEndpoints.install(this)
      // The "internal" connector executes Scala the operator uploaded, so it belongs with the
      // compiler rather than in Connector's fixed map. Registered here; absent without it.
      code.api.util.OptionalConnectors.register("internal", InternalConnector.instance)
      logger.info(s"Dynamic code compiler installed (allow_user_generated_scala_code=$enabled)")
    } catch {
      case e: LinkageError =>
        // Belt and braces: unwind everything, in case the failure came after a partial install.
        DynamicCode.uninstall()
        CompiledEndpoints.uninstall()
        code.api.util.OptionalConnectors.unregister("internal")
        logger.info(s"Dynamic code compiler NOT installed: the toolbox is absent from this " +
          s"deployment (${e.getClass.getSimpleName}: ${e.getMessage}). Dynamic Resource Docs, " +
          s"Dynamic Message Docs, Connector Methods, ABAC rules and the 'internal' connector are " +
          s"unavailable; everything else is unaffected.")
    }
  }
}
