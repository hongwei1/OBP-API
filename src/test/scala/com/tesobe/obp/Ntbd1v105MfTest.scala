package com.tesobe.obp

import com.tesobe.obp.ErrorMessages.{InvalidAmountException, InvalidIdTypeException, InvalidMobilNumberException, InvalidPassportOrNationalIdException}
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
      mobileNumberOfMoneySender = "+972532225455",
      amount = "100",
      description = "cool",
      idNumber = "204778591",
      idType = "1",
      nameOfMoneyReceiver = "kium",
      birthDateOfMoneyReceiver = "930708",
      mobileNumberOfMoneyReceiver = "+972506724131")
    result match {
      case Right(result) =>
        result.P135_BDIKAOUT.P135_TOKEN should be("3635791")
        result.P135_BDIKAOUT.P135_AMALOT.P135_SCHUM_AMALA should be("0.00")
        result.P135_BDIKAOUT.esbHeaderResponse.responseStatus.callStatus should be("Success")
        result.P135_BDIKAOUT.MFAdminResponse.returnCode should be("0")

      case Left(result) =>
        fail()
    }


  }

  test("getNtbd1v105 should fail with idNumber.length > 9"){
    an [InvalidPassportOrNationalIdException] should be thrownBy getNtbd1v105Mf(branch = "616",
      accountType = "330",
      accountNumber = "50180963",
      cbsToken = ">U(          81433020102612",
      cardNumber = "4580000045673214",
      cardExpirationDate = "",
      cardWithdrawalLimit = "",
      mobileNumberOfMoneySender = "+972532225455",
      amount = "100",
      description = "cool",
      idNumber = "20477859132323232",
      idType = "1",
      nameOfMoneyReceiver = "kium",
      birthDateOfMoneyReceiver = "930708",
      mobileNumberOfMoneyReceiver = "+972506724131")
    




  }
  
  test("getNtbd1v105 should fail for mobileNumber without Israeli country code"){
    an [InvalidMobilNumberException] should be thrownBy getNtbd1v105Mf(branch = "616",
      accountType = "330",
      accountNumber = "50180963",
      cbsToken = ">U(          81433020102612",
      cardNumber = "4580000045673214",
      cardExpirationDate = "",
      cardWithdrawalLimit = "",
      mobileNumberOfMoneySender = "+33532225455",
      amount = "100",
      description = "cool",
      idNumber = "204778591",
      idType = "1",
      nameOfMoneyReceiver = "kium",
      birthDateOfMoneyReceiver = "930708",
      mobileNumberOfMoneyReceiver = "+33506724131")





  }

  test("getNtbd1v105 should fail for amount > 99900 and if not devisible by 100"){
    an [InvalidAmountException] should be thrownBy getNtbd1v105Mf(branch = "616",
      accountType = "330",
      accountNumber = "50180963",
      cbsToken = ">U(          81433020102612",
      cardNumber = "4580000045673214",
      cardExpirationDate = "",
      cardWithdrawalLimit = "",
      mobileNumberOfMoneySender = "+972532225455",
      amount = "100000",
      description = "cool",
      idNumber = "204778591",
      idType = "1",
      nameOfMoneyReceiver = "kium",
      birthDateOfMoneyReceiver = "930708",
      mobileNumberOfMoneyReceiver = "+972506724131")

    an [InvalidAmountException] should be thrownBy getNtbd1v105Mf(branch = "616",
      accountType = "330",
      accountNumber = "50180963",
      cbsToken = ">U(          81433020102612",
      cardNumber = "4580000045673214",
      cardExpirationDate = "",
      cardWithdrawalLimit = "",
      mobileNumberOfMoneySender = "+972532225455",
      amount = "999",
      description = "cool",
      idNumber = "204778591",
      idType = "1",
      nameOfMoneyReceiver = "kium",
      birthDateOfMoneyReceiver = "930708",
      mobileNumberOfMoneyReceiver = "+972506724131")
  }

  test("getNtbd1v105 should fail with idType != 1 && !=5") {
    an [InvalidIdTypeException] should be thrownBy  getNtbd1v105Mf(branch = "616",
      accountType = "330",
      accountNumber = "50180963",
      cbsToken = ">U(          81433020102612",
      cardNumber = "4580000045673214",
      cardExpirationDate = "",
      cardWithdrawalLimit = "",
      mobileNumberOfMoneySender = "+972532225455",
      amount = "100",
      description = "cool",
      idNumber = "204778591",
      idType = "8",
      nameOfMoneyReceiver = "kium",
      birthDateOfMoneyReceiver = "930708",
      mobileNumberOfMoneyReceiver = "+972506724131")
  }
  


}

