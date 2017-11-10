package com.tesobe.obp  
import com.tesobe.obp.Nt1c3Mf.getNt1c3
  

class Nt1c3MfTest  extends ServerSetup{
  implicit val formats = net.liftweb.json.DefaultFormats
  
  test("getNt1c3Mf gets result containing proper keywords") {
    val result =  getNt1c3(branch = "616",
      accountType = "330",
      accountNumber = "50180963",
      username = "N7jut8d",
      cbsToken = "\\/G>          81433020102612")
    result match {
      case Right(result) =>
    result.TA1TSHUVATAVLAIT1.TA1_SHETACH_LE_SEND_NOSAF.TA1_COUNTER should be ("4")
    result.TA1TSHUVATAVLAIT1.TA1_SHETACH_LE_SEND_NOSAF.TA1_TNUOT.TA1_PIRTEY_TNUA(2).TA1_TNUA_BODEDET.TA1_TA_ERECH should be ("20170611")
      case Left(result) => fail()
    }
  }


}
