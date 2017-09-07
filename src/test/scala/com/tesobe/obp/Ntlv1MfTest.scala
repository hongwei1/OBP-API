package com.tesobe.obp

import com.tesobe.obp.RunMockServer.startMockServer
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}
import com.tesobe.obp.Ntlv1Mf.getNtlv1MfHttpApache

class Ntlv1MfTest extends FunSuite with Matchers with BeforeAndAfterAll{

  override def beforeAll() {
    startMockServer
  }
/*        "KeyArguments": {
        "Branch": "000",
        "IDNumber": "4051769",
        "IDType": "1",
        "IDCounty": "2121"
      },
      "AuthArguments": {
	"User": "N7jut8d"
        "MFToken":"<&+          81433020102612"*/
  test("getNt1l1MfHttpApache gets proper target mobilenumber with prefix"){
    val result = getNtlv1MfHttpApache(
      username ="N7jut8d",
      branch = "000",
      idNumber = "4051769",
      idType = "1",idCounty = "2121",
      cbsToken = "<&+          81433020102612")
    result.O1OUT1AREA_1.O1_CONTACT_REC.head.O1_TEL_AREA should be("50")
    result.O1OUT1AREA_1.O1_CONTACT_REC.head.O1_TEL_NUM should be("5410377")
    
  }
  override def afterAll() {
    com.tesobe.obp.RunMockServer.mockServer.stop()
  }

}
