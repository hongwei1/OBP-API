package com.tesobe.obpatms

import com.openbankproject.commons.dto._
import com.openbankproject.commons.model._
import com.openbankproject.commons.util.JsonSerializers
import org.json4s.native.JsonMethods.parse
import org.json4s.native.Serialization.write

/**
  * Dispatches on the AMQP messageId exactly as RabbitMQConnector_vOct2024.sendRequest publishes
  * it (obp_get_atm, obp_create_or_update_atm, ...) — see RabbitMQUtils.scala in obp-api. There is
  * no discriminator inside the JSON body itself; the messageId AMQP property is the only routing
  * key, which is also why a message this handler does not recognise is simply logged and dropped
  * rather than nacked back onto a shared queue other consumers might also be watching.
  *
  * Uses the exact same json4s Formats obp-api uses to write outbound / read inbound adapter
  * messages (com.openbankproject.commons.util.JsonSerializers.nullTolerateFormats) — including
  * AbstractTypeDeserializer, which is what lets `atm: AtmT` / `branch: BranchT` trait-typed
  * OutBound fields extract into their concrete Commons class. There is deliberately no adapter-side
  * reimplementation of that: reusing the library the core itself uses is what keeps the two sides
  * from drifting into two incompatible codecs.
  */
class MessageHandler(atms: AtmRepo, branches: BranchRepo) {

  private implicit val formats = JsonSerializers.nullTolerateFormats

  private def okStatus = Status("", Nil)

  private def notFoundStatus(what: String) =
    Status("404", List(InboundStatusMessage("obp-atms", "error", "404", s"$what not found")))

  private def inboundCallContext(outbound: OutboundAdapterCallContext) =
    InboundAdapterCallContext(outbound.correlationId, outbound.sessionId, outbound.generalContext)

