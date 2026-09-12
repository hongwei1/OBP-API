package code.api.util

import com.openbankproject.commons.model.{BankIdAccountId, User, View}
import net.liftweb.common.{Box, Full}

/**
 * Attribute-based account access, as the core sees it.
 *
 * ABAC rules are user-supplied Scala compiled at runtime. That engine is being moved out of
 * obp-api into an optional module, so that a deployment which does not want a Scala compiler in
 * its image can simply not ship one. The core still has to ask the question on every account
 * access, hence this seam.
 *
 * WHY A MUTABLE REGISTRY IS SAFE HERE, WHEN IT WAS NOT FOR BERLIN GROUP
 *
 * Stage 3 deliberately rejected a registry for the Berlin Group vocabulary: APIUtil is touched
 * far earlier than any standard's object, so the table would have been empty exactly when the
 * core read it — and the failure was silent and wrong (a BG client receiving OBP-shaped errors).
 *
 * The hazard does not apply here, because of what "not installed" means. Consulting this before
 * a provider registers yields `Full(false)`, which is not a wrong answer: it is identical to the
 * shipped default `allow_abac_account_access=false`. A late registration can only ever ADD
 * capability; it can never retract an access already granted. There is also no window in
 * practice — `Http4sServer` completes `Boot.boot` before it binds a port.
 *
 * WHY `Full(false)` IS THE FAIL-CLOSED DEFAULT
 *
 * `APIUtil.hasAccountAccess` reaches ABAC only in its last branch, after public-view, firehose
 * and ordinary view checks have all already failed — the comment there reads "Normal checks
 * failed — try ABAC as fallback". ABAC is therefore a pure GRANT: it can hand out access the
 * view model refused, and it cannot take away access the view model allowed, because that case
 * returned `Full(true)` before ever getting here.
 *
 * So with no provider installed, `Full(false)` means "ABAC grants nothing". Nothing that was
 * denied becomes allowed. The `Failure(...)` an installed engine returns when a rule explicitly
 * denies is, in this position, just a more informative denial — the outcome is a refusal either
 * way. That is the whole reason this default is safe to pick, and it is pinned by
 * AbacAccountAccessTest.
 */
trait AbacAccountAccessProvider {

  /**
   * Does ABAC grant `user` access to `bankIdAccountId` through `view`?
   *
   * `Full(true)` grants, `Full(false)` declines to grant, `Failure` declines with a reason.
   * Only ever consulted after the ordinary access checks have already refused.
   */
  def grantsAccountAccess(
    user: User,
    view: View,
    bankIdAccountId: BankIdAccountId,
    callContext: Option[CallContext]
  ): Box[Boolean]
}

object AbacAccountAccess {

  @volatile private var installed: Option[AbacAccountAccessProvider] = None

  /** Called once, during boot, by whichever module supplies the rule engine. */
  def install(provider: AbacAccountAccessProvider): Unit = installed = Some(provider)

  /** Test hook: restore the no-provider state. */
  def uninstall(): Unit = installed = None

  /** True when a rule engine is available. Reported by the diagnostics endpoints. */
  def isAvailable: Boolean = installed.isDefined

  /**
   * The core's question. With no engine installed this is `Full(false)` — ABAC grants nothing,
   * exactly as when `allow_abac_account_access` is false. See the trait's doc for why that is
   * the fail-closed answer rather than a fail-open one.
   */
  def grantsAccountAccess(
    user: User,
    view: View,
    bankIdAccountId: BankIdAccountId,
    callContext: Option[CallContext]
  ): Box[Boolean] =
    installed match {
      case Some(provider) => provider.grantsAccountAccess(user, view, bankIdAccountId, callContext)
      case None => Full(false)
    }
}
