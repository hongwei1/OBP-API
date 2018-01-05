
package com.tesobe.obp


import com.tesobe.obp.ErrorMessages.InvalidTimeException
import com.tesobe.obp.GetBankAccounts.base64EncodedSha256
import com.tesobe.obp.Util.TransactionRequestTypes
import com.tesobe.obp.june2017.LeumiDecoder._
import com.tesobe.obp.june2017._

import scala.collection.immutable.List
/**
  * Created by work on 6/12/17.
  */
class LeumiDecoderTest  extends ServerSetup {

  val accountId1 = "3jdVT1N-wWeawA-fTqLkr5vE0qHiQLkhjru2YvJ8F98"
  val accountId2 = "ahrGDvrcgX9YQFH-fpR_pPp9v9FW_l9ZGm2X3iuM698"
  val accountId3 = "yP95NBuFMr5zC5DCCdupFw-D1YfksNUlwZ2VLRSlxQs"
  val accountId4 = "kQWRSBnWJolR63EOBvb26MezPQXTrdQZTSZUqhQfOVM"
  val accountId5 = "wZShrbQW6CYiQGgSUwaxMYfz0W1ee59R0yOuJNha9W0"
/*  
  Encoded from:
  val accountId1 = base64EncodedSha256("616" + "330" + "3565953" + "fjdsaFDSAefwfsalfid")
  val accountId2 = base64EncodedSha256("616" + "430" +"50180983" + "fjdsaFDSAefwfsalfid")
  val accountId3 = base64EncodedSha256("616" + "330" +"50180963" + "fjdsaFDSAefwfsalfid")
  val accountId4 = base64EncodedSha256("814" + "330" + "20102612" + "fjdsaFDSAefwfsalfid")
  val accountId5 = base64EncodedSha256("814" + "330" + "20105505" + "fjdsaFDSAefwfsalfid")*/
  val mfToken = ">,?          81433020102612"
  val username = "N7jut8d"
  val authInfoIsFirstTrue = AuthInfo("", username, mfToken, true) 
  val authInfoIsFirstFalse = AuthInfo("", username, mfToken, false)
  
  
  test("getBankAccounts works for stub"){
    val result = getBankAccounts(OutboundGetAccounts(AuthInfo("karlsid", username, ""),true, null)) //TODO ,need fix

    //getBalance is not called here
    result should be (InboundGetAccounts(AuthInfo("karlsid", username, mfToken), Status(), List(InboundAccountJune2017("", mfToken, "10", "616", accountId1, "3565953", "330", "0", "ILS", List(username), List("Owner", "Accountant", "Auditor"), "", "", "", "", List(AccountRouting("Israel Domestic","10-616-3565953")), Nil),
            InboundAccountJune2017("", mfToken, "10", "616", accountId2, "50180983", "430", "0", "ILS", List(""), List("Accountant", "Auditor"), "", "", "", "", List(AccountRouting("Israel Domestic","10-616-50180983")) , Nil),
            InboundAccountJune2017("", mfToken, "10", "616", accountId3, "50180963", "330", "0", "ILS", List(username), List("Owner", "Accountant", "Auditor"), "", "", "", "", List(AccountRouting("Israel Domestic","10-616-50180963")) , Nil),
            InboundAccountJune2017("", mfToken, "10", "814", accountId4, "20102612", "330", "0", "ILS", List(username), List("Owner", "Accountant", "Auditor"), "", "", "", "", List(AccountRouting("Israel Domestic","10-814-20102612")) , Nil),
            InboundAccountJune2017("", mfToken, "10", "814", accountId5, "20105505", "330", "0", "ILS", List(username), List("Owner", "Accountant", "Auditor"), "", "", "", "",  List(AccountRouting("Israel Domestic","10-814-20105505")), Nil))))
  }
  
  test("getBankAccountbyAccountId works for stub"){
    val result = getBankAccountbyAccountId(OutboundGetAccountbyAccountID(AuthInfo("karlsid", username, mfToken),"10",accountId1))
    result should be (InboundGetAccountbyAccountID(AuthInfo("karlsid", username, mfToken), Status(), (Some(InboundAccountJune2017("", mfToken, "10", "616", accountId1, "3565953", "330", "5541.28", "ILS", List(username), List("Owner", "Accountant", "Auditor"), "", "", "", "",  List(AccountRouting("Israel Domestic","10-616-3565953"),AccountRouting("IBAN","IL230106160000050180963")), List(AccountRule("CREDIT_LIMIT", "15000")))))))
  }
  
