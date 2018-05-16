package com.tesobe.obp

import com.tesobe.obp.NttfWMf.getNttfWMf

class NttfWMfTest extends ServerSetup{
  
  test("getNttfWMMfHttpApache returns proper values"){
    val result = getNttfWMf(branch = "814",
      accountType = "330",
      accountNumber = "20102612",
      cbsToken = ";-V          81433020102612")
    result.PELET_NTTF_W.P_HEADER.P_TIKRA_MAX_MUTAV should be ("2500")
    result.PELET_NTTF_W.P_PRATIM.P_PIRTEY_KARTIS.head.P_MISPAR_KARTIS should be ("1063157422")
    result.PELET_NTTF_W.P_PRATIM.P_PIRTEY_KARTIS.head.P_TOKEF_KARTIS should be ("0")
    result.PELET_NTTF_W.P_PRATIM.P_PIRTEY_KARTIS.head.P_TIKRAT_KARTIS should be ("10019")
    
  }

}