package com.tesobe.obp
import com.tesobe.obp.GetBankAccounts._
import net.liftweb.json.{Extraction, prettyRender}
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

   test("hex256(string) is really sha256 hash of string") {
    hexEncodedSha256("fred") should be ("d0cfc2e5319b82cdc71a33873e826c93d7ee11363f8ac91c4fa3a2cfcd2286e5")
  }
  
  test("getModeratedCoreAccountJson gives correct account data") {
    val account = FullBankAccount(BasicBankAccount("3565953", "616", "330", AccountPermissions(true,false,false)),"IL230106160000050180963","5668.13", "15000")
    val result = getModeratedCoreAccountJSON(account)
    print(result)
    implicit val formats = net.liftweb.json.DefaultFormats
    println(prettyRender(Extraction.decompose(result)))
  }
}
