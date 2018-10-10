package code.bankconnectors.vARZ.mf_calls

import java.util.UUID

import code.api.v3_1_0.PostCustomerJsonV310

object Util {
  
  def mapPostCustomerJsonV310ToKundeRequestAndDisposerRequest(customer: PostCustomerJsonV310) = {
  val postkundenkontakteRequest = PostPrivatkundenkontakteRequest(
    musterkundnr = 0,
    kundenstamm = Kundenstamm(
      famname = "",
      vorname = "",
      titel = "",
      mobiltel = "",
      emailadr = "",
      filiale = "",
      titelnach = "",
      kundnr = 0,
      hilfszahl = 0),
    uuid = UUID.randomUUID().toString)
  val postDisposersRequest = PostDisposersRequest(
    number = 0,
    credentials = Credentials(
      name = "",
      pin = ""),
    status = "ACTIVE",
    language = "DE",
    `type` = "FULL",
    customerNr = 0,
    address = DisposerAddress(
      identifier = "H",
      number = 1),
    bankSupervisorId = "POCOBP",
    bankAdvisorId = ""
  )
  (postkundenkontakteRequest, postDisposersRequest)
}
  
}
