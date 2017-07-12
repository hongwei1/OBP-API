package com.tesobe.obp

import org.scalatest.{FunSuite, Matchers}
import com.tesobe.obp.Nt1c4Mf._

class Nt1c4MfTest extends FunSuite with Matchers{

  implicit val formats = net.liftweb.json.DefaultFormats

  test("getNt1c4Mf gets result containing proper keywords") {
    val result =  getNt1c4Mf("./src/test/resources/nt1c_4_result.json")
    assert(result.contains("TNATSHUVATAVLAIT1"))
  }
  
  test("getIntraDayTransactions extracts case class Nt1c4"){
    val result = getIntraDayTransactions("./src/test/resources/nt1c_4_result.json")
    result.TNATSHUVATAVLAIT1.TNA_SHETACH_LE_SEND_NOSAF.TNA_TNUOT.TNA_PIRTEY_TNUA(5).TNA_TNUA_BODEDET.TNA_AMF_OR_NAFA  should be ("1")
    
  }

}
