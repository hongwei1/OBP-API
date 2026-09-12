package code.abacrule

import code.api.Constant.ABAC_POLICY_ACCOUNT_ACCESS
import code.api.util.{AbacAccountAccess, AbacAccountAccessProvider, AbacRuleEngineProvider, AbacRules, AbacSubject, CallContext}
import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.{BankIdAccountId, User, View}
import net.liftweb.common.{Box, Failure, Full}

import java.util.concurrent.TimeUnit
import scala.concurrent.Await
import scala.concurrent.duration.Duration

/**
 * Answers the core's ABAC question by running the rule engine.
 *
 * This is the half of `APIUtil.checkAbacAccountAccess` that needs a Scala compiler: the rules are
 * user-supplied source, compiled at runtime by `AbacRuleEngine`. Keeping it on this side of
 * `AbacAccountAccess` is what lets the engine move to an optional module later — a deployment
 * that installs no provider gets `Full(false)` from the seam, which is the same answer as
 * `allow_abac_account_access=false`.
 *
 * The blocking `Await` is inherited behaviour, not a new choice: `hasAccountAccess` is a
 * synchronous `Box`-returning method on the request path, so the future has to be joined
 * somewhere. The 10-second ceiling and the swallow-and-decline on exception are likewise
 * unchanged — an engine that throws or hangs must not grant access, and must not take down the
 * request either.
 */
object AbacRuleEngineAccountAccessProvider extends AbacAccountAccessProvider with AbacRuleEngineProvider with MdcLoggable {

  private val evaluationTimeout = Duration(10, TimeUnit.SECONDS)

  override def grantsAccountAccess(
    user: User,
    view: View,
    bankIdAccountId: BankIdAccountId,
    callContext: Option[CallContext]
  ): Box[Boolean] =
    callContext match {
      case Some(cc) =>
        try {
          val futureResult = AbacRuleEngine.executeRulesByPolicyDetailed(
            policy = ABAC_POLICY_ACCOUNT_ACCESS,
            authenticatedUserId = user.userId,
            callContext = cc,
            bankId = Some(bankIdAccountId.bankId.value),
            accountId = Some(bankIdAccountId.accountId.value),
            viewId = Some(view.viewId.value)
          )
          Await.result(futureResult, evaluationTimeout) match {
            case Full((true, _)) => Full(true) // ABAC granted
            case Full((false, ruleIds)) if ruleIds.nonEmpty =>
              Failure(s"ABAC rules denied access. Failing rule IDs: ${ruleIds.mkString(", ")}")
            case _ => Full(false) // No rules or other issue
          }
        } catch {
          case _: Exception => Full(false)
        }
      case None => Full(false)
    }

  // ── AbacRuleEngineProvider: the operator-facing management operations ───────────────────────
  // Straight delegation; the seam exists so the core does not name AbacRuleEngine, not to change
  // what these do.
  override def validateRuleCode(ruleCode: String): scala.concurrent.Future[net.liftweb.common.Box[String]] =
    AbacRuleEngine.validateRuleCodeAsync(ruleCode)

  override def executeRule(ruleId: String, s: AbacSubject): scala.concurrent.Future[net.liftweb.common.Box[Boolean]] =
    AbacRuleEngine.executeRule(ruleId, s.authenticatedUserId, s.onBehalfOfUserId, s.userId, s.callContext,
      s.bankId, s.accountId, s.viewId, s.transactionId, s.transactionRequestId, s.customerId)

  override def executeRulesByPolicy(policy: String, s: AbacSubject): scala.concurrent.Future[net.liftweb.common.Box[Boolean]] =
    AbacRuleEngine.executeRulesByPolicy(policy, s.authenticatedUserId, s.onBehalfOfUserId, s.userId, s.callContext,
      s.bankId, s.accountId, s.viewId, s.transactionId, s.transactionRequestId, s.customerId)

  override def executeRulesByPolicyDetailed(policy: String, s: AbacSubject): scala.concurrent.Future[net.liftweb.common.Box[(Boolean, List[String])]] =
    AbacRuleEngine.executeRulesByPolicyDetailed(policy, s.authenticatedUserId, s.callContext,
      s.bankId, s.accountId, s.viewId)

  override def clearRuleFromCache(ruleId: String): Unit = AbacRuleEngine.clearRuleFromCache(ruleId)

  /**
   * Called from Boot. Idempotent, and survives the rule engine not being there — see
   * DynamicCodeCompilerImpl.install for why a deployment may have removed the toolbox.
   *
   * On failure NOTHING is left installed, so AbacAccountAccess answers Full(false): ABAC grants
   * no access. That is the fail-closed direction, since ABAC is only ever consulted after the
   * ordinary view checks have already refused.
   */
  def install(): Unit = {
    try {
      AbacAccountAccess.install(this)
      AbacRules.install(this)
      logger.info("ABAC rule engine installed (account-access seam + management seam)")
    } catch {
      case e: LinkageError =>
        AbacAccountAccess.uninstall()
        AbacRules.uninstall()
        logger.info(s"ABAC rule engine NOT installed: the toolbox is absent from this deployment " +
          s"(${e.getClass.getSimpleName}). ABAC grants no account access and the rule-management " +
          s"endpoints report it as disabled.")
    }
  }
}
