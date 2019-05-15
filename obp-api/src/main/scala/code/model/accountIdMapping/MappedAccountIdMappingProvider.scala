package code.model.accountIdMapping

import code.util.Helper.MdcLoggable
import com.openbankproject.commons.model.AccountId
import net.liftweb.common.{Empty, Full}
import net.liftweb.mapper.By


object MappedAccountIdMappingProvider extends AccountIdMappingProvider with MdcLoggable
{
  
  override def getOrCreateObpAccountId(cbsAccountId: AccountId ) =
  {
  
    val mappedInternalAccountMapping = MappedAccountIdMapping.find(
      By(MappedAccountIdMapping.mCbsAccountId, cbsAccountId.value)
    )

    mappedInternalAccountMapping match
    {
      case Full(vImpl) =>
      {
        mappedInternalAccountMapping
      }
      case Empty =>
      {
        val mappedAccountIdMapping: MappedAccountIdMapping =
          MappedAccountIdMapping
            .create
            .mCbsAccountId(cbsAccountId.value)
            .saveMe
        logger.debug(s"getOrCreateAccountId--> create MappedAccountIdMapping : $mappedAccountIdMapping")
        Full(mappedAccountIdMapping)
      }
    }
  }
}

