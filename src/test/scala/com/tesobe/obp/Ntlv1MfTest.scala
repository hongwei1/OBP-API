package com.tesobe.obp

import com.tesobe.obp.Nt1cTMf._
import com.tesobe.obp.RunMockServer.startMockServer
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}
import com.tesobe.obp.Ntlv1Mf.getNtlv1MfHttpApache

class Ntlv1MfTest extends FunSuite with Matchers with BeforeAndAfterAll{

  override def beforeAll() {
    startMockServer
  }
  
  test("getNt1l1MfHttpApache gets result"){
    val result = getNtlv1MfHttpApache("branch","idNumber","idType","idCounty","cbsToken")
    println(result)
    
  }
  override def afterAll() {
    com.tesobe.obp.RunMockServer.mockServer.stop()
  }

}
