package com.tesobe.obp

import com.tesobe.obp.Ntg6AMf.getNtg6A

class Ntg6AMfTest extends ServerSetup {

  test("getNtg6A returns correct result on success") {
    val result = getNtg6A(
      branch = "616",
      accountType = "330",
      accountNumber = "50180963",
      cbsToken = "בל          81433020102612",
      counterpartyBranchNumber = "616",
      counterpartyAccountNumber = "50180963",
      counterpartyName = " ",
      counterpartyDescription = " ",
      counterpartyIBAN = " ",
      counterpartyNameInEnglish = " ",
      counterpartyDescriptionInEnglish = " "
    )
    result match {
      case Right(result) =>
        result.NTDriveNoResp.esbHeaderResponse.responseStatus.callStatus should be("Success")
        result.NTDriveNoResp.MFAdminResponse.messageText should be(Some("העדכון בוצע בהצלחה !"))

      case Left(result) =>
        fail("mapping exception!!!")
    }

  }
}
