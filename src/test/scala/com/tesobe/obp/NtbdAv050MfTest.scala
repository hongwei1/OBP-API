package com.tesobe.obp

import com.tesobe.obp.NtbdAv050Mf.getNtbdAv050
import com.tesobe.obp.RunMockServer.startMockServer
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}

class NtbdAv050MfTest  extends FunSuite with Matchers with BeforeAndAfterAll{

  override def beforeAll() {
    startMockServer
  }




  test("getNtbdAv050 returns proper values"){
    val result = getNtbdAv050(branch = "814",
      accountType = "330",
      accountNumber = "20102612",
      cbsToken = ";-V          81433020102612",
      transferType= "1",
      transferDateInFuture = "20170705")
    result.P050_BDIKACHOVAOUT.esbHeaderResponse.responseStatus.statusCode should be ("Success")
    result.P050_BDIKACHOVAOUT.MFAdminResponse.returnCode should be ("0")
    result.P050_BDIKACHOVAOUT.P050_TOKEN_OUT should be ("3639292")
    result.P050_BDIKACHOVAOUT.P050_SCUM_MAX_LE_HAVARA should be ("10000.00")
    result.P050_BDIKACHOVAOUT.P050_SHEM_HOVA_ANGLIT should be ("")

  }




  override def afterAll() {
    com.tesobe.obp.RunMockServer.mockServer.stop()
  }


}
