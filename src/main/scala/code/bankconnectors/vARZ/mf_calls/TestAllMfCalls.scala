package code.bankconnectors.vARZ.mf_calls

import java.util.UUID

import code.util.Helper.MdcLoggable

object TestAllMfCalls extends App with MdcLoggable {
  
  val postkundenkontakteRequest = PostPrivatkundenkontakteRequest(
    uuid = UUID.randomUUID().toString,
    kundenstamm = Kundenstamm(
      famname = "Mustermann",
      vorname = "Max",
      mobiltel = "+4365012345678",
      emailadr = "max.m@max.com")
  )
  
  val postkundenkontakteResponse = KundeservicesV3.postPrivatenkundenkontakte(postkundenkontakteRequest)
  logger.info(s"postkundenkontakteResponse : $postkundenkontakteResponse")
  
  
  val postDisposersRequest = PostDisposersRequest(
    credentials = Credentials(
      name = "ProductOwner2",
      pin = 12345678
    ),
    status = "ACTIVE",
    language = "DE",
    `type` = "MINI",
    customerNr = 29926,
    address = DisposerAddress(
      identifier = "H",
      number = 1
    ),
    bankSupervisorId = "FLEXAPI"
  )
  
  val postDisposersResponse = PostDisposers.postDisposers(postDisposersRequest)
  logger.info(s"postDisposersResponse : $postDisposersResponse")
  
  
  val consumerNumber = "27060"
  val accountsForThisCustomer = KundeServicesV4.getKonten(consumerNumber)
  logger.info(s"accountsForThisCustomer : $accountsForThisCustomer")
  
  
  val getKuntenResponse = KundeservicesV3.getKunten(consumerNumber)
  logger.info(s"getKuntenResponse : $getKuntenResponse")
  
}
