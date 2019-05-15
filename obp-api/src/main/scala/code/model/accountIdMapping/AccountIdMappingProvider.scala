package code.model.accountIdMapping

import com.openbankproject.commons.model.AccountId
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector


object AccountIdMappingProvider extends SimpleInjector {

  val accountIdMappingProvider = new Inject(buildOne _) {}

  def buildOne: AccountIdMappingProvider = MappedAccountIdMappingProvider

}

trait AccountIdMappingProvider {
  
  def getOrCreateObpAccountId(cbsAccountId: AccountId): Box[AccountIdMapping]
  
}
