package com.tesobe.obp

import com.tesobe.obp.Ntbd2v135Mf.getNtbd2v135MfHttpApache
import com.tesobe.obp.RunMockServer.startMockServer
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}

class Ntbd2v135Test extends FunSuite with Matchers with BeforeAndAfterAll{

  override def beforeAll() {
    startMockServer
  }


  test("Ntbd1v135 gives proper token for Ntbd2v135"){
    val result = getNtbd2v135MfHttpApache(
      branch = "616",
      accountType = "330",
      accountNumber = "50180963",
      username = "N7jut8d",
      cbsToken = ">M8          81433020102612",
      ntbd1v135_Token = "3635791",
      nicknameOfMoneySender = "mike",
      messageToMoneyReceiver = "walla"
    )

    result.P135_BDIKAOUT.P135_SHAA_RISHUM should be ("14:39")
    result.P135_BDIKAOUT.P135_TARICH_BITZUA should be ("20170607")
  }




  override def afterAll() {
    com.tesobe.obp.RunMockServer.mockServer.stop()
  }


}
