package code.bankconnectors.vARZ.mf_calls

import java.util.UUID
import code.api.v3_1_0.PostCustomerJsonV310

object MfUtil {
  
  def mapPostCustomerJsonV310ToKundeRequestAndDisposerRequest(customer: PostCustomerJsonV310) = {
  val postkundenkontakteRequest = PostPrivatkundenkontakteRequest(
    uuid = UUID.randomUUID().toString,
    kundenstamm = Kundenstamm(
      famname = customer.legal_name,
      vorname = customer.legal_name,
      mobiltel = customer.mobile_phone_number,
      emailadr = customer.email,
      
      titel = None,
      filiale = None,
      titelnach = None,
      kundnr = None,
      hilfszahl = None),
    musterkundnr = None
  )
  val postDisposersRequest = PostDisposersRequest(
    credentials = Credentials(
      name = "HARDCODE-Simon",
      pin = 123456),
    status = "ACTIVE",
    language = "DE",
    `type` = "MINI",
    customerNr = customer.customer_number.toInt,
    address = DisposerAddress(
      identifier = "H",
      number = 1),
    bankSupervisorId = "POCOBP",
    
    bankAdvisorId = None,
    number = None
  )
  (postkundenkontakteRequest, postDisposersRequest)
}
  
}
