package com.tesobe.obp

import com.tesobe.obp.Nt1cTMf._
import org.scalatest.{FunSuite, Matchers}

class Nt1cTMfTest extends FunSuite with Matchers{

  implicit val formats = net.liftweb.json.DefaultFormats

  test("getNt1c4Mf gets result containing proper keywords") {
    val result =  getNt1cTMf("./src/test/resources/nt1c_T_result.json")
    assert(result.contains("TN2_KOD_ARCHAVA"))
  }
  
  test("getIntraDayTransactions extracts case class Nt1c4"){
    val result = getCompletedTransactions("./src/test/resources/nt1c_T_result.json")
    result.TN2_TSHUVA_TAVLAIT.N2TshuvaTavlait.TN2_TNUOT.TN2_PIRTEY_TNUA(14).TN2_TNUA_BODEDET.TN2_MIS_SIDURI should be ("346926")
  }

}
