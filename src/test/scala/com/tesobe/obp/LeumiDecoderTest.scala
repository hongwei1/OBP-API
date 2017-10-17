package com.tesobe.obp

import com.tesobe.obp.GetBankAccounts.{base64EncodedSha256, hexEncodedSha256}
import com.tesobe.obp.june2017.LeumiDecoder._
import com.tesobe.obp.june2017._
/**
  * Created by work on 6/12/17.
  */
class LeumiDecoderTest  extends ServerSetup {

  val accountId1 = base64EncodedSha256("616" + "330" + "3565953" + "fjdsaFDSAefwfsalfid")
  val accountId2 = base64EncodedSha256("616" + "430" +"50180983" + "fjdsaFDSAefwfsalfid")
  val accountId3 = base64EncodedSha256("616" + "330" +"50180963" + "fjdsaFDSAefwfsalfid")
  val accountId4 = base64EncodedSha256("814" + "330" + "20102612" + "fjdsaFDSAefwfsalfid")
  val accountId5 = base64EncodedSha256("814" + "330" + "20105505" + "fjdsaFDSAefwfsalfid")
  val accountIdHex = hexEncodedSha256("814" + "330" + "20105505" + "fjdsaFDSAefwfsalfid")
  println("HEX is like this: " + accountIdHex)
  val mfToken = "?+1         81433020102612"
  
  test("getBankAccounts works for Stub"){
    val result = getBankAccounts(OutboundGetAccounts(AuthInfo("karlsid", "karl", ""), null)) //TODO ,need fix

    //Balance is from nt1c call, all accounts use the same json stub => all accounts have the same balance
    result should be (InboundGetAccounts(AuthInfo("karlsid", "karl", mfToken),
      List(InboundAccountJune2017("", List(InboundStatusMessage("ESB","Success", "0", "OK")), mfToken, "10", "616", accountId1, "3565953", "330", "5541.28", "ILS", List(""), List("Auditor"), "", "", "", "", "", ""),
        InboundAccountJune2017("", List(InboundStatusMessage("ESB","Success", "0", "OK")), mfToken, "10", "616", accountId2, "50180983", "430", "5541.28", "ILS", List("karl"), List("Owner"), "", "", "", "", "", ""), 
        InboundAccountJune2017("", List(InboundStatusMessage("ESB","Success", "0", "OK")), mfToken, "10", "616", accountId3, "50180963", "330", "5541.28", "ILS", List(""), List("Auditor"), "", "", "", "", "", ""),
        InboundAccountJune2017("", List(InboundStatusMessage("ESB","Success", "0", "OK")), mfToken, "10", "814", accountId4, "20102612", "330", "5541.28", "ILS", List(""), List("Auditor"), "", "", "", "", "", ""),
        InboundAccountJune2017("", List(InboundStatusMessage("ESB","Success", "0", "OK")), mfToken, "10", "814", accountId5, "20105505", "330", "5541.28", "ILS", List(""), List("Auditor"), "", "", "", "", "", ""))))
  }
  
  test("getBankAccountbyAccountId works for Stub"){
    val result = getBankAccountbyAccountId(OutboundGetAccountbyAccountID(AuthInfo("karlsid", "karl", mfToken),"10",accountId1))
    result should be (InboundGetAccountbyAccountID(AuthInfo("karlsid", "karl", mfToken),(InboundAccountJune2017("",List(InboundStatusMessage("ESB","Success", "0", "OK")),  mfToken, "10", "616", accountId1, "3565953", "330", "5541.28", "ILS", List(""), List("Auditor"), "", "", "", "", "", ""))))
  }

  test("getBankAccountbyAccountNumber works for Stub"){
    val result = getBankAccountByAccountNumber(OutboundGetAccountbyAccountNumber(AuthInfo("karlsid", "karl", mfToken),"10","3565953"))
    result should be (InboundGetAccountbyAccountID(AuthInfo("karlsid", "karl", mfToken),(InboundAccountJune2017("",List(InboundStatusMessage("ESB","Success", "0", "OK")),  mfToken, "10", "616", accountId1, "3565953", "330", "5541.28", "ILS", List(""), List("Auditor"), "", "", "", "", "", ""))))
  }
  
  test("getTransactions works for Stubs first transaction"){
    val result = getTransactions(OutboundGetTransactions(AuthInfo("karlsid", "karl", ""), "10", accountId1, 15, "Sat Jul 01 00:00:00 CEST 2000", "Sat Jul 01 00:00:00 CEST 2017"))
    val transactionId = base64EncodedSha256(result.data.head.amount + result.data.head.completedDate + result.data.head.newBalanceAmount)
    result.data.head should be (InternalTransaction(
      "",
      List(
        InboundStatusMessage("ESB","Success", "0", "OK"), //TODO, need to fill the coreBanking error
        InboundStatusMessage("MF","Success", "0", "OK")   //TODO, need to fill the coreBanking error
      ),
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
    val result = getToken(OutboundGetToken("N7jut8d"))
    result should be (InboundToken("N7jut8d",mfToken))
  }


}
