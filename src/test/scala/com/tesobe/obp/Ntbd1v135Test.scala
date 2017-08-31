package com.tesobe.obp

import com.tesobe.obp.Ntbd1v135Mf.getNtbd1v135MfHttpApache
import com.tesobe.obp.RunMockServer.startMockServer
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}

class Ntbd1v135Test extends FunSuite with Matchers with BeforeAndAfterAll{

  override def beforeAll() {
    startMockServer
  }


  test("Ntbd1v135 gives proper token for Ntbd2v135"){
    val result = getNtbd1v135MfHttpApache(
      "616",
      "330",
      "50180963",
      "N7jut8d",
      ">M8          81433020102612",
      "0526684745",
      "0532221234",
      "Rent payment",
      "575.36"
    )
   
    result.P135_BDIKAOUT.P135_TOKEN should be ("3635791")
  }




  override def afterAll() {
    com.tesobe.obp.RunMockServer.mockServer.stop()
  }


}
