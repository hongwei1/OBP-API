package com.tesobe.obp

import com.tesobe.obp.Ntg6CMf.getNtg6C

class Ntg6CMfTest extends ServerSetup{
  
  test("getNtg6C returns correct result on success"){
    val result = getNtg6C(
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
    result.NTDriveNoResp.esbHeaderResponse.responseStatus.callStatus should be ("Success")
    result.NTDriveNoResp.MFAdminResponse.messageText should be (Some("העדכון בוצע בהצלחה !"))
  }
  

}
