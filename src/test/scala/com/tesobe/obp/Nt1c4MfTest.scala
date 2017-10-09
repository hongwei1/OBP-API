package com.tesobe.obp

import org.scalatest.{BeforeAndAfterAll, FunSuite, Matchers}
import com.tesobe.obp.Nt1c4Mf._
import com.tesobe.obp.RunMockServer.startMockServer

class Nt1c4MfTest extends ServerSetup{

  implicit val formats = net.liftweb.json.DefaultFormats

  test("getNt1c4Mf gets result containing proper keywords") {
    val result =  getNt1c4MfHttpApache("","","","","")
    assert(result.contains("TNATSHUVATAVLAIT1"))
  }
  
  test("getIntraDayTransactions extracts case class Nt1c4"){
    val result = getIntraDayTransactions("nt1c_4_result.json")
    result.TNATSHUVATAVLAIT1.TNA_SHETACH_LE_SEND_NOSAF.TNA_TNUOT.TNA_PIRTEY_TNUA(5).TNA_TNUA_BODEDET.TNA_AMF_OR_NAFA  should be ("1")
    
  }

}
