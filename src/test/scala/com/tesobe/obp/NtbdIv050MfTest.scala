package com.tesobe.obp

import com.tesobe.obp.NtbdIv050Mf.getNtbdIv050

class NtbdIv050MfTest extends ServerSetup {

  test("getNtbdIv050 returns proper values") {
    val result = getNtbdIv050(branch = "616",
      accountType = "330",
      accountNumber = "03565953",
      cbsToken = "<)V          81433020102612",
      ntbdAv050Token = "3639292",
      transactionAmount = "15302")
    result match {
      case Right(result) =>
    result.P050_BDIKAZCHUTOUT.esbHeaderResponse.responseStatus.callStatus should be("Success")
    result.P050_BDIKAZCHUTOUT.MFAdminResponse.returnCode should be("0")
    result.P050_BDIKAZCHUTOUT.P050_MAHADURA_101.P050_KOD_ISHUR should be("")
    result.P050_BDIKAZCHUTOUT.P050_MAHADURA_101.P050_KOD_SIBA_LE_ISHUR_PAKID should be("")

      case Left(result) =>
        fail()
    }

  }

}