package code.internalMapping.customer

import code.model.{BankId, CustomerId}
import net.liftweb.common.Box
import net.liftweb.util.SimpleInjector


object CustomerIDMappingProvider extends SimpleInjector {

  val customerIDMappingProvider = new Inject(buildOne _) {}

  def buildOne: CustomerIDMappingProvider = MappedCustomerIDMappingProvider

}

trait CustomerIDMappingProvider {
  
  def getOrCreateCustomerIDMapping(bankId: BankId, customerNumber: String): Box[CustomerIDMapping]
  
  def getCustomerIDMapping(customerId: CustomerId): Box[CustomerIDMapping]
  
}