  test("getCoreBankAccounts gives correct result for stub"){
    getCoreBankAccounts(OutboundGetCoreBankAccounts(authInfoIsFirstTrue, List(BankIdAccountId(BankId("10"), AccountId(accountId1))))) should be (
      InboundGetCoreBankAccounts(AuthInfo("","N7jut8d",">,?          81433020102612",true),List(InternalInboundCoreAccount("", List(InboundStatusMessage("ESB","Success","0","OK"), InboundStatusMessage("MF","Success","0","OK")), "3jdVT1N-wWeawA-fTqLkr5vE0qHiQLkhjru2YvJ8F98", "", "10", AccountRouting("Israel Domestic","10-616-3565953")))))

  }
  
  test("checkBankAccountExists gives correct result for stub"){
    checkBankAccountExists(OutboundCheckBankAccountExists(authInfoIsFirstTrue, "10", accountId1)) should be (
    InboundGetAccountbyAccountID(AuthInfo("","N7jut8d",">,?          81433020102612",true), Status(), Some(InboundAccountJune2017("", ">,?          81433020102612", "10", "616", "3jdVT1N-wWeawA-fTqLkr5vE0qHiQLkhjru2YvJ8F98", "3565953", "330", "0", "ILS", List("N7jut8d"), List("Owner", "Accountant", "Auditor"), "", "", "", "", List(AccountRouting("Israel Domestic","10-616-3565953")) , List()))))
  }

  
  test("getTransactions works for Stubs first transaction"){
    val result = getTransactions(OutboundGetTransactions(AuthInfo("karlsid", username, ">,?          81433020102612"), "10", accountId1, 15, "Sat Jul 01 00:00:00 CEST 2000", "Sat Jul 01 00:00:00 CEST 2017"))
    val transactionId = "b0KL4sToinUtUN2H3be_LtYv2XRxvuRmc6PqtjFqNEM"
    result.data.head should be (InternalTransaction(
      transactionId,
      accountId1,
      "-1312.21",
      "10",
      getUtcDatefromLeumiLocalDate("20160201"),
      "",
      "פרעון הלוואה",
      "ILS",
      "פרעון הלוואה",
      "-7192.83",
      "ILS",
      "20160201",
      "12",
      "karlsid"
    ))
  }
  
  test("getTransaction works for TransactionId b0KL4sToinUtUN2H3be_LtYv2XRxvuRmc6PqtjFqNEM"){
    val result = getTransaction(OutboundGetTransaction(authInfoIsFirstTrue, defaultBankId, accountId1, "b0KL4sToinUtUN2H3be_LtYv2XRxvuRmc6PqtjFqNEM"))
  }

  
  test("getCustomer gives correct result for stubs , isFirst = true"){ 
    val customerId = base64EncodedSha256(username + config.getString("salt.global"))
    val result = getCustomer(OutboundGetCustomersByUserId(AuthInfo("karlsid", username, mfToken, true)))should be
    InboundGetCustomersByUserId(AuthInfo("karlsid", username, mfToken), Status(), List(InternalFullCustomer(customerId = customerId, bankId = "10", number = username, legalName = "??????????????" + " " + "????????????????????", mobileNumber = "", email = "", faceImage = CustomerFaceImage(null, "notinthiscall"), dateOfBirth=simpleTransactionDateFormat.parse("19481231"), relationshipStatus = "", dependents = 0, dobOfDependents = List(null), highestEducationAttained = " ", employmentStatus = "", creditRating = CreditRating("notfromthiscall","notfromthiscall"), creditLimit =  AmountOfMoney(defaultCurrency, "15000"), kycStatus = true, lastOkDate = simpleLastLoginFormat.parse("20170611" + "120257"))))
  }

  test("getCustomer gives correct result for stubs , isFirst = false"){
    val customerId = base64EncodedSha256(username + config.getString("salt.global"))
    val result = getCustomer(OutboundGetCustomersByUserId(AuthInfo("karlsid", username, mfToken, false)))should be
    InboundGetCustomersByUserId(AuthInfo("karlsid", username, mfToken), Status(), List(InternalFullCustomer(customerId = customerId, bankId = "10", number = username, legalName = "??????????????" + " " + "????????????????????", mobileNumber = "", email = "", faceImage = CustomerFaceImage(null, "notinthiscall"), dateOfBirth=simpleTransactionDateFormat.parse("19481231"), relationshipStatus = "", dependents = 0, dobOfDependents = List(null), highestEducationAttained = " ", employmentStatus = "", creditRating = CreditRating("notfromthiscall","notfromthiscall"), creditLimit =  AmountOfMoney(defaultCurrency, "15000"), kycStatus = true, lastOkDate = simpleLastLoginFormat.parse("20170611" + "120257"))))
  }
  
