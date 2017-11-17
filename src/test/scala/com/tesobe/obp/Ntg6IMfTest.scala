package com.tesobe.obp

import com.tesobe.obp.Ntg6IMf.getNtg6IMf

class Ntg6IMfTest extends ServerSetup{
  
  test("getNtg6I returns correct result on success"){
    val result = getNtg6IMf(
      branch = "616",
      accountType = "330",
      accountNumber = "50180963",
      cbsToken = "בל          81433020102612"
    )

    result match {
      case Right(result) =>
        result.PMUTSHLIFA_OUT.esbHeaderResponse.responseStatus.callStatus should be ("Success")
        result.PMUTSHLIFA_OUT.MFAdminResponse.returnCode should be ("0")
        result.PMUTSHLIFA_OUT.PMUT_MONE should be ("2")
        result.PMUTSHLIFA_OUT.PMUT_RESHIMAT_MUTAVIM.head.PMUT_PIRTEY_MUTAV.PMUT_TEUR_MUTAV should be ("                     יעכטגאט")
        result.PMUTSHLIFA_OUT.PMUT_RESHIMAT_MUTAVIM.head.PMUT_PIRTEY_MUTAV.PMUT_SHEM_MUTAV  should be ("         יעכטגאט")

      case Left(result) =>
        fail()
    }
  }

}
