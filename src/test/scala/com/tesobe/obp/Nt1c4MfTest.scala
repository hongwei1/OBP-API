package com.tesobe.obp

import com.tesobe.obp.Nt1c4Mf._

class Nt1c4MfTest extends ServerSetup{

  implicit val formats = net.liftweb.json.DefaultFormats

  test("getNt1c4Mf gets result containing proper keywords") {
      val result =  getNt1c4(branch = "616",
        accountType = "330",
        accountNumber = "50180963",
        username = "N7jut8d",
        cbsToken = "<.D          81433020102612")
    result match {
      case Right(result) =>
    result.TNATSHUVATAVLAIT1.TNA_SHETACH_LE_SEND_NOSAF.TNA_TNUOT.TNA_PIRTEY_TNUA(5).TNA_TNUA_BODEDET.TNA_AMF_OR_NAFA  should be ("1")
      case Left(result) => fail()
    }
    
  }

}
