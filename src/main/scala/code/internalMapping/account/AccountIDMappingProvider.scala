package code.internalMapping.account

import code.model.{BankId, AccountId}
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector


object AccountIDMappingProvider extends SimpleInjector {

  val accountIDMappingProvider = new Inject(buildOne _) {}

  def buildOne: AccountIDMappingProvider = MappedAccountIDMappingProvider

}

trait AccountIDMappingProvider {
  
  def getOrCreateAccountIDMapping(bankId: BankId, accountNumber: String): Box[AccountIDMapping]
  
  def getAccountIDMapping(accountId: AccountId): Box[AccountIDMapping]
  
}
