package code.api.util

import net.liftweb.common.{Box, Failure, Full}

import scala.concurrent.Future

/**
 * The subject a rule is evaluated against: who is asking, on whose behalf, and about what.
 *
 * A bundle rather than eleven parameters repeated on every method — the engine's own signatures
 * carry them positionally and a trait mirroring that shape would be unreadable and easy to get
 * silently wrong at a call site.
 */
case class AbacSubject(
  authenticatedUserId: String,
  callContext: CallContext,
  onBehalfOfUserId: Option[String] = None,
  userId: Option[String] = None,
  bankId: Option[String] = None,
  accountId: Option[String] = None,
  viewId: Option[String] = None,
  transactionId: Option[String] = None,
  transactionRequestId: Option[String] = None,
  customerId: Option[String] = None
)

/**
 * Managing ABAC rules: validating, running and cache-invalidating them on demand.
 *
 * Separate from `AbacAccountAccess` on purpose, because the two want OPPOSITE defaults when no
 * engine is installed:
 *
 *  - `AbacAccountAccess` sits on the authorisation path of ordinary data requests. It must
 *    degrade silently to "grants nothing" — a Failure there would turn a normal account read into
 *    an error for a caller who never asked about ABAC.
 *  - These operations are what an operator explicitly invoked. Answering `Full(false)` would tell
 *    them their rule evaluated to false, which is not what happened; they should be told the
 *    engine is not available. So the default here is a Failure carrying
 *    DynamicCodeExecutionDisabled — the same thing they see when
 *    allow_user_generated_scala_code is off.
 *
 * One seam with one default could not serve both without lying to one of them.
 */
trait AbacRuleEngineProvider {
  def validateRuleCode(ruleCode: String): Future[Box[String]]
  def executeRule(ruleId: String, subject: AbacSubject): Future[Box[Boolean]]
  def executeRulesByPolicy(policy: String, subject: AbacSubject): Future[Box[Boolean]]
  def executeRulesByPolicyDetailed(policy: String, subject: AbacSubject): Future[Box[(Boolean, List[String])]]
  def clearRuleFromCache(ruleId: String): Unit
}

object AbacRules {

  @volatile private var installed: Option[AbacRuleEngineProvider] = None

  def install(provider: AbacRuleEngineProvider): Unit = installed = Some(provider)
  def uninstall(): Unit = installed = None
  def isAvailable: Boolean = installed.isDefined

  private def unavailable[A]: Future[Box[A]] =
    Future.successful(Failure(ErrorMessages.DynamicCodeExecutionDisabled))

  def validateRuleCode(ruleCode: String): Future[Box[String]] =
    installed.map(_.validateRuleCode(ruleCode)).getOrElse(unavailable)

  def executeRule(ruleId: String, subject: AbacSubject): Future[Box[Boolean]] =
    installed.map(_.executeRule(ruleId, subject)).getOrElse(unavailable)

  def executeRulesByPolicy(policy: String, subject: AbacSubject): Future[Box[Boolean]] =
    installed.map(_.executeRulesByPolicy(policy, subject)).getOrElse(unavailable)

  def executeRulesByPolicyDetailed(policy: String, subject: AbacSubject): Future[Box[(Boolean, List[String])]] =
    installed.map(_.executeRulesByPolicyDetailed(policy, subject)).getOrElse(unavailable)

  /** A cache that does not exist needs no invalidating, so this is a no-op rather than an error. */
  def clearRuleFromCache(ruleId: String): Unit = installed.foreach(_.clearRuleFromCache(ruleId))
}
