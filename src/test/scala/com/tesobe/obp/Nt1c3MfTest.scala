package com.tesobe.obp  
import com.tesobe.obp.Nt1c3Mf._

class Nt1c3MfTest  extends ServerSetup{
  implicit val formats = net.liftweb.json.DefaultFormats
  
  test("getNt1c3Mf gets result containing proper keywords") {
    val result =  getNt1c3Mf("nt1c_3_result.json")
    assert(result.contains("TA1TSHUVATAVLAIT1"))
  }
  
  test("getFutureTransaction extract case class object") {
    val result = getFutureTransactions("nt1c_3_result.json")
    result.TA1TSHUVATAVLAIT1.TA1_SHETACH_LE_SEND_NOSAF.TA1_COUNTER should be ("4")
    result.TA1TSHUVATAVLAIT1.TA1_SHETACH_LE_SEND_NOSAF.TA1_TNUOT.TA1_PIRTEY_TNUA(2).TA1_TNUA_BODEDET.TA1_TA_ERECH should be ("20170611")
  }


}
