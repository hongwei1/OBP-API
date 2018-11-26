package code.internalMapping.account

import code.model.{AccountId, BankId}

/**
  * This trait is used for storing the mapped between obp account_id and bank_id to real cbs account number.
  * eg: Once we create new account over CBS, we need also create a accountId in api side.
  *     For security reason, we can only use the accountId (UUID) in the apis.  
  *     Because these id’s might be cached on the internet.
  */
trait AccountIDMapping {
  def bankId : BankId 
  def accountId : AccountId
  def accountNumber : String // The Bank side real Account number. 
}
