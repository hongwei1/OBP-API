package com.tesobe.obp

import com.tesobe.obp.Ntg6KMf.getNtg6KMf

class Ntg6KMfTest extends ServerSetup{
  
  test("getNtg6K returns correct result on success"){
    val result = getNtg6KMf(branch = "616", accountType = "330", accountNumber = "50180963", cbsToken = "בל          81433020102612", true)

    result match {
      case Right(result) =>
        result.PMUTSHLIFA_OUT.esbHeaderResponse.responseStatus.callStatus should be ("Success")
        result.PMUTSHLIFA_OUT.MFAdminResponse.returnCode should be ("0")
        result.PMUTSHLIFA_OUT.PMUT_MONE should be ("3")
        result.PMUTSHLIFA_OUT.PMUT_RESHIMAT_MUTAVIM.head.PMUT_PIRTEY_MUTAV.PMUT_TEUR_MUTAV should be ("")
        result.PMUTSHLIFA_OUT.PMUT_RESHIMAT_MUTAVIM.head.PMUT_PIRTEY_MUTAV.PMUT_SHEM_MUTAV  should be ("            סכלא")
        result.PMUTSHLIFA_OUT.PMUT_RESHIMAT_MUTAVIM(1).PMUT_PIRTEY_MUTAV.PMUT_SHEM_MUTAV should be ("             טטט")
        result.PMUTSHLIFA_OUT.PMUT_RESHIMAT_MUTAVIM(2).PMUT_PIRTEY_MUTAV.PMUT_SHEM_MUTAV should be ("             לעי")

      case Left(result) =>
        fail()
    }
  }

}