  test("createTransaction - Transfer to Account does not break"){
    val result = createTransaction(OutboundCreateTransaction(
      authInfo = AuthInfo("",username,mfToken),
      fromAccountBankId = "",
      fromAccountId = accountId1,
      transactionRequestType= TransactionRequestTypes.TRANSFER_TO_ACCOUNT.toString,
      transactionChargePolicy= "",
      transactionRequestCommonBody =  TransactionRequestBodyTransferToAccount(
        value = com.tesobe.obp.june2017.AmountOfMoneyJsonV121("ILS","10"),
        description = "",
        transfer_type =  "RealTime",
        future_date = "",
        to = ToAccountTransferToAccountJson(
          name = "",
          bank_code = "",
          branch_number = "",
          account = ToAccountTransferToAccountAccountJson(
          number = "",
          iban = ""
        ))),
      toCounterpartyId = "",
      toCounterpartyName = "",
      toCounterpartyCurrency = "",
      toCounterpartyRoutingAddress = "",
      toCounterpartyRoutingScheme = "",
      toCounterpartyBankRoutingAddress = "",
      toCounterpartyBankRoutingScheme = ""

    ))
    result should be (InboundCreateTransactionId(AuthInfo("",username,mfToken),InternalTransactionId("",List(InboundStatusMessage("ESB","Success","0","OK")),"")))
  }

  test("createTransaction - Transfer to Account fails with invalid futuredate"){
    an [InvalidTimeException] should be thrownBy  createTransaction(OutboundCreateTransaction(
      authInfo = AuthInfo("",username,mfToken),
      fromAccountBankId = "",
      fromAccountId = accountId1,
      transactionRequestType= TransactionRequestTypes.TRANSFER_TO_ACCOUNT.toString,
      transactionChargePolicy= "",
      transactionRequestCommonBody =  TransactionRequestBodyTransferToAccount(
        value = com.tesobe.obp.june2017.AmountOfMoneyJsonV121("ILS","10"),
        description = "",
        transfer_type =  "RealTime",
        future_date = "20170743l",
        to = ToAccountTransferToAccountJson(
          name = "",
          bank_code = "",
          branch_number = "",
          account = ToAccountTransferToAccountAccountJson(
            number = "",
            iban = ""
          ))),
      toCounterpartyId = "",
      toCounterpartyName = "",
      toCounterpartyCurrency = "",
      toCounterpartyRoutingAddress = "",
      toCounterpartyRoutingScheme = "",
      toCounterpartyBankRoutingAddress = "",
      toCounterpartyBankRoutingScheme = ""
    ))
  }
  
  test("getCounterpartiesForAccount returns correct result for test stub"){
    val result = getCounterpartiesForAccount(OutboundGetCounterparties(authInfoIsFirstTrue,
      InternalOutboundGetCounterparties("10", accountId1,"")))
    
   result should be (InboundGetCounterparties(AuthInfo("","N7jut8d",">,?          81433020102612",true), Status(), List(
           InternalCounterparty("", "         יעכטגאט", "10", "3jdVT1N-wWeawA-fTqLkr5vE0qHiQLkhjru2YvJ8F98", "", "I8SSjafxp6UOiUXMda-jIRqaloepG4Mf0ECWv4pm7-I", "", "7571", "", "10", "", "601", true, "                     יעכטגאט", "", "", List(PostCounterpartyBespoke("",""), PostCounterpartyBespoke("",""))), 
           InternalCounterparty("", "    חבש ןר לאיחי", "10", "3jdVT1N-wWeawA-fTqLkr5vE0qHiQLkhjru2YvJ8F98", "", "CWqswR6-g51DnnjhE5GEL5ijNB3x0ytTh7iO8riAPm8", "", "122573", "", "10", "", "601", true, "                 ךורא רצק םש", "", "", List(PostCounterpartyBespoke("",""), PostCounterpartyBespoke("",""))), 
           InternalCounterparty("", "            סכלא", "10", "3jdVT1N-wWeawA-fTqLkr5vE0qHiQLkhjru2YvJ8F98", "", "Q_VzYzLmPVNvmtXwH8iTjADSzr5SKmf2ELgWk0UALEk", "", "100727", "", "12", "", "773", true, "", "", "", List(PostCounterpartyBespoke("",""), PostCounterpartyBespoke("",""))), 
           InternalCounterparty("", "             טטט", "10", "3jdVT1N-wWeawA-fTqLkr5vE0qHiQLkhjru2YvJ8F98", "", "MRWD8s_VZalJvMTblRLKSu-dBAMElUliHt8oCQm1HkE", "", "1089", "", "13", "", "63", true, "", "", "", List(PostCounterpartyBespoke("",""), PostCounterpartyBespoke("",""))),
           InternalCounterparty("", "             לעי", "10", "3jdVT1N-wWeawA-fTqLkr5vE0qHiQLkhjru2YvJ8F98", "", "lymTJ1uhjH3e8ri6kujdZDPrSbdWAVf3BsmqPBR9cTw", "", "639257", "", "52", "", "188", true, "", "", "", List(PostCounterpartyBespoke("",""), PostCounterpartyBespoke("","")))
         )))


  }
  
