package code.kycmedias

import java.util.Date

import com.openbankproject.commons.model.KycMedia
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector


object KycMedias extends SimpleInjector {

  val kycMediaProvider = new Inject(buildOne _) {}

  def buildOne: KycMediaProvider = MappedKycMediasProvider

}

trait KycMediaProvider {

  def getKycMedias(customerId: String) : List[KycMedia]

  def addKycMedias(bankId: String, customerId: String, id: String, customerNumber: String, `type`: String, url: String, date: Date, relatesToKycDocumentId: String, relatesToKycCheckId: String) : Box[KycMedia]

}
