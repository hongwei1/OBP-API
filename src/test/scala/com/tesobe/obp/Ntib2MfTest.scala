package com.tesobe.obp  

import com.tesobe.obp.Ntib2Mf._
import com.tesobe.obp.RunMockServer.startMockServer
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}


/**
  * Created by work on 6/12/17.
  */
class Ntib2MfTest extends FunSuite with Matchers with BeforeAndAfterAll{

  override def beforeAll() {
    startMockServer
  }
  
  test("getNtibMf gets response from the mainframe"){
    val mfresult = getNtib2MfHttpApache("","","","","")
    assert(mfresult.contains("SHETACHTCHUVA"))
  }
  
  test("getIban gets the IBAN"){
    val iban = getIban("","","","","")
    iban should be ("""IL230106160000050180963""")
  }

  override def afterAll() {
    com.tesobe.obp.RunMockServer.mockServer.stop()
  }
}