  test("getCounterpartyByCounterpartyId returns correct result for ")  {
    val result = getCounterpartyByCounterpartyId(OutboundGetCounterpartyByCounterpartyId(authInfoIsFirstTrue,
      OutboundGetCounterpartyById("I8SSjafxp6UOiUXMda-jIRqaloepG4Mf0ECWv4pm7-I")))
    
    result should be (InboundGetCounterparty(authInfoIsFirstTrue, Status(), Some(InternalCounterparty("", "         יעכטגאט", "10", "3jdVT1N-wWeawA-fTqLkr5vE0qHiQLkhjru2YvJ8F98", "", "I8SSjafxp6UOiUXMda-jIRqaloepG4Mf0ECWv4pm7-I", "", "7571", "", "10", "", "601", true, "                     יעכטגאט", "", "", List(PostCounterpartyBespoke("",""), PostCounterpartyBespoke("",""))))))
  }

  test("getCounterparty returns correct result for ")  {
    val result = getCounterparty(OutboundGetCounterparty(authInfoIsFirstTrue, "10",
      
      accountId1,"I8SSjafxp6UOiUXMda-jIRqaloepG4Mf0ECWv4pm7-I"))

    result should be (InboundGetCounterparty(authInfoIsFirstTrue, Status(), Some(InternalCounterparty("", "         יעכטגאט", "10", "3jdVT1N-wWeawA-fTqLkr5vE0qHiQLkhjru2YvJ8F98", "", "I8SSjafxp6UOiUXMda-jIRqaloepG4Mf0ECWv4pm7-I", "", "7571", "", "10", "", "601", true, "                     יעכטגאט", "", "", List(PostCounterpartyBespoke("",""), PostCounterpartyBespoke("",""))))))
  }
  
  
  test("getBranches returns correct result for first branch of local test stub") {
    val result = getBranches(OutboundGetBranches(authInfoIsFirstTrue, "10"))
    result.data.head should be (

      InboundBranchVJune2017(BranchId("957"),BankId("10"),"אבן יהודה",Address("רח' המייסדים 64","","","אבן יהודה",None,"","4050000","IL"),Location(34.88898,32.2697),Meta(License("pddl","Open Data Commons Public Domain Dedication and License (PDDL)")),None,Some(Lobby(List(OpeningTimes("00:00","00:00"), OpeningTimes("00:00","00:00")),List(OpeningTimes("08:30","13:00"), OpeningTimes("16:00","18:15")),List(OpeningTimes("08:30","14:30"), OpeningTimes("00:00","00:00")),List(OpeningTimes("08:30","14:30"), OpeningTimes("00:00","00:00")),List(OpeningTimes("08:30","13:00"), OpeningTimes("16:00","18:15")),List(OpeningTimes("08:30","12:30"), OpeningTimes("00:00","00:00")),List(OpeningTimes("00:00","00:00"), OpeningTimes("00:00","00:00")))),None,Some(true),Some("נגישות לכסא גלגלים" + "," +"לולאת השראה ללקויי שמיעה" + "," +"כספומט מותאם ללקויי ראייה" + "," +"עמדת מידע מותאמת ללקויי ראייה" + "," +"שירותי נכים בסניף"),None,None,Some(""))
    )
  }
  
