package com.tesobe.obp

import com.tesobe.obp.Ntg6BMf.getNtg6B

class Ntg6BMfTest extends ServerSetup{
  
  test("getNtg6B returns correct result on success"){
    val result = getNtg6B(
      branch = "616",
      accountType = "330",
      accountNumber = "50180963",
      cbsToken = "בל          81433020102612",
      counterpartyBankId = "10",
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
