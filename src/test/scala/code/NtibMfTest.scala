package com.tesobe.obp  

import com.tesobe.obp.NtibMf._
import org.scalatest.{FunSuite, Matchers}


/**
  * Created by work on 6/12/17.
  */
class NtibMfTest extends FunSuite with Matchers{
  
  test("getNtibMf gets response from the mainframe"){
    val mfresult = getNtibMf("./src/test/resources/ntib_result.json")
    assert(mfresult.contains("SHETACHTCHUVA"))
  }
  
  test("getIban gets the IBAN"){
    val iban = getIban("./src/test/resources/ntib_result.json")
    iban should be ("""IL230106160000050180963""")
  }

}
