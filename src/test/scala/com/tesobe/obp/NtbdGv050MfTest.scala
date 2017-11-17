package com.tesobe.obp

import com.tesobe.obp.NtbdGv050Mf.getNtbdGv050

class NtbdGv050MfTest extends ServerSetup {

  test("getNtbdGv050 returns proper values") {
    val result = getNtbdGv050(branch = "616",
      accountType = "330",
      accountNumber = "03565953",
      cbsToken = "<)V          81433020102612",
      ntbdAv050Token = "3639292",
      bankTypeOfTo = "1")
    result match {
      case Right(result) =>
    result.P050_AMALOTOUT.esbHeaderResponse.responseStatus.callStatus should be("Success")
    result.P050_AMALOTOUT.MFAdminResponse.returnCode should be("0")
    result.P050_AMALOTOUT.P050_AMALOT_OUT_OLD.P050_SCUM_AMLAT_HAVARA should be ("1.65")

      case Left(result) =>
        fail()
    }

  }
}