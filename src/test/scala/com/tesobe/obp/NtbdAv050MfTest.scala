package com.tesobe.obp

import com.tesobe.obp.ErrorMessages.{InvalidTimeException, InvalidTransferTypeException}
import com.tesobe.obp.NtbdAv050Mf.getNtbdAv050

class NtbdAv050MfTest extends ServerSetup{

  test("getNtbdAv050 returns proper values"){
    val result = getNtbdAv050(branch = "814",
      accountType = "330",
      accountNumber = "20102612",
      cbsToken = ";-V          81433020102612",
      transferType= "regular",
      transferDateInFuture = "28190705")
    result match {
      case Right(result) =>
    result.P050_BDIKACHOVAOUT.esbHeaderResponse.responseStatus.statusCode should be ("Success")
    result.P050_BDIKACHOVAOUT.MFAdminResponse.returnCode should be ("0")
    result.P050_BDIKACHOVAOUT.P050_TOKEN_OUT should be ("3639292")
    result.P050_BDIKACHOVAOUT.P050_SCUM_MAX_LE_HAVARA should be ("10000.00")
    result.P050_BDIKACHOVAOUT.P050_SHEM_HOVA_ANGLIT should be ("")

      case Left(result) =>
        fail()
    }

  }
  test("getNtbdAv050 fails for invalid parameters"){
    
    an [InvalidTransferTypeException] should be thrownBy getNtbdAv050(branch = "814",
      accountType = "330",
      accountNumber = "20102612",
      cbsToken = ";-V          81433020102612",
      transferType= "whatever",
      transferDateInFuture = "20170705")

    an [InvalidTimeException] should be thrownBy getNtbdAv050(branch = "814",
      accountType = "330",
      accountNumber = "20102612",
      cbsToken = ";-V          81433020102612",
      transferType= "regular",
      transferDateInFuture = "20171705")

    an [InvalidTimeException] should be thrownBy getNtbdAv050(branch = "814",
      accountType = "330",
      accountNumber = "20102612",
      cbsToken = ";-V          81433020102612",
      transferType= "regular",
      transferDateInFuture = "20170732")

    an [InvalidTimeException] should be thrownBy getNtbdAv050(branch = "814",
      accountType = "330",
      accountNumber = "20102612",
      cbsToken = ";-V          81433020102612",
      transferType= "regular",
      transferDateInFuture = "20171128")
   


  }

}
