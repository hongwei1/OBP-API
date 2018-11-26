package code.internalMapping.account

import code.model.{BankId, AccountId}
import code.util.MappedUUID
import net.liftweb.mapper._

class MappedAccountIDMapping extends AccountIDMapping with LongKeyedMapper[MappedAccountIDMapping] with IdPK with CreatedUpdated {

  def getSingleton = MappedAccountIDMapping

  // Unique
  object mAccountId extends MappedUUID(this)
  object mAccountNumber extends MappedString(this, 50)
  object mBankId extends MappedString(this, 50)

  override def accountId = AccountId(mAccountId.get) 
  override def bankId = BankId(mBankId.get)
  override def accountNumber: String = mAccountNumber.get
  
}

object MappedAccountIDMapping extends MappedAccountIDMapping with LongKeyedMetaMapper[MappedAccountIDMapping] {
  //one account info per bank for each api user
  override def dbIndexes = UniqueIndex(mAccountId) :: UniqueIndex(mBankId, mAccountNumber) :: super.dbIndexes
}