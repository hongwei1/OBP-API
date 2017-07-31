package com.tesobe.obp

import com.tesobe.obp.Nt1cTMf._
import org.scalatest.{FunSuite, Matchers}

class Nt1cTMfTest extends FunSuite with Matchers{

  implicit val formats = net.liftweb.json.DefaultFormats

  test("getNt1cTM Mf gets result containing proper keywords") {
    val result =  getNt1cTMf("nt1c_T_result.json")
    assert(result.contains("TN2_KOD_ARCHAVA"))
  }
  
  test("getCompletedTransactions extracts proper case class"){
    val result = getCompletedTransactions("nt1c_T_result.json")
    //result.TN2_TSHUVA_TAVLAIT.N2TshuvaTavlait.TN2_TNUOT.TN2_PIRTEY_TNUA(14).TN2_TNUA_BODEDET.TN2_MIS_SIDURI should be ("346926")
  }

}
