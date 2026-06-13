package code.views

import code.api.util.DoobieUtil
import code.views.system.ViewDefinition
import doobie._
import doobie.implicits._
import net.liftweb.common.{Box, Empty, Full}

/**
 * Doobie-backed view lookups for the `viewdefinition` table, replacing the Lift
 * Mapper `ViewDefinition.findSystemView`/`findCustomView` queries at the READ-ONLY
 * call sites in [[MapperViews]].
 *
 * Like the existing `MapperViews.viewDefinitionFromRow` (the account-access read
 * path), this builds an in-memory Lift `ViewDefinition` from a Doobie row. View
 * stays == ViewDefinition (no new View implementation → no equality/cast
 * regressions; the `asInstanceOf[ViewDefinition]` sites keep working).
 *
 * Only ~15 columns are materialised: the 100 `canSeeX_`/`canAddX_` boolean
 * permission columns are vestigial — every permission check goes through
 * `view.allowed_actions` (the separate ViewPermission table, queried by
 * bank_id/account_id/view_id, which the constructed object carries) and
 * `createViewJSON` derives all JSON flags from `allowed_actions`.
 *
 * IMPORTANT: the objects returned here are NOT persisted (built via
 * `ViewDefinition.create`, so Lift marks them new). They must only be used at
 * read-only sites; never call `.saveMe`/`.delete_!` on them (that would INSERT a
 * duplicate). The update/remove view paths stay on Lift `ViewDefinition.find`.
 */
object DoobieViewProvider {

  private case class ViewRow(
    id: Option[Long],
    viewId: Option[String],
    name: Option[String],
    description: Option[String],
    metadataView: Option[String],
    isSystem: Option[Boolean],
    isPublic: Option[Boolean],
    isFirehose: Option[Boolean],
    usePrivateAlias: Option[Boolean],
    usePublicAlias: Option[Boolean],
    hideOtherAccountMetadata: Option[Boolean],
    canGrantCustom: Option[Boolean],
    canRevokeCustom: Option[Boolean],
    bankId: Option[String],
    accountId: Option[String]
  )

  private val selectCols: Fragment =
    fr"""SELECT id_, view_id, name_, description_, metadataView_, isSystem_, isPublic_, isFirehose_,
                usePrivateAliasIfOneExists_, usePublicAliasIfOneExists_, hideOtherAccountMetadataIfAlias_,
                canGrantAccessToCustomViews_, canRevokeAccessToCustomViews_, bank_id, account_id
         FROM viewdefinition"""

  private def toViewDefinition(row: ViewRow): ViewDefinition = {
    val vd = ViewDefinition.create
      .view_id(row.viewId.getOrElse(""))
      .name_(row.name.getOrElse(""))
      .description_(row.description.getOrElse(""))
      .metadataView_(row.metadataView.getOrElse(""))
      .isSystem_(row.isSystem.getOrElse(false))
      .isPublic_(row.isPublic.getOrElse(false))
      .isFirehose_(row.isFirehose.getOrElse(false))
      .usePrivateAliasIfOneExists_(row.usePrivateAlias.getOrElse(false))
      .usePublicAliasIfOneExists_(row.usePublicAlias.getOrElse(false))
      .hideOtherAccountMetadataIfAlias_(row.hideOtherAccountMetadata.getOrElse(false))
      .canGrantAccessToCustomViews_(row.canGrantCustom.getOrElse(false))
      .canRevokeAccessToCustomViews_(row.canRevokeCustom.getOrElse(false))
    // System views carry NULL bank_id/account_id (column default null) — only set when present.
    row.bankId.foreach(vd.bank_id(_))
    row.accountId.foreach(vd.account_id(_))
    // NB: the primary key id_ is intentionally NOT set — MappedLongIndex rejects writes
    // ("Do not have permissions to set this field"), and like viewDefinitionFromRow these
    // read-only objects don't need it (every consumer keys off bank_id/account_id/view_id).
    vd
  }

  private def runOne(where: Fragment): Box[ViewDefinition] =
    DoobieUtil.runQuery((selectCols ++ where ++ fr"LIMIT 1").query[ViewRow].option) match {
      case Some(row) => Full(toViewDefinition(row))
      case None      => Empty
    }

  /** Mirror of ViewDefinition.findSystemView: NULL bank/account, isSystem=true, matching view_id. */
  def findSystemView(viewId: String): Box[ViewDefinition] =
    runOne(fr"WHERE bank_id IS NULL AND account_id IS NULL AND isSystem_ = true AND view_id = $viewId")

  /** Mirror of ViewDefinition.findCustomView: matching bank/account, isSystem=false, matching view_id. */
  def findCustomView(bankId: String, accountId: String, viewId: String): Box[ViewDefinition] =
    runOne(fr"WHERE bank_id = $bankId AND account_id = $accountId AND isSystem_ = false AND view_id = $viewId")
}