  /** Returns None for a messageId this deployment's routing table should never send here — the
    * caller logs and drops the delivery rather than replying, since there is nothing meaningful
    * to reply with when the request was never meant for this process.
    *
    * Any exception raised while decoding the request or serving it is turned into a plain
    * `com.openbankproject.commons.model.ErrorMessage(code, message)` reply. That type is
    * recognised generically by Connector.extractAdapterResponse (ErrorMessage.isErrorMessage
    * matches on shape: exactly a `code` int and a `message` string) regardless of which InBound
    * type the request expected, so one reply shape covers every method without needing a
    * per-method typed error branch — the branches above build a typed InBound only for the
    * business-level "not found" case, where the concrete InBound type is what the core expects
    * to (unsuccessfully) unpack. */
  def handle(messageId: String, body: String): Option[String] = {
    def reply[T <: InBoundTrait[_]](inbound: T): String = write(inbound)

    def attempt(run: => Option[String]): Option[String] =
      try run
      catch {
        case t: Throwable => Some(write(ErrorMessage(500, s"obp-atms: ${Option(t.getMessage).getOrElse(t.toString)}")))
      }

    attempt {
    val json = parse(body)
    messageId match {
      case "obp_get_atm" =>
        val req = json.extract[OutBoundGetAtm]
        val cc = inboundCallContext(req.outboundAdapterCallContext)
        atms.findOne(req.bankId, req.atmId) match {
          case Some(atm) => Some(reply(InBoundGetAtm(cc, okStatus, atm)))
          case None => Some(reply(InBoundGetAtm(cc, notFoundStatus("atm"), null)))
        }

      case "obp_get_atms" =>
        val req = json.extract[OutBoundGetAtms]
        val cc = inboundCallContext(req.outboundAdapterCallContext)
        Some(reply(InBoundGetAtms(cc, okStatus, atms.findByBank(req.bankId))))

      case "obp_get_branch" =>
        val req = json.extract[OutBoundGetBranch]
        val cc = inboundCallContext(req.outboundAdapterCallContext)
        branches.findOne(req.bankId, req.branchId) match {
          case Some(branch) => Some(reply(InBoundGetBranch(cc, okStatus, branch)))
          case None => Some(reply(InBoundGetBranch(cc, notFoundStatus("branch"), null)))
        }

      case "obp_get_branches" =>
        val req = json.extract[OutBoundGetBranches]
        val cc = inboundCallContext(req.outboundAdapterCallContext)
        Some(reply(InBoundGetBranches(cc, okStatus, branches.findByBank(req.bankId))))

      case "obp_create_or_update_atm" =>
        val req = json.extract[OutBoundCreateOrUpdateAtm]
        val cc = inboundCallContext(req.outboundAdapterCallContext)
        Some(reply(InBoundCreateOrUpdateAtm(cc, okStatus, atms.upsert(req.atm))))

      case "obp_create_or_update_branch" =>
        val req = json.extract[OutBoundCreateOrUpdateBranch]
        val cc = inboundCallContext(req.outboundAdapterCallContext)
        Some(reply(InBoundCreateOrUpdateBranch(cc, okStatus, branches.upsert(req.branch))))

      case "obp_delete_atm" =>
        val req = json.extract[OutBoundDeleteAtm]
        val cc = inboundCallContext(req.outboundAdapterCallContext)
        Some(reply(InBoundDeleteAtm(cc, okStatus, atms.delete(req.atm.bankId, req.atm.atmId))))

      // obp_delete_atm_attribute / obp_delete_atm_attributes_by_atm_id are NOT part of this
      // deployment's recommended MethodRouting rows (see k3s/method-routing-atms.sql): atm
      // attributes live in the core's MappedAtmAttribute table, which this process does not own
      // (createOrUpdateAtmAttribute / getAtmAttributeById / getAtmAttributesByAtm have no
      // InBound/OutBound DTOs at all, so they can never be routed here — see plan 9.3 step 2).
      // Routing only the two deletes here while creates/reads stay on `mapped` would split one
      // logical table across two data stores. Handlers exist for completeness (the DTOs support
      // it structurally) and answer truthfully for a store with no attribute rows of its own.
      case "obp_delete_atm_attribute" =>
        val req = json.extract[OutBoundDeleteAtmAttribute]
        val cc = inboundCallContext(req.outboundAdapterCallContext)
        Some(reply(InBoundDeleteAtmAttribute(cc, okStatus, true)))

      case "obp_delete_atm_attributes_by_atm_id" =>
        val req = json.extract[OutBoundDeleteAtmAttributesByAtmId]
        val cc = inboundCallContext(req.outboundAdapterCallContext)
        Some(reply(InBoundDeleteAtmAttributesByAtmId(cc, okStatus, true)))

      case "obp_update_atm_accessibility_features" =>
        val req = json.extract[OutBoundUpdateAtmAccessibilityFeatures]
        val cc = inboundCallContext(req.outboundAdapterCallContext)
        atms.updateAccessibilityFeatures(req.bankId, req.atmId, req.accessibilityFeatures) match {
          case Some(atm) => Some(reply(InBoundUpdateAtmAccessibilityFeatures(cc, okStatus, atm)))
          case None => Some(reply(InBoundUpdateAtmAccessibilityFeatures(cc, notFoundStatus("atm"), null)))
        }

      case "obp_update_atm_location_categories" =>
        val req = json.extract[OutBoundUpdateAtmLocationCategories]
        val cc = inboundCallContext(req.outboundAdapterCallContext)
        atms.updateLocationCategories(req.bankId, req.atmId, req.locationCategories) match {
          case Some(atm) => Some(reply(InBoundUpdateAtmLocationCategories(cc, okStatus, atm)))
          case None => Some(reply(InBoundUpdateAtmLocationCategories(cc, notFoundStatus("atm"), null)))
        }

      case "obp_update_atm_notes" =>
        val req = json.extract[OutBoundUpdateAtmNotes]
        val cc = inboundCallContext(req.outboundAdapterCallContext)
        atms.updateNotes(req.bankId, req.atmId, req.notes) match {
          case Some(atm) => Some(reply(InBoundUpdateAtmNotes(cc, okStatus, atm)))
          case None => Some(reply(InBoundUpdateAtmNotes(cc, notFoundStatus("atm"), null)))
        }

      // See the field-naming note on AtmRepo.updateServices: the wire field is called
      // supportedCurrencies but the value is the services list (a pre-existing mismatch in
      // obp-commons' OutBoundUpdateAtmServices, not introduced here).
      case "obp_update_atm_services" =>
        val req = json.extract[OutBoundUpdateAtmServices]
        val cc = inboundCallContext(req.outboundAdapterCallContext)
        atms.updateServices(req.bankId, req.atmId, req.supportedCurrencies) match {
          case Some(atm) => Some(reply(InBoundUpdateAtmServices(cc, okStatus, atm)))
          case None => Some(reply(InBoundUpdateAtmServices(cc, notFoundStatus("atm"), null)))
        }

      case "obp_update_atm_supported_currencies" =>
        val req = json.extract[OutBoundUpdateAtmSupportedCurrencies]
        val cc = inboundCallContext(req.outboundAdapterCallContext)
        atms.updateSupportedCurrencies(req.bankId, req.atmId, req.supportedCurrencies) match {
          case Some(atm) => Some(reply(InBoundUpdateAtmSupportedCurrencies(cc, okStatus, atm)))
          case None => Some(reply(InBoundUpdateAtmSupportedCurrencies(cc, notFoundStatus("atm"), null)))
        }

      case "obp_update_atm_supported_languages" =>
        val req = json.extract[OutBoundUpdateAtmSupportedLanguages]
        val cc = inboundCallContext(req.outboundAdapterCallContext)
        atms.updateSupportedLanguages(req.bankId, req.atmId, req.supportedLanguages) match {
          case Some(atm) => Some(reply(InBoundUpdateAtmSupportedLanguages(cc, okStatus, atm)))
          case None => Some(reply(InBoundUpdateAtmSupportedLanguages(cc, notFoundStatus("atm"), null)))
        }

      case _ => None
    }
    }
  }
}
