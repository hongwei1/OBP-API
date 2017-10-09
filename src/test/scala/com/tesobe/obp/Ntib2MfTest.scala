package com.tesobe.obp  

import com.tesobe.obp.Ntib2Mf._


/**
  * Created by work on 6/12/17.
  */
class Ntib2MfTest extends ServerSetup {

  
  test("getNtibMf gets response from the mainframe"){
    val mfresult = getNtib2Mf("","","","","")
    assert(mfresult.contains("SHETACHTCHUVA"))
  }
  
  test("getIban gets the IBAN"){
    val iban = getIban("","","","","")
    iban should be ("""IL230106160000050180963""")
  }

  
}
