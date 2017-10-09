package com.tesobe.obp

import com.tesobe.obp.Ntbd2v135Mf.getNtbd2v135Mf

class Ntbd2v135Test extends ServerSetup {
  
  test("Ntbd2v135 gives proper results"){
    val result = getNtbd2v135Mf(
      branch = "616",
      accountType = "330",
      accountNumber = "50180963",
      username = "N7jut8d",
      cbsToken = ">M8          81433020102612",
      ntbd1v135_Token = "3635791",
      nicknameOfMoneySender = "mike",
      messageToMoneyReceiver = "walla"
    )

    result.P135_BDIKAOUT.P135_SHAA_RISHUM should be ("14:39")
    result.P135_BDIKAOUT.P135_TARICH_BITZUA should be ("20170607")
  }

}
