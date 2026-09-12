package code.api.UKOpenBanking

import code.api.Constant
import code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON.accountIdSwagger
import code.api.UKOpenBanking.v2_0_0.JSONFactory_UKOpenBanking_200
import code.api.UKOpenBanking.v2_0_0.JSONFactory_UKOpenBanking_200.{Account, AccountBalancesUKV200, AccountInner, AccountList, Accounts, AmountUKOpenBankingJson, BalanceJsonUKV200, BalanceUKOpenBankingJson, BankTransactionCodeJson, CreditLineJson, DataJsonUKV200, Links, MetaBisJson, MetaInnerJson, TransactionCodeJson, TransactionInnerJson, TransactionsInnerJson, TransactionsJsonUKV200}
import code.api.util.APIUtil.DateWithDayExampleObject
import com.openbankproject.commons.util.ReflectUtils
import com.github.dwickern.macros.NameOf.nameOf

/**
 * Swagger example values for the UK Open Banking v2.0.0 JSON types.
 *
 * These used to sit in code.api.ResourceDocs1_4_0.SwaggerDefinitionsJSON, which made the core's
 * example file import eighteen types from the UK Open Banking package while the UK endpoints
 * imported the resulting examples back out of the core — a cycle between the core and one
 * standard. The examples belong with the types they are examples of, so they live here and the
 * core only aggregates `allFields` at the two places that assemble swagger definitions.
 *
 * `allFields` mirrors MessageDocsSwaggerDefinitions: SwaggerJSONFactory.loadDefinitions reflects
 * over these values to emit a definition per type, so a value dropped from here disappears from
 * the generated swagger.
 */
object SwaggerDefinitionsUKOB {

  lazy val amountUKOpenBankingJson = AmountUKOpenBankingJson(
    Amount = "0",
    Currency = "EUR"
  )

  lazy val accountInnerJsonUKOpenBanking_v200 = AccountInner(
    SchemeName = "SortCodeAccountNumber",
    Identification = "80200110203345",
    Name = "Mr Kevin",
    SecondaryIdentification = Some("00021")
  )

  lazy val accountJsonUKOpenBanking_v200 = Account(
    AccountId = "22289",
    Currency = "GBP",
    AccountType = "Personal",
    AccountSubType = "CurrentAccount",
    Nickname = "Bills",
    Account = accountInnerJsonUKOpenBanking_v200
  )

  lazy val accountList = AccountList(List(accountJsonUKOpenBanking_v200))
  
  lazy val links =  Links(Self = s"${Constant.HostName}/open-banking/v2.0/accounts/")
  
  lazy val metaUK = JSONFactory_UKOpenBanking_200.MetaUK(1) 
  
  lazy val accountsJsonUKOpenBanking_v200 = Accounts(
    Data = accountList,
    Links = links,
    Meta = metaUK
  )
  
  lazy val bankTransactionCodeJson = BankTransactionCodeJson(
    Code = "ReceivedCreditTransfer",
    SubCode = "DomesticCreditTransfer"
  )

  lazy val balanceUKOpenBankingJson = BalanceUKOpenBankingJson(
    Amount = amountUKOpenBankingJson,
    CreditDebitIndicator = "Credit",
    Type = "InterimBooked"
  )

  lazy val transactionCodeJson = TransactionCodeJson(
    Code = "Transfer",
    Issuer = "AlphaBank"
  )
  
  lazy val transactionInnerJson  = TransactionInnerJson(
    AccountId = accountIdSwagger.value,
    TransactionId  = "123",
    TransactionReference = "Ref 1",
    Amount = amountUKOpenBankingJson,
    CreditDebitIndicator = "Credit",
    Status = "Booked",
    BookingDateTime = DateWithDayExampleObject,
    ValueDateTime = DateWithDayExampleObject,
    TransactionInformation = "Cash from Aubrey",
    BankTransactionCode = bankTransactionCodeJson,
    ProprietaryBankTransactionCode = transactionCodeJson,
    Balance = balanceUKOpenBankingJson
  )

  lazy val transactionsInnerJson =  TransactionsInnerJson(
    Transaction = List(transactionInnerJson)
  )

  lazy val metaInnerJson  = MetaInnerJson(
    TotalPages = 1,
    FirstAvailableDateTime = DateWithDayExampleObject,
    LastAvailableDateTime = DateWithDayExampleObject
  )

  lazy val transactionsJsonUKV200 = TransactionsJsonUKV200(
    Data = transactionsInnerJson,
    Links = links.copy(s"${Constant.HostName}/open-banking/v2.0/accounts/22289/transactions/"),
    Meta = metaInnerJson
  )
  
  lazy val creditLineJson = CreditLineJson(
    Included = true,
    Amount = amountUKOpenBankingJson,
    Type = "Pre-Agreed"
  )
  
  lazy val balanceJsonUK200 = BalanceJsonUKV200(
    AccountId = "22289",
    Amount = amountUKOpenBankingJson,
    CreditDebitIndicator = "Credit",
    Type = "InterimAvailable",
    DateTime = DateWithDayExampleObject,
    CreditLine = List(creditLineJson)
  )
  
  lazy val dataJsonUK200 = DataJsonUKV200(
    Balance = List(balanceJsonUK200)
  )
  
  lazy val metaBisJson =  MetaBisJson(
    TotalPages = 1
  )
  
  lazy val accountBalancesUKV200 = AccountBalancesUKV200(
    Data = dataJsonUK200,
    Links = links.copy(s"${Constant.HostName}/open-banking/v2.0/accounts/22289/balances/"),
    Meta = metaBisJson
  )
  

  val allFields: List[AnyRef] =
    ReflectUtils.getValues(this, List(nameOf(allFields)))
      .filter(it => it != null && it.isInstanceOf[AnyRef])
      .map(_.asInstanceOf[AnyRef])
}
