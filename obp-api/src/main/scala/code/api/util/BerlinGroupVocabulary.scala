package code.api.util

import com.openbankproject.commons.util.ApiVersion.berlinGroupV13
import com.openbankproject.commons.util.ScannedApiVersion
import net.liftweb.common.Full

/**
 * The Berlin Group vocabulary the CORE needs, defined here rather than in the Berlin Group
 * package, so the dependency runs one way: `code.api.berlin.*` may reach into `code.api.util`,
 * and never the reverse.
 *
 * The core genuinely needs three things. It has to recognise a Berlin Group URL, because a BG
 * request gets a BG-shaped error body rather than OBP's (APIUtil, ErrorResponseConverter); it has
 * to know that shape to build one; and ResourceDocRegistry has to rank the BG standard against UK
 * Open Banking when two standards register the same partialFunctionName. None of that is Berlin
 * Group *logic* — it is protocol vocabulary the core reads.
 *
 * Why the values live here instead of being registered by the Berlin Group package into a table
 * the core reads (the "registry inversion" that would be the purer inversion): APIUtil is touched
 * extremely early and from everywhere, long before any Berlin Group object is initialised, so a
 * registry would be reliably empty at the moment the core consults it — and the failure would be
 * silent, a BG client receiving OBP-shaped errors. These are prop-derived constants with no
 * behaviour, so owning them here costs nothing and cannot be mistimed.
 *
 * `code.api.berlin.group.ConstantsBG` and the Berlin Group JSON factory alias these, so the
 * hundreds of call sites inside the standard are unchanged.
 */
object BerlinGroupVocabulary {

  /**
   * The transaction-status codes a signing basket can carry.
   *
   * These are Berlin Group's codes, but the core writes them: MappedSigningBasketProvider stamps
   * RCVD on create and CANC on cancel. Having that persistence code reach into
   * code.api.berlin.group for the enumeration is the same inversion this object exists to undo,
   * so the definition lives here and ConstantsBG aliases it for the standard's own call sites.
   *
   * Only these five of Berlin Group's status codes are supported for signing baskets:
   * RCVD (Received), PATC (PartiallyAcceptedTechnicalCorrect — multiple authentications needed,
   * some but not all performed, validations successful), ACTC (AcceptedTechnicalValidation),
   * CANC (Cancelled) and RJCT (Rejected).
   */
  object SigningBasketsStatus extends Enumeration {
    type SigningBasketsStatus = Value
    val RCVD, PATC, ACTC, CANC, RJCT = Value
  }

  /**
   * Berlin Group v1.3, with its URL path segment overridden by
   * `berlin_group_version_1_canonical_path` when an installation serves it under a different one.
   */
  val berlinGroupVersion1: ScannedApiVersion = APIUtil.getPropsValue("berlin_group_version_1_canonical_path") match {
    case Full(props) => berlinGroupV13.copy(apiShortVersion = props)
    case _ => berlinGroupV13
  }

  val berlinGroupVersion2: ScannedApiVersion = ScannedApiVersion("berlin-group", "BG", "v2")

  /**
   * The version the alias aggregator registers under in the ScannedApis map.
   *
   * The empty triple when `berlin_group_v1_3_alias_path` is unset is load-bearing: no request can
   * address it, and it deliberately does NOT equal `berlinGroupVersion1`, because colliding with
   * the canonical BG v1.3 key would let the doc-less alias win `ScannedApis`' `.toMap` and blank
   * out /resource-docs/BGv1.3/obp.
   *
   * Note this is NOT the same value as `Http4sBGv13Alias.aliasVersion`, which falls back to the
   * canonical version when inactive — that one is only ever used to answer "is the alias allowed",
   * where inheriting the canonical verdict is exactly right.
   */
  val berlinGroupV13AliasScannedVersion: ScannedApiVersion =
    if (APIUtil.berlinGroupV13AliasPath.nonEmpty)
      ScannedApiVersion(
        APIUtil.berlinGroupV13AliasPath.head,
        APIUtil.berlinGroupV13AliasPath.head,
        APIUtil.berlinGroupV13AliasPath.last)
    else
      ScannedApiVersion("", "", "")

  /** One entry of a Berlin Group `tppMessages` error body. */
  case class ErrorMessageBG(category: String, code: String, path: Option[String], text: String)

  /** The Berlin Group error body: `{"tppMessages": [...]}`, as opposed to OBP's own shape. */
  case class ErrorMessagesBG(tppMessages: List[ErrorMessageBG])
}
