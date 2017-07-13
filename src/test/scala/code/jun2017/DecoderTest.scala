package com.tesobe.obp.jun2017

import com.tesobe.obp.GetBankAccounts.hexEncodedSha256
import org.scalatest.{FunSuite, Matchers}
import com.tesobe.obp.jun2017.LeumiDecoder._

/**
  * Created by work on 6/12/17.
  */
class DecoderTest extends FunSuite with Matchers{
  
  test("getBankAccounts works for Stub"){
    val result = getBankAccounts(GetAccounts(AuthInfo("karlsid","karl")))
    val accountId1 = hexEncodedSha256("3565953" + "fjdsaFDSAefwfsalfid")
    val accountId2 = hexEncodedSha256("50180983" + "fjdsaFDSAefwfsalfid")
    val accountId3 = hexEncodedSha256("50180963" + "fjdsaFDSAefwfsalfid")
    val accountId4 = hexEncodedSha256("20102612" + "fjdsaFDSAefwfsalfid")
    val accountId5 = hexEncodedSha256("20105505" + "fjdsaFDSAefwfsalfid")
    //Balance is from nt1c call, all accounts use the same json stub => all accounts have the same balance
    result should be (BankAccounts(AuthInfo("karlsid","karl"),
      List(InboundAccountJune2017("","10","616",accountId1,"","330","5668.13","ILS",List(""),List("Accountant"),"","","","","",""),
        InboundAccountJune2017("","10","616",accountId2,"","430","5668.13","ILS",List("./src/test/resources/joni_result.json")
          ,List("Owner"),"","","","","",""), 
        InboundAccountJune2017("","10","616",accountId3,"","330","5668.13","ILS",List(""),List("Accountant"),"","","","","",""),
        InboundAccountJune2017("","10","814",accountId4,"","330","5668.13","ILS",List(""),List("Accountant"),"","","","","",""),
        InboundAccountJune2017("","10","814",accountId5,"","330","5668.13","ILS",List(""),List("Accountant"),"","","","","",""))))

  }
  
 

}
