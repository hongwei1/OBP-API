package com.tesobe.obp
import com.tesobe.obp.Nt1cBMf._
import com.tesobe.obp.RunMockServer.startMockServer
import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}

class Nt1CBMfTest extends FunSuite with Matchers with BeforeAndAfterAll{

  override def beforeAll() {
    startMockServer
  }
  
  test("getBalance gets balance of account"){
    val result = getBalance("N7jut8d", "616", "330", "50180963", "/G>          81433020102612")
    result should be ("5541.28")
      
  }

  test("getLimit gets credit limit of account "){
    val result = getLimit("N7jut8d","616","330","50180963","/G>          81433020102612")
    result should be ("15000")

  }
/*
 test("getLimitJsonAst gets credit limit of account") {
   val result = getLimitJsonAst("./src/test/resources/nt1c_result.json")
   result should be("15000")
 }
*/
  override def afterAll() {
    com.tesobe.obp.RunMockServer.mockServer.stop()
  }
}
