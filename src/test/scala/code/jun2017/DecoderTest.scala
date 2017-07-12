package com.tesobe.obp.jun2017

import org.scalatest.{FunSuite, Matchers}
import com.tesobe.obp.jun2017.Decoder._

/**
  * Created by work on 6/12/17.
  */
class DecoderTest extends FunSuite with Matchers{
  
  test("print getBankAccounts"){
    val result = getBankAccounts(GetAccounts(AuthInfo("karlsid","karl")))
    println(result)
      
  }
  
 

}
