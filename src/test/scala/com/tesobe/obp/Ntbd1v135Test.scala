package com.tesobe.obp

import com.tesobe.obp.Ntbd1v135Mf.getNtbd1v135Mf

class Ntbd1v135Test extends ServerSetup {

  test("Ntbd1v135 gives proper token for Ntbd2v135"){
    val result = getNtbd1v135Mf(
      "616",
      "330",
      "50180963",
      "N7jut8d",
      ">M8          81433020102612",
      "+972526684745",
      "+972532221234",
      "Rent payment",
      "575.36"
    )
    result match {
      case Right(result) =>
    result.P135_BDIKAOUT.P135_TOKEN should be ("3635791")
      case Left(result) => fail()
    }
  }


}
