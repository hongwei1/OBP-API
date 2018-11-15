package code.bankconnectors.vARZ.mf_calls

import java.text.SimpleDateFormat
import java.util.UUID

import code.api.v3_1_0.PostCustomerJsonV310
import code.bankconnectors.vARZ.mf_calls.Accounts.{AccountTransactionsResponse, ArzTransaction}
import code.model.{BankAccount, CounterpartyCore, TransactionCore, TransactionId}

object MfUtil {
  
  def generatePostKundeRequest(
    legalName: String,
    mobileNumber: String,
    email: String) = 
    PostPrivatkundenkontakteRequest(
      uuid = UUID.randomUUID().toString,
      kundenstamm = Kundenstamm(
        famname = legalName,
        vorname = legalName,
        mobiltel = mobileNumber,
        emailadr = email
      )
    )
    
    def generatePostDisposerRequest(customerNumber: Int) =
      PostDisposersRequest(
        credentials = Credentials(
          name = "HARDCODE-Simon",
          pin = 123456
        ),
        status = "ACTIVE",
        language = "DE",
        `type` = "MINI",
        customerNr = customerNumber,
        address = DisposerAddress(
          identifier = "H",
          number = 1
        ),
        bankSupervisorId = "POCOBP"
      )
  
  def mapArzTransactionToTransactionCore(response: ArzTransaction) = {
    TransactionCore(
      id = TransactionId(response.transactionId),
      thisAccount = null,
      otherAccount = null,
      transactionType = "todoTransactiontype",
      amount = response.transactionAmount.amount.toInt,
      currency = response.transactionAmount.currency,
      description = Some(response.bookingTextUnstructured),
      startDate = new SimpleDateFormat("yyyy-MM-dd").parse(response.bookingDate),
      finishDate = new SimpleDateFormat("yyyy-MM-dd").parse(response.valueDate),
      balance = null
    )
  }
  
}
