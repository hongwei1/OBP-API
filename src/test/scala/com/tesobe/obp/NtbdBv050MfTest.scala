package com.tesobe.obp

import com.tesobe.obp.NtbdBv050Mf.getNtbdBv050
import com.tesobe.obp.RunMockServer.startMockServer
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}

class NtbdBv050MfTest extends FunSuite with Matchers with BeforeAndAfterAll{

  override def beforeAll() {
    startMockServer
  }




  test("getNtbdBv050 returns proper values"){
    val result = getNtbdBv050(branch = "616",
      accountType = "330",
      accountNumber = "03565953",
      cbsToken = "<)V          81433020102612",
      ntbdAv050Token = "3639292",
      toAccountBankId = "10",
      toAccountBranchId = "914",
      toAccountAccountNumber = "1696441",
      toAccountIban = "0",
      transactionAmount = "000000015302",
      description = "Cause ",
      referenceNameOfTo = "recipient Name")
    result.NTDriveNoResp.esbHeaderResponse.responseStatus.callStatus should be ("Success")
    result.NTDriveNoResp.MFAdminResponse.returnCode should be ("1")
    result.NTDriveNoResp.MFAdminResponse.messageText.getOrElse("") should be ("העדכון בוצע בהצלחה !")


  }




  override def afterAll() {
    com.tesobe.obp.RunMockServer.mockServer.stop()
  }


}
