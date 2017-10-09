package com.tesobe.obp

import com.tesobe.obp.Ntbd2v105Mf.getNtbd2v105Mf

class Ntbd2v105MfTest extends ServerSetup {

  test("getNtbd2v105HttpApache returns proper values"){
    val result = getNtbd2v105Mf(branch = "616",
      accountType = "330",
      accountNumber = "50180963",
      cbsToken = ">U(          81433020102612",
      ntbd1v105Token = "3639283",
    "","")
    result.PELET_1352.esbHeaderResponse.responseStatus.callStatus should be ("Success")
    result.PELET_1352.MFAdminResponse.returnCode should be ("0")


  }

}

