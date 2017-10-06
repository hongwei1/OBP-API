package com.tesobe.obp

import com.tesobe.obp.Ntbd2v050Mf.getNtbd2v050
import com.tesobe.obp.RunMockServer.startMockServer
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}

class Ntbd2v050MfTest extends FunSuite with Matchers with BeforeAndAfterAll {

  override def beforeAll() {
    startMockServer
  }


  test("getNtbd2v050 returns proper values") {
    val result = getNtbd2v050(branch = "616",
      accountType = "330",
      accountNumber = "50180963",
      cbsToken = ";,T          81433020102612",
      username = "N7jut8d",
      ntbdAv050Token = "3639292",
      fromAccountOwnerName = "recipient Name")
    result.P050_ISHUROUT.esbHeaderResponse.responseStatus.callStatus should be("Success")
    result.P050_ISHUROUT.MFAdminResponse.returnCode should be("0")
    result.P050_ISHUROUT.P050_SHAA_BITZUA should be("14:48")
    result.P050_ISHUROUT.P050_TARICH_BITZUA should be("20170620")

  }


  override def afterAll() {
    com.tesobe.obp.RunMockServer.mockServer.stop()
  }
}