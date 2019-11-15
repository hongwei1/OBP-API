package code.kycstatuses

import java.util.Date

import com.openbankproject.commons.model.KycStatus
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector


object KycStatuses extends SimpleInjector {

  val kycStatusProvider = new Inject(buildOne _) {}

  def buildOne: KycStatusProvider = MappedKycStatusesProvider

}

trait KycStatusProvider {

  def getKycStatuses(customerId: String) : List[KycStatus]

  def addKycStatus(bankId: String, customerId: String, customerNumber: String, ok: Boolean, date: Date) : Box[KycStatus]

}
