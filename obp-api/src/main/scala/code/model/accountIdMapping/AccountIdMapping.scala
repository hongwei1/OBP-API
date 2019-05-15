package code.model.accountIdMapping

import com.openbankproject.commons.model.AccountId

/**
  * For security reason, we can only use the accountId (UUID) in some apis. Because these ids can be cached over the websites.
  *
  * For the following trait, it is used for storing the mapping between obp account_id and bank account unique identifier for real account.
  * 
  * eg: Once we get Accounts over CBS. OBP will create a UUID for each account. But the bank must provide us a unique identifier 
  *     For OBP side generate the accountId. 
  *     
  */
trait AccountIdMapping {
  def obpAccountId : AccountId // The UUID for the Account,  To be used in URLs
  def cbsAccountId: AccountId // This must be from Bank, bank need provide a unique id for each account.
}
