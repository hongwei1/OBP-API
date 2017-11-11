package com.tesobe.obp  

import com.tesobe.obp.Ntib2Mf._


/**
  * Created by work on 6/12/17.
  */
class Ntib2MfTest extends ServerSetup {

  
  test("getNtibMf gets response from the mainframe"){
    val mfresult = getNtib2Mf("","","","","")
    mfresult match {
      case Right(mfresult) =>
        mfresult.SHETACHTCHUVA.TS00_PIRTEY_TCHUVA.TS00_TV_TCHUVA.TS00_NIGRERET_TCHUVA.TS00_IBAN should be("""IL230106160000050180963""")
      case Left(mfresult) =>
        fail()
    }
  }

  
}
