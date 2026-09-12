package code.api.berlin.group

import code.api.util.BerlinGroupVocabulary
import com.openbankproject.commons.util.ScannedApiVersion

object ConstantsBG {
  // Defined in code.api.util.BerlinGroupVocabulary and aliased here. The core recognises Berlin
  // Group URLs and renders Berlin Group error bodies, so it needs these values; having the core
  // import them from this package made code.api.util and code.api.berlin mutually dependent.
  // The direction is one way now, and every call site inside the standard still says ConstantsBG.
  val berlinGroupVersion1: ScannedApiVersion = BerlinGroupVocabulary.berlinGroupVersion1
  val berlinGroupVersion2: ScannedApiVersion = BerlinGroupVocabulary.berlinGroupVersion2
  object SigningBasketsStatus extends Enumeration {
    type SigningBasketsStatus = Value
    // Only the codes
    // 1) RCVD (Received),
    // 2) PATC (PartiallyAcceptedTechnical Correct) The payment initiation needs multiple authentications, where some but not yet all have been performed. Syntactical and semantical validations are successful.,
    // 3) ACTC (AcceptedTechnicalValidation) ,
    // 4) CANC (Cancelled) and
    // 5) RJCT (Rejected) are supported for signing baskets.
    val RCVD, PATC, ACTC, CANC, RJCT = Value
  }
}
