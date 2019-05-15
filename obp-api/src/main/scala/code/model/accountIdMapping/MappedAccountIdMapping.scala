package code.model.accountIdMapping

import code.util.MappedUUID
import com.openbankproject.commons.model.AccountId
import net.liftweb.mapper._

class MappedAccountIdMapping extends AccountIdMapping with LongKeyedMapper[MappedAccountIdMapping] with IdPK with CreatedUpdated {

  def getSingleton = MappedAccountIdMapping

  object mObpAccountId extends MappedUUID(this)
  object mCbsAccountId extends MappedUUID(this)

  override def obpAccountId = AccountId(mObpAccountId.get) 
  override def cbsAccountId = AccountId(mCbsAccountId.get)
  
}

object MappedAccountIdMapping extends MappedAccountIdMapping with LongKeyedMetaMapper[MappedAccountIdMapping] {
  override def dbIndexes = UniqueIndex(mObpAccountId) ::UniqueIndex(mCbsAccountId) :: super.dbIndexes
}