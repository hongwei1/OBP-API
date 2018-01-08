package com.tesobe.obp

import com.tesobe.obp.GetBankAccounts._
import com.tesobe.obp.JoniMf._
import com.tesobe.obp.RunMockServer._
import net.liftweb.json.JsonAST.JValue

class JoniMfTest extends ServerSetup {

  val username = "N7jut8d"
  val mfToken = ">,?          81433020102612"
  val mfresult: String = textFileToString("joni_result.json")
  val sane_result: String = replaceEmptyObjects(mfresult)
  implicit val formats = net.liftweb.json.DefaultFormats
  
  
  test("replaceEmptyObjects replaces all {} with \"\" "){
    assert(!sane_result.contains("{}"))
  }


  test("getJoniMf returns type JoniMfUser"){
    val joniCall = getJoniMf(username, true)
    joniCall match {
      case Right(joniCall) =>
        assert(joniCall.SDR_JONI.esbHeaderResponse.responseStatus.callStatus == "Success")
        assert(joniCall.SDR_JONI.MFAdminResponse.returnCode == "0")
        assert(joniCall.SDR_JONI.esbHeaderResponse.responseStatus.errorDesc
          == Some(""))
        joniCall.SDR_JONI.SDR_LAK_SHEDER.SDRL_LINE(2).SDR_CHN(0).SDRC_LINE.SDRC_CHN.SDRC_CHN_CHN should be("20102642")
        joniCall.SDR_JONI.MFTOKEN should be(mfToken)
      case Left(joniCall) =>
        fail()
    }

  }
   test("getBasicBankAccountsForUser"){
    val accounts = getBasicBankAccountsForUser(username, false)
   accounts should be (Right(List(
     BasicBankAccount("3565953", "616", "330", mfToken, AccountPermissions(false,true,true)), 
     BasicBankAccount("50180983", "616", "430", mfToken, AccountPermissions(false,true,false)),
     BasicBankAccount("50180963", "616", "330", mfToken, AccountPermissions(false,true,true)),
     //BasicBankAccount("20102642","814","0", AccountPermissions(true,false,false)),
     BasicBankAccount("20102612", "814", "330", mfToken, AccountPermissions(false,true,true)),
     //BasicBankAccount("20102632", "814", "999", AccountPermissions(true,false,false)),
     BasicBankAccount("20105505", "814", "330", mfToken, AccountPermissions(false,true,true)),
   )))
   
  }
  test("getBasicBankAccountsForUser works first without, then with cache use"){
    val accountsFromMF = getBasicBankAccountsForUser(username, false)
    accountsFromMF should be (Right(List(
      BasicBankAccount("3565953", "616", "330", mfToken, AccountPermissions(false,true,true)),
      BasicBankAccount("50180983", "616", "430", mfToken, AccountPermissions(false,true,false)),
      BasicBankAccount("50180963", "616", "330", mfToken, AccountPermissions(false,true,true)),
      //BasicBankAccount("20102642","814","0", AccountPermissions(true,false,false)),
      BasicBankAccount("20102612", "814", "330", mfToken, AccountPermissions(false,true,true)),
      //BasicBankAccount("20102632", "814", "999", AccountPermissions(true,false,false)),
      BasicBankAccount("20105505", "814", "330", mfToken, AccountPermissions(false,true,true))
    )))
    val accountsFromCache = getBasicBankAccountsForUser(username, true)
    accountsFromCache should be (Right(List(
      BasicBankAccount("3565953", "616", "330", mfToken, AccountPermissions(false,true,true)),
      BasicBankAccount("50180983", "616", "430", mfToken, AccountPermissions(false,true,false)),
      BasicBankAccount("50180963", "616", "330", mfToken, AccountPermissions(false,true,true)),
      //BasicBankAccount("20102642","814","0", AccountPermissions(true,false,false)),
      BasicBankAccount("20102612", "814", "330", mfToken, AccountPermissions(false,true,true)),
      //BasicBankAccount("20102632", "814", "999", AccountPermissions(true,false,false)),
      BasicBankAccount("20105505", "814", "330", mfToken, AccountPermissions(false,true,true))
    )))

  }
}