package code.abacrule

import code.api.Constant.ABAC_POLICY_ACCOUNT_ACCESS
import code.api.util.{AbacAccountAccess, AbacAccountAccessProvider, CallContext}
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
object AbacRuleEngineAccountAccessProvider extends AbacAccountAccessProvider with MdcLoggable {

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

  /** Called from Boot. Idempotent. */
  def install(): Unit = {
    AbacAccountAccess.install(this)
    logger.info("ABAC account-access rule engine installed")
  }
}
