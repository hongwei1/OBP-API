package com.tesobe.obp.jun2017

import com.tesobe.obp.GetBankAccounts.base64EncodedSha256
import com.tesobe.obp.jun2017.LeumiDecoder._
import com.tesobe.obp.Nt1cTMf.getNt1cTMf
import org.scalatest.{FunSuite, Matchers}
/**
  * Created by work on 6/12/17.
  */
class LeumiDecoderTest extends FunSuite with Matchers{

  val accountId1 = base64EncodedSha256("3565953" + "fjdsaFDSAefwfsalfid")
  val accountId2 = base64EncodedSha256("50180983" + "fjdsaFDSAefwfsalfid")
  val accountId3 = base64EncodedSha256("50180963" + "fjdsaFDSAefwfsalfid")
  val accountId4 = base64EncodedSha256("20102612" + "fjdsaFDSAefwfsalfid")
  val accountId5 = base64EncodedSha256("20105505" + "fjdsaFDSAefwfsalfid")
  
  test("getBankAccounts works for Stub"){
    val result = getBankAccounts(GetAccounts(AuthInfo("karlsid","karl")))

    //Balance is from nt1c call, all accounts use the same json stub => all accounts have the same balance
    result should be (InboundBankAccounts(AuthInfo("karlsid","karl"),
      List(InboundAccountJune2017("errorcode","10","616",accountId1,"3565953","330","5668.13","ILS",List(""),List("Auditor"),"","","","","",""),
        InboundAccountJune2017("errorcode","10","616",accountId2,"50180983","430","5668.13","ILS",List("./src/test/resources/joni_result.json")
          ,List("Owner"),"","","","","",""), 
        InboundAccountJune2017("errorcode","10","616",accountId3,"50180963","330","5668.13","ILS",List(""),List("Auditor"),"","","","","",""),
        InboundAccountJune2017("errorcode","10","814",accountId4,"20102612","330","5668.13","ILS",List(""),List("Auditor"),"","","","","",""),
        InboundAccountJune2017("errorcode","10","814",accountId5,"20105505","330","5668.13","ILS",List(""),List("Auditor"),"","","","","",""))))
  }
  
  test("getBankAccountbyAccountId works for Stub"){
    val result = getBankAccountbyAccountId(GetAccountbyAccountID(AuthInfo("karlsid","karl"),"10",accountId1))
    result should be (InboundBankAccount(AuthInfo("karlsid","karl"),(InboundAccountJune2017("errorcode","10","616",accountId1,
      "3565953","330","5668.13","ILS",List(""),List("Auditor"),"","","","","",""))))
  }

  test("getBankAccountbyAccountNumber works for Stub"){
    val result = getBankAccountByAccountNumber(GetAccountbyAccountNumber(AuthInfo("karlsid","karl"),"10","3565953"))
    result should be (InboundBankAccount(AuthInfo("karlsid","karl"),(InboundAccountJune2017("errorcode","10","616",accountId1,
      "3565953","330","5668.13","ILS",List(""),List("Auditor"),"","","","","",""))))
  }
  
  test("getTransactions works for Stubs first transaction"){
    val first = getNt1cTMf("./src/test/resources/nt1c_T_result.json")
    val result = getTransactions(GetTransactions(AuthInfo("karlsid","karl"),"10", accountId1,"parameters"))
    val transactionId = base64EncodedSha256(result.data.head.amount + result.data.head.completedDate + result.data.head.newBalanceAmount)
    result.data.head should be (InternalTransaction(
      "errorcode",
      transactionId,
      accountId1,
      "-1312.21",
      "10",
      "20160201",
      "counterpartyId",
      "counterpartyName",
      "ILS",
      "פרעון הלוואה",
      "-7192.83",
      "ILS",
      "20160201",
      "12",
      "karlsid"
    ))
  }
  
  test("getToken gives correct token") {
    val result = getToken(GetToken("N7jut8d"))
    result should be (InboundToken("N7jut8d","<M/          81433020102612"))
  }
  
 

}
