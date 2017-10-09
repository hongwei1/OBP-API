package com.tesobe.obp

import com.tesobe.obp.Ntbd1v105Mf.getNtbd1v105Mf

class Ntbd1v105MfTest extends ServerSetup {

  test("getNtbd1v105 returns proper values"){
    val result = getNtbd1v105Mf(branch = "616",
      accountType = "330",
      accountNumber = "50180963",
      cbsToken = ">U(          81433020102612",
      cardNumber = "4580000045673214",
      cardExpirationDate = "",
      cardWithdrawalLimit = "",
      mobileNumberOfMoneySender = "0532225455",
      amount = "100",
      description = "cool",
      idNumber = "204778591",
      idType = "1",
      nameOfMoneyReceiver = "kium",
      birthDateOfMoneyReceiver = "930708",
      mobileNumberOfMoneyReceiver = "0506724131")
    result.P135_BDIKAOUT.P135_TOKEN should be ("3635791")
    result.P135_BDIKAOUT.P135_AMALOT.P135_SCHUM_AMALA should be ("0.00")
    result.P135_BDIKAOUT.esbHeaderResponse.responseStatus.callStatus should be ("Success")
    result.P135_BDIKAOUT.MFAdminResponse.returnCode should be ("0")


  }


}

