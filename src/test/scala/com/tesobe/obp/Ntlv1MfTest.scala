package com.tesobe.obp

import com.tesobe.obp.Ntlv1Mf.getNtlv1Mf

class Ntlv1MfTest extends ServerSetup{

/*        "KeyArguments": {
        "Branch": "000",
        "IDNumber": "4051769",
        "IDType": "1",
        "IDCounty": "2121"
      },
      "AuthArguments": {
	"User": "N7jut8d"
        "MFToken":"<&+          81433020102612"*/
  test("getNt1l1MfHttpApache gets proper target mobilenumber with prefix"){
    val result = getNtlv1Mf(
      username ="N7jut8d",
      idNumber = "4051769",
      idType = "1",
      cbsToken = "<&+          81433020102612")


    result match {
      case Right(result) =>
    result.O1OUT1AREA_1.O1_CONTACT_REC.head.O1_TEL_AREA should be("50")
    result.O1OUT1AREA_1.O1_CONTACT_REC.head.O1_TEL_NUM should be("5410377")

      case Left(result) =>
        fail()
    }
    
  }

}
