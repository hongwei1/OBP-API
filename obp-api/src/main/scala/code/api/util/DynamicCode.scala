package code.api.util

import org.json4s.JValue

/**
 * One compiler diagnostic, as the core reports it.
 *
 * `line` / `column` are 1-based in the method body the author submitted (the server's wrapper
 * lines are already subtracted); 0 when the compiler gave no position.
 */
case class DynamicCodeProblem(line: Int, column: Int, severity: String, message: String)

/**
 * Compiling user-supplied code, as the core sees it.
 *
 * Dynamic Resource Docs, Dynamic Message Docs and Connector Methods are all bodies of Scala (or
 * JS, or Java) that a privileged user POSTs and the server compiles at runtime. On JDK 24+ the
 * SecurityManager sandbox that used to contain them cannot even be installed — `DynamicUtil`
 * catches the failure and `doPrivileged` degrades to a pass-through — so holding the relevant
 * entitlement is effectively host RCE. A deployment that does not use the feature should be able
 * to ship without the compiler at all, and that is what this seam is for.
 *
 * WHAT DOES NOT CROSS THE SEAM: the compiled artefact. Every management endpoint that compiles
 * discards the result and keeps only "did it compile" plus the dependency verdict — so the
 * implementations return that, and `Http4sEndpointIO`, `DynamicFunction` and the toolboxes stay
 * entirely on the engine side.
 *
 * DEFAULTS WITH NO ENGINE INSTALLED: `isEnabled` is false and every check returns
 * `Left(ErrorMessages.DynamicCodeExecutionDisabled)` — byte for byte what those endpoints already
 * answer when `allow_user_generated_scala_code` is false. An absent module therefore reads to a
 * caller exactly like the feature being switched off, which it is.
 */
trait DynamicCodeCompiler {

  /** Mirrors `allow_user_generated_scala_code`. False also when no engine is installed. */
  def isEnabled: Boolean

  /**
   * Compile a connector method body and validate its dependencies.
   *
   * `Left(message)` when it does not compile. Dependency rejection THROWS
   * `code.api.JsonResponseException` rather than returning Left — unchanged from before the seam,
   * because that exception already carries the 400 and the message the client receives.
   */
  def checkConnectorMethod(methodName: String, methodBody: String, programmingLang: String): Either[String, Unit]

  /** Compile a dynamic message doc body and validate its dependencies. Same contract as above. */
  def checkDynamicMessageDoc(programmingLang: String, methodBody: String): Either[String, Unit]

  /** Compile a dynamic resource doc's method body and validate its dependencies. Same contract. */
  def checkDynamicResourceDoc(
    exampleRequestBody: Option[JValue],
    successResponseBody: Option[JValue],
    methodBody: String
  ): Either[String, Unit]

  /** Dry run: compiler diagnostics only, nothing stored and nothing validated. Empty = compiles. */
  def compileProblems(
    exampleRequestBody: Option[JValue],
    successResponseBody: Option[JValue],
    methodBody: String
  ): List[DynamicCodeProblem]
}

object DynamicCode {

  @volatile private var installed: Option[DynamicCodeCompiler] = None

  /** Called once, during boot, by whichever module supplies the compiler. */
  def install(compiler: DynamicCodeCompiler): Unit = installed = Some(compiler)

  /** Test hook: restore the no-compiler state. */
  def uninstall(): Unit = installed = None

  /** True when a compiler is available at all, regardless of the prop. */
  def isAvailable: Boolean = installed.isDefined

  /**
   * Whether dynamic code may be compiled here: a compiler must be installed AND
   * `allow_user_generated_scala_code` must be on. The endpoints check this first and answer
   * `DynamicCodeExecutionDisabled`, so an instance without the module never reaches the rest.
   */
  def isEnabled: Boolean = installed.exists(_.isEnabled)

  private def disabled[A]: Either[String, A] = Left(ErrorMessages.DynamicCodeExecutionDisabled)

  def checkConnectorMethod(methodName: String, methodBody: String, programmingLang: String): Either[String, Unit] =
    installed.map(_.checkConnectorMethod(methodName, methodBody, programmingLang)).getOrElse(disabled)

  def checkDynamicMessageDoc(programmingLang: String, methodBody: String): Either[String, Unit] =
    installed.map(_.checkDynamicMessageDoc(programmingLang, methodBody)).getOrElse(disabled)

  def checkDynamicResourceDoc(
    exampleRequestBody: Option[JValue],
    successResponseBody: Option[JValue],
    methodBody: String
  ): Either[String, Unit] =
    installed.map(_.checkDynamicResourceDoc(exampleRequestBody, successResponseBody, methodBody)).getOrElse(disabled)

  /** With no compiler installed this is the single "disabled" diagnostic rather than an empty
    * list — an empty list means "it compiled", which would be a lie. */
  def compileProblems(
    exampleRequestBody: Option[JValue],
    successResponseBody: Option[JValue],
    methodBody: String
  ): List[DynamicCodeProblem] =
    installed
      .map(_.compileProblems(exampleRequestBody, successResponseBody, methodBody))
      .getOrElse(List(DynamicCodeProblem(0, 0, "ERROR", ErrorMessages.DynamicCodeExecutionDisabled)))
}
