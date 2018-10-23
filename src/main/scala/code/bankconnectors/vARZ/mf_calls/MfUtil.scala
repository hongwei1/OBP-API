package code.bankconnectors.vARZ.mf_calls

import java.util.UUID
import code.api.v3_1_0.PostCustomerJsonV310

object MfUtil {
  
  def gerernatePostKundeRequest(
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
    
    def gerernatePostDisposerRequest(customerNumber: Int) =
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
  
}
