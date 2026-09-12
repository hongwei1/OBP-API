package code.api.berlin.group

import code.api.util.BerlinGroupVocabulary
import com.openbankproject.commons.util.ScannedApiVersion

/**
 * Aliases for the Berlin Group vocabulary the core also needs.
 *
 * The values are defined in code.api.util.BerlinGroupVocabulary: the core recognises Berlin Group
 * URLs, renders Berlin Group error bodies and persists signing-basket status codes, so it needs
 * them, and having the core import them from this package made code.api.util and code.api.berlin
 * mutually dependent. The dependency is one way now — nothing outside this package refers to
 * ConstantsBG — while every call site inside the standard still reads ConstantsBG.
 */
object ConstantsBG {
  val berlinGroupVersion1: ScannedApiVersion = BerlinGroupVocabulary.berlinGroupVersion1
  val berlinGroupVersion2: ScannedApiVersion = BerlinGroupVocabulary.berlinGroupVersion2
  val SigningBasketsStatus = BerlinGroupVocabulary.SigningBasketsStatus
}
