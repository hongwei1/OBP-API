package com.tesobe.obp
import com.tesobe.obp.GetBankAccounts._
import org.scalatest.{FunSuite, Matchers}

class GetBankAccountsTest extends FunSuite with Matchers {

  test("getFullBankAccounts extracts FullBankAccounts" ) {
    val accounts = getFullBankAccountsforUser("./src/test/resources/joni_result.json")
    accounts should be(List(
      FullBankAccount(BasicBankAccount("3565953", "616", "330", AccountPermissions(true,false,false)),"IL230106160000050180963","5668.13", "15000"),
      FullBankAccount(BasicBankAccount("50180983", "616", "430", AccountPermissions(true,false,true)),"IL230106160000050180963","5668.13", "15000"),
      FullBankAccount(BasicBankAccount("50180963", "616", "330", AccountPermissions(true,false,false)),"IL230106160000050180963","5668.13", "15000"),
      //FullBankAccount(BasicBankAccount("20102642", "814", "0", AccountPermissions(true,false,false)),"IL230106160000050180963","5668.13", "15000"),
      FullBankAccount(BasicBankAccount("20102612", "814", "330", AccountPermissions(true,false,false)),"IL230106160000050180963","5668.13", "15000"),
      //FullBankAccount(BasicBankAccount("20102632", "814", "999", AccountPermissions(true,false,false)),"IL230106160000050180963","5668.13", "15000"),
      FullBankAccount(BasicBankAccount("20105505", "814", "330", AccountPermissions(true,false,false)),"IL230106160000050180963","5668.13", "15000")
    ))
    }

   test("base64encoded256(string) is really sha256 hash of string") {
     val res1 = base64EncodedSha256("fred")
     val res2 = base64EncodedSha256("karl")
     res1 should be ("0M/C5TGbgs3HGjOHPoJsk9fuETY/iskcT6Oiz80ihuU")
     res2 should be ("wxppC1KLgaxxCoDWE3KF28ltJHLOBNMfREcXrHLTgYM")
  } 

  
}
