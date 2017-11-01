package com.tesobe.obp

import com.tesobe.obp.Ntbd2v050Mf.getNtbd2v050

class Ntbd2v050MfTest extends ServerSetup {

  test("getNtbd2v050 returns proper values") {
    val result = getNtbd2v050(branch = "616",
      accountType = "330",
      accountNumber = "50180963",
      cbsToken = ";,T          81433020102612",
      username = "N7jut8d",
      ntbdAv050Token = "3639292",
      ntbdAv050fromAccountOwnerName = "recipient Name")
    "            ".trim should be ("")
    result match {
      case Right(result) =>
    result.P050_ISHUROUT.esbHeaderResponse.responseStatus.callStatus should be("Success")
    result.P050_ISHUROUT.MFAdminResponse.returnCode should be("0")
    result.P050_ISHUROUT.P050_SHAA_BITZUA should be("14:48")
    result.P050_ISHUROUT.P050_TARICH_BITZUA should be("20170620")

      case Left(result) =>
        fail()
    }

  }

}