  test("getTransactionRequests returns correct result for first transaction request of test stub"){
      getTransactionRequests(OutboundGetTransactionRequests210(authInfoIsFirstTrue,OutboundTransactionRequests(accountId1,"","","","","","","",""))).data.head should be (
      TransactionRequest(TransactionRequestId("lXyL7qzIwO4MEowJ8gTccddZ68jVF-Fju2DQiQBiN5Y"),"_FUTURE_STANDING_ORDER",TransactionRequestAccount("10","3jdVT1N-wWeawA-fTqLkr5vE0qHiQLkhjru2YvJ8F98"),TransactionRequestBody(TransactionRequestAccount("",""),AmountOfMoney("ILS","-262.73"),"ה.ק.חסכון"),null,"","COMPLETED",null,getUtcDatefromLeumiLocalDate("20170521"),TransactionRequestChallenge("",0,""),TransactionRequestCharge("",AmountOfMoney("ILS","0")),"",CounterpartyId(""),"ה.ק.חסכון",BankId("10"),AccountId("3jdVT1N-wWeawA-fTqLkr5vE0qHiQLkhjru2YvJ8F98"),ViewId(""),"","","","",false))
  }
  
  test("getBranch works for local test stub"){
    getBranch(OutboundGetBranch(authInfoIsFirstTrue, "10", "957")).data should be (
    Some(InboundBranchVJune2017(BranchId("957"),BankId("10"),"אבן יהודה",Address("רח' המייסדים 64","","","אבן יהודה",None,"","4050000","IL"),Location(34.88898,32.2697),Meta(License("pddl","Open Data Commons Public Domain Dedication and License (PDDL)")),None,Some(Lobby(List(OpeningTimes("00:00","00:00"), OpeningTimes("00:00","00:00")),List(OpeningTimes("08:30","13:00"), OpeningTimes("16:00","18:15")),List(OpeningTimes("08:30","14:30"), OpeningTimes("00:00","00:00")),List(OpeningTimes("08:30","14:30"), OpeningTimes("00:00","00:00")),List(OpeningTimes("08:30","13:00"), OpeningTimes("16:00","18:15")),List(OpeningTimes("08:30","12:30"), OpeningTimes("00:00","00:00")),List(OpeningTimes("00:00","00:00"), OpeningTimes("00:00","00:00")))),None,Some(true),Some("נגישות לכסא גלגלים" + "," +"לולאת השראה ללקויי שמיעה" + "," +"כספומט מותאם ללקויי ראייה" + "," +"עמדת מידע מותאמת ללקויי ראייה" + "," +"שירותי נכים בסניף"),None,None,Some(""))))
  }
  
  test("getAtms returns correct results"){
    getAtms(OutboundGetAtms(authInfoIsFirstTrue, "10")).data.head should be (
      InboundAtmJune2017(AtmId("957"),BankId("10"),"אבן יהודה",
        Address("רח' המייסדים 64","","","אבן יהודה",None,"","4050000","IL"),
        Location(34.88898,32.2697),
        Meta(License("pddl","Open Data Commons Public Domain Dedication and License (PDDL)")),
        Some("00:00"),
        Some("00:00"),
        Some("08:30"),
        Some("18:15"),
        Some("08:30"),
        Some("00:00"),
        Some("08:30"),
        Some("00:00"),
        Some("08:30"),
        Some("18:15"),
        Some("08:30"),
        Some("00:00"),
        Some("00:00"),
        Some("00:00"),
        Some(true),
        Some(""),
        Some("כספומט מותאם ללקויי ראייה"),
        Some(true))
    )
  }
  
  test("getAtm works for AtmId 957"){
    val result = getAtm(OutboundGetAtm(authInfoIsFirstTrue, "10", "957")).data should be (
      Some(InboundAtmJune2017(AtmId("957"),BankId("10"),"אבן יהודה",
        Address("רח' המייסדים 64","","","אבן יהודה",None,"","4050000","IL"),
        Location(34.88898,32.2697),
        Meta(License("pddl","Open Data Commons Public Domain Dedication and License (PDDL)")),
        Some("00:00"),
        Some("00:00"),
        Some("08:30"),
        Some("18:15"),
        Some("08:30"),
        Some("00:00"),
        Some("08:30"),
        Some("00:00"),
        Some("08:30"),
        Some("18:15"),
        Some("08:30"),
        Some("00:00"),
        Some("00:00"),
        Some("00:00"),
        Some(true),
        Some(""),
        Some("כספומט מותאם ללקויי ראייה"),
        Some(true)))
    )
  }
  


}

