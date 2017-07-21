package com.tesobe.obp
import com.tesobe.obp.Nt1cMf._
import org.scalatest.{FunSuite, Matchers}
class Nt1cMfTest extends FunSuite with Matchers{
  
  test("getBalance gets balance of account"){
    val result = getBalance("./src/test/resources/nt1c_result.json")
    result should be ("5668.13")
      
  }

  test("getLimit gets credit limit of account "){
    val result = getLimit("./src/test/resources/nt1c_result.json")
    result should be ("15000")

  }
/*
 test("getLimitJsonAst gets credit limit of account") {
   val result = getLimitJsonAst("./src/test/resources/nt1c_result.json")
   result should be("15000")
 }
*/

}
