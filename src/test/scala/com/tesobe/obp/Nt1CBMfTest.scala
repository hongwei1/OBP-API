package com.tesobe.obp
import com.tesobe.obp.Nt1cBMf.getNt1cB

class Nt1CBMfTest extends ServerSetup{

  test("getBalance gets balance of account"){
    val result = getNt1cB("N7jut8d", "616", "330", "50180963", "<בל          81433020102612")
    result match {
      case Right(x) =>
        x.TSHUVATAVLAIT.HH_MISGAROT_ASHRAI.HH_PIRTEY_CHESHBON.HH_MATI.HH_ITRA_NOCHECHIT should be("5541.28")

      case Left(x) =>
        fail()
    }      
  }
}
