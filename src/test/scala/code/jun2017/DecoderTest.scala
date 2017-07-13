package com.tesobe.obp.jun2017

import org.scalatest.{FunSuite, Matchers}
import com.tesobe.obp.jun2017.LeumiDecoder._

/**
  * Created by work on 6/12/17.
  */
class DecoderTest extends FunSuite with Matchers{
  
  test("getBankAccounts works for Stub"){
    val result = getBankAccounts(GetAccounts(AuthInfo("karlsid","karl")))
    //Balance is from nt1c call, all accounts use the same json stub => all accounts have the same balance
    result should be (BankAccounts(AuthInfo("karlsid","karl"),
      List(InboundAccountJune2017("","10","616","3565953","","330","5668.13","ILS",List(""),List("Accountant"),"","","","","",""),
        InboundAccountJune2017("","10","616","50180983","","430","5668.13","ILS",List("./src/test/resources/joni_result.json")
          ,List("Owner"),"","","","","",""), 
        InboundAccountJune2017("","10","616","50180963","","330","5668.13","ILS",List(""),List("Accountant"),"","","","","",""),
        InboundAccountJune2017("","10","814","20102612","","330","5668.13","ILS",List(""),List("Accountant"),"","","","","",""),
        InboundAccountJune2017("","10","814","20105505","","330","5668.13","ILS",List(""),List("Accountant"),"","","","","",""))))

  }
  
 

}
