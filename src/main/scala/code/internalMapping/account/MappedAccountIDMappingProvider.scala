package code.internalMapping.account

import code.model.{BankId, AccountId}
import code.util.Helper.MdcLoggable
import net.liftweb.common.{Empty, Failure, Full, ParamFailure}
import net.liftweb.mapper.By

object MappedAccountIDMappingProvider extends AccountIDMappingProvider with MdcLoggable
{
  
  override def getOrCreateAccountIDMapping(
    bankId: BankId,
    accountNumber: String
  ) =
  {
  
    val mappedAccountIDMapping = MappedAccountIDMapping.find(
      By(MappedAccountIDMapping.mBankId, bankId.value),
      By(MappedAccountIDMapping.mAccountNumber, accountNumber)
    )
  
    mappedAccountIDMapping match
    {
      case Full(vImpl) =>
      {
        logger.debug(s"getOrCreateAccountId --> the mappedAccountIDMapping has been existing in server !")
        mappedAccountIDMapping
      }
      case Empty =>
      {
        val mappedAccountIDMapping: MappedAccountIDMapping =
          MappedAccountIDMapping
            .create
            .mBankId(bankId.value)
            .mAccountNumber(accountNumber)
            .saveMe
        logger.debug(s"getOrCreateAccountId--> create mappedAccountIDMapping : $mappedAccountIDMapping")
        Full(mappedAccountIDMapping)
      }
      case Failure(msg, t, c) => Failure(msg, t, c)
      case ParamFailure(x,y,z,q) => ParamFailure(x,y,z,q)
    }
  }
  
  
  override def getAccountIDMapping(accountId: AccountId) = {
    MappedAccountIDMapping.find(
      By(MappedAccountIDMapping.mAccountId, accountId.value),
    )
  }
}
