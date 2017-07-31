package com.tesobe.obp

import com.tesobe.obp.Nt1cTMf._
import com.tesobe.obp.RunMockServer.startMockServer
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}

class Nt1cTMfTest extends FunSuite with Matchers with BeforeAndAfterAll{

  override def beforeAll() {
    startMockServer
  }

  implicit val formats = net.liftweb.json.DefaultFormats
  
  test("Nt1cTMf gets a result"){
    val result = getNt1cTMfHttpApache("","","","",List("","",""),List("","",""),"")
    println(result)
  }

  override def afterAll() {
    com.tesobe.obp.RunMockServer.mockServer.stop()
  }

}
