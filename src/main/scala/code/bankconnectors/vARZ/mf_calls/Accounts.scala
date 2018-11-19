package code.bankconnectors.vARZ.mf_calls

import code.api.util.APIUtil
import code.bankconnectors.vARZ.HttpClient._
import net.liftweb.json.JValue
import net.liftweb.json.JsonParser._
import net.liftweb.json.Extraction.decompose

object Accounts {

  val baseUrl  = APIUtil.getPropsValue("base.url.accounts").getOrElse("")
  implicit val formats = net.liftweb.json.DefaultFormats
  
  case class AccountTransactionsResponse(
                                        account: Int,
                                        transactions: ArzTransactions
                                        )
  
  case class ArzTransactions(booked: List[ArzTransaction])
  case class ArzTransaction(
                             transactionId: String,
                             entryReference: String,
                             endToEndId: String,
                             mandateId: String,
                             creditorId: String,
                             bookingDate: String, // 2018-08-16,
                             valueDate: String, //2018-08-16,
                             transactionAmount: ArzTransactionAmount,
                             creditorName: String,
                             creditorAccount: String,
                             ultimateCreditor: String,
                             debtorName: String,
                             debtorAccount: String,
                             ultimateDebtor: String,
                             remittanceInformationUnstructured: String,
                             remittanceInformationStructured: String,
                             bookingTextUnstructured: String
                           )
  
  case class ArzTransactionsDefaultError(
  `type`: String,
  title: String,
  status: Int,
  detail: String,
  instance: String
                                        )
  
  case class ArzTransactionAmount( currency: String, amount: Double)

  def getTransactionsFromCbs(accountId: String): Either[ArzTransactionsDefaultError, AccountTransactionsResponse] = {
    val path = s"$baseUrl/accounts/$accountId/transactions"
    val result = makeGetRequest(path)
    try {
      Right(parse(result).extract[AccountTransactionsResponse]) 
    } catch {
      case e: net.liftweb.json.MappingException => 
        try {
          Left(parse(result).extract[ArzTransactionsDefaultError])
        } catch  {
          case e: net.liftweb.json.MappingException => throw new Exception("OBP-50201: Connector did not return the set of transactions we requested")
    }
    }
    
  }



}

