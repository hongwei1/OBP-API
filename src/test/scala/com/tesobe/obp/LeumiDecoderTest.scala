
package com.tesobe.obp

import com.tesobe.obp.GetBankAccounts.base64EncodedSha256
import com.tesobe.obp.Util.TransactionRequestTypes
import com.tesobe.obp.june2017.LeumiDecoder._
import com.tesobe.obp.june2017.{AmountOfMoneyJsonV121, _}

import scala.collection.immutable.List
/**
  * Created by work on 6/12/17.
  */
class LeumiDecoderTest  extends ServerSetup {

  val accountId1 = base64EncodedSha256("616" + "330" + "3565953" + "fjdsaFDSAefwfsalfid")
  val accountId2 = base64EncodedSha256("616" + "430" +"50180983" + "fjdsaFDSAefwfsalfid")
  val accountId3 = base64EncodedSha256("616" + "330" +"50180963" + "fjdsaFDSAefwfsalfid")
  val accountId4 = base64EncodedSha256("814" + "330" + "20102612" + "fjdsaFDSAefwfsalfid")
  val accountId5 = base64EncodedSha256("814" + "330" + "20105505" + "fjdsaFDSAefwfsalfid")
  val mfToken = ">,?          81433020102612"
  val username = "N7jut8d"
  val authInfoIsFirstTrue = AuthInfo("", username, mfToken, true) 
  val authInfoIsFirstFalse = AuthInfo("", username, mfToken, false)
  
  
  test("getBankAccounts works for Stub"){
    val result = getBankAccounts(OutboundGetAccounts(AuthInfo("karlsid", username, ""),true, null)) //TODO ,need fix

    //getBalance is not called here
    result should be (InboundGetAccounts(AuthInfo("karlsid", username, mfToken),
      List(InboundAccountJune2017("", List(InboundStatusMessage("ESB","Success", "0", "OK")), mfToken, "10", "616", accountId1, "3565953", "330", "0", "ILS", List(username), List("Owner", "Accountant", "Auditor"), "", "", "", "", "", "", Nil),
        InboundAccountJune2017("", List(InboundStatusMessage("ESB","Success", "0", "OK")), mfToken, "10", "616", accountId2, "50180983", "430", "0", "ILS", List(""), List("Accountant", "Auditor"), "", "", "", "", "", "", Nil),
        InboundAccountJune2017("", List(InboundStatusMessage("ESB","Success", "0", "OK")), mfToken, "10", "616", accountId3, "50180963", "330", "0", "ILS", List(username), List("Owner", "Accountant", "Auditor"), "", "", "", "", "", "", Nil),
        InboundAccountJune2017("", List(InboundStatusMessage("ESB","Success", "0", "OK")), mfToken, "10", "814", accountId4, "20102612", "330", "0", "ILS", List(username), List("Owner", "Accountant", "Auditor"), "", "", "", "", "", "", Nil),
        InboundAccountJune2017("", List(InboundStatusMessage("ESB","Success", "0", "OK")), mfToken, "10", "814", accountId5, "20105505", "330", "0", "ILS", List(username), List("Owner", "Accountant", "Auditor"), "", "", "", "", "", "", Nil))))
  }
  
  test("getBankAccountbyAccountId works for Stub"){
    val result = getBankAccountbyAccountId(OutboundGetAccountbyAccountID(AuthInfo("karlsid", username, mfToken),"10",accountId1))
    result should be (InboundGetAccountbyAccountID(AuthInfo("karlsid", username, mfToken),(InboundAccountJune2017("",List(InboundStatusMessage("ESB","Success", "0", "OK")),  mfToken, "10", "616", accountId1, "3565953", "330", "5541.28", "ILS", List(username), List("Owner", "Accountant", "Auditor"), "", "", "", "", "IBAN","IL230106160000050180963", List(AccountRules("CREDIT_LIMIT", "15000"))))))
  }

  
  test("getTransactions works for Stubs first transaction"){
    val result = getTransactions(OutboundGetTransactions(AuthInfo("karlsid", username, ">,?          81433020102612"), "10", accountId1, 15, "Sat Jul 01 00:00:00 CEST 2000", "Sat Jul 01 00:00:00 CEST 2017"))
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

  
  test("getCustomer gives correct result for stubs , isFirst = true"){ 
    val customerId = base64EncodedSha256(username + config.getString("salt.global"))
    val result = getCustomer(OutboundGetCustomersByUserId(AuthInfo("karlsid", username, mfToken, true)))should be
    InboundGetCustomersByUserId(AuthInfo("karlsid", username, mfToken), List(InternalFullCustomer(status = "",
      errorCode = "",
      backendMessages = List(InboundStatusMessage("","","","")),
      customerId = customerId,
      bankId = "10",
      number = username,
      legalName = "??????????????" + " " + "????????????????????",
      mobileNumber = "",
      email = "",
      faceImage = CustomerFaceImage(null, "notinthiscall"),
      dateOfBirth= simpleTransactionDateFormat.parse("19481231"),
      relationshipStatus = "",
      dependents = 0,
      dobOfDependents = List(null),
      highestEducationAttained = " ",
      employmentStatus = "",
      creditRating = CreditRating("notfromthiscall","notfromthiscall"),
      creditLimit =  AmountOfMoney(defaultCurrency, "15000"),
      kycStatus = true,
      lastOkDate = simpleLastLoginFormat.parse("20170611" + "120257")
    )))
  }

  test("getCustomer gives correct result for stubs , isFirst = false"){
    val customerId = base64EncodedSha256(username + config.getString("salt.global"))
    val result = getCustomer(OutboundGetCustomersByUserId(AuthInfo("karlsid", username, mfToken, false)))should be
    InboundGetCustomersByUserId(AuthInfo("karlsid", username, mfToken), List(InternalFullCustomer(status = "",
      errorCode = "",
      backendMessages = List(InboundStatusMessage("","","","")),
      customerId = customerId,
      bankId = "10",
      number = username,
      legalName = "??????????????" + " " + "????????????????????",
      mobileNumber = "",
      email = "",
      faceImage = CustomerFaceImage(null, "notinthiscall"),
      dateOfBirth= simpleTransactionDateFormat.parse("19481231"),
      relationshipStatus = "",
      dependents = 0,
      dobOfDependents = List(null),
      highestEducationAttained = " ",
      employmentStatus = "",
      creditRating = CreditRating("notfromthiscall","notfromthiscall"),
      creditLimit =  AmountOfMoney(defaultCurrency, "15000"),
      kycStatus = true,
      lastOkDate = simpleLastLoginFormat.parse("20170611" + "120257")
    )))
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
  
  test("getCounterparties returns correct result for test stub"){
    val result = getCounterpartiesForAccount(OutboundGetCounterparties(authInfoIsFirstTrue,
      InternalOutboundGetCounterparties("10", accountId1,"")))
    
   result should be (InboundGetCounterparties(AuthInfo("","N7jut8d",">,?          81433020102612",true),
      List(
        InternalCounterparty("",List(InboundStatusMessage("","","","")),"","         יעכטגאט","10","3jdVT1N-wWeawA-fTqLkr5vE0qHiQLkhjru2YvJ8F98","","kk9xisFY6zFRdKkKMSlHFwGYvEvzxT4o9wXg42O-ArE","","7571","","10","","601",true,"                     יעכטגאט","","",List(PostCounterpartyBespoke("",""), PostCounterpartyBespoke("",""))), 
        InternalCounterparty("",List(InboundStatusMessage("","","","")),"","    חבש ןר לאיחי","10","3jdVT1N-wWeawA-fTqLkr5vE0qHiQLkhjru2YvJ8F98","","Zix0iCiIyhzHzu8UXry9uEyWqwHjdV6jONCeCfAt_HI","","122573","","10","","601",true,"                 ךורא רצק םש","","",List(PostCounterpartyBespoke("",""), PostCounterpartyBespoke("",""))), 
        InternalCounterparty("",List(InboundStatusMessage("","","","")),"","            סכלא","10","3jdVT1N-wWeawA-fTqLkr5vE0qHiQLkhjru2YvJ8F98","","g-K_lhH0LWAQgzfZaowpn4Nhf7blan1v8hFow5i1RyM","","100727","","12","","773",true,"","","",List(PostCounterpartyBespoke("",""), PostCounterpartyBespoke("",""))), 
        InternalCounterparty("",List(InboundStatusMessage("","","","")),"","             טטט","10","3jdVT1N-wWeawA-fTqLkr5vE0qHiQLkhjru2YvJ8F98","","OC8wYolmHumLENu2PEQhIj9Np5cWb0bWlTX59qXHR9A","","1089","","13","","63",true,"","","",List(PostCounterpartyBespoke("",""), PostCounterpartyBespoke("",""))),
        InternalCounterparty("",List(InboundStatusMessage("","","","")),"","             לעי","10","3jdVT1N-wWeawA-fTqLkr5vE0qHiQLkhjru2YvJ8F98","","5qocG1uPAq-LSFNUeXs0_ahvT3Hmw5QL6HcnNytGanI","","639257","","52","","188",true,"","","",List(PostCounterpartyBespoke("",""), PostCounterpartyBespoke("","")))
      )))


  }
  
  test("getCounterpartyByCounterpartyId returns correct result for ")  {
    val result = getCounterpartyByCounterpartyId(OutboundGetCounterpartyByCounterpartyId(authInfoIsFirstTrue,
      OutboundGetCounterpartyById("kk9xisFY6zFRdKkKMSlHFwGYvEvzxT4o9wXg42O-ArE")))
    
    result should be (InboundGetCounterparty(authInfoIsFirstTrue, InternalCounterparty("",List(InboundStatusMessage("","","","")),"","         יעכטגאט","10","3jdVT1N-wWeawA-fTqLkr5vE0qHiQLkhjru2YvJ8F98","","kk9xisFY6zFRdKkKMSlHFwGYvEvzxT4o9wXg42O-ArE","","7571","","10","","601",true,"                     יעכטגאט","","",List(PostCounterpartyBespoke("",""), PostCounterpartyBespoke("",""))),
    ))
  }
  
  test("getBranches returns correct result for local test stub") {
    val result = getBranches(OutboundGetBranches(authInfoIsFirstTrue, "10"))
    result.data.head should be (

      InboundBranchVJune2017("","",List(InboundStatusMessage("","","","")),BranchId("957"),BankId("10"),"אבן יהודה",Address("רח' המייסדים 64","","","אבן יהודה",None,"","4050000","IL"),Location(34.88898,32.2697),Meta(License("pddl","Open Data Commons Public Domain Dedication and License (PDDL)")),None,None,None,Some(true),Some(List("נגישות לכסא גלגלים","לולאת השראה ללקויי שמיעה","כספומט מותאם ללקויי ראייה","עמדת מידע מותאמת ללקויי ראייה","שירותי נכים בסניף")),None,None,Some(""))
    )
  }
  
  test("take a look at getTransactionRequests"){
    val result = getTransactionRequests(OutboundGetTransactionRequests210(authInfoIsFirstTrue,OutboundTransactionRequests(accountId1,"","","","","","","","")))
    println(result)

/*
    InboundGetTransactionRequests210(AuthInfo(,N7jut8d,>,?          81433020102612,true),InternalGetTransactionRequests(,List(InboundStatusMessage(ESB,Success,0,OK), InboundStatusMessage(MF,Success,0,OK)),List(TransactionRequest(lXyL7qzIwO4MEowJ8gTccddZ68jVF-Fju2DQiQBiN5Y,_FUTURE_STANDING_ORDER,TransactionRequestAccount(10,3jdVT1N-wWeawA-fTqLkr5vE0qHiQLkhjru2YvJ8F98),TransactionRequestBody(TransactionRequestAccount(,),AmountOfMoney(ILS,-262.73),ה.ק.חסכון),null,,COMPLETED,null,Sun May 21 02:00:00 CEST 2017,TransactionRequestChallenge(,0,),TransactionRequestCharge(,AmountOfMoney(ILS,0)),,,ה.ק.חסכון,10,3jdVT1N-wWeawA-fTqLkr5vE0qHiQLkhjru2YvJ8F98,,,,,,false), TransactionRequest(XAKwiEDBEQDZ80r4ucqA_xkTtrWdxH4co_xmgZJBLR8,_FUTURE_CREDIT_CARD,TransactionRequestAccount(10,3jdVT1N-wWeawA-fTqLkr5vE0qHiQLkhjru2YvJ8F98),TransactionRequestBody(TransactionRequestAccount(,),AmountOfMoney(ILS,-1875.92),לאומי ויזה י),null,,COMPLETED,null,Sun Jun 11 02:00:00 CEST 2017,TransactionRequestChallenge(,0,),TransactionRequestCharge(,AmountOfMoney(ILS,0)),,,לאומי ויזה י,10,3jdVT1N-wWeawA-fTqLkr5vE0qHiQLkhjru2YvJ8F98,,,,,,false), TransactionRequest(Fx3pZILwZYym7F9D3H5AkRC8buj9-V3LN1zpmjSlZxc,_FUTURE_CREDIT_CARD,TransactionRequestAccount(10,3jdVT1N-wWeawA-fTqLkr5vE0qHiQLkhjru2YvJ8F98),TransactionRequestBody(TransactionRequestAccount(,),AmountOfMoney(ILS,-140.00),ל.מסטרקארדי),null,,COMPLETED,null,Sun Jun 11 02:00:00 CEST 2017,TransactionRequestChallenge(,0,),TransactionRequestCharge(,AmountOfMoney(ILS,0)),,,ל.מסטרקארדי,10,3jdVT1N-wWeawA-fTqLkr5vE0qHiQLkhjru2YvJ8F98,,,,,,false), TransactionRequest(vPjL4ouJhPczssM1TwAa4eUT4L6TXkEOfJJeRu-Twkw,_FUTURE_STANDING_ORDER,TransactionRequestAccount(10,3jdVT1N-wWeawA-fTqLkr5vE0qHiQLkhjru2YvJ8F98),TransactionRequestBody(TransactionRequestAccount(,),AmountOfMoney(ILS,-1351.03),הוראת קבע י),null,,COMPLETED,null,Thu Jun 01 02:00:00 CEST 2017,TransactionRequestChallenge(,0,),TransactionRequestCharge(,AmountOfMoney(ILS,0)),,,הוראת קבע י,10,3jdVT1N-wWeawA-fTqLkr5vE0qHiQLkhjru2YvJ8F98,,,,,,false), TransactionRequest(P0QEGNdl3S-5nfE0f5VRX59gnR2Z2OdwHAaiJz7LQTo,_INTRADAY,TransactionRequestAccount(10,3jdVT1N-wWeawA-fTqLkr5vE0qHiQLkhjru2YvJ8F98),TransactionRequestBody(TransactionRequestAccount(,),AmountOfMoney(ILS,-440.00),הע. אינטרנט י),null,,COMPLETED,null,Wed Apr 05 02:00:00 CEST 2017,TransactionRequestChallenge(,0,),TransactionRequestCharge(,AmountOfMoney(ILS,0)),,,הע. אינטרנט י,10,3jdVT1N-wWeawA-fTqLkr5vE0qHiQLkhjru2YvJ8F98,,,,,,false), TransactionRequest(KTpZRxDj6tNnM9OhYSFyI-Eqbn4ewh9-qyhHrcX7lkk,_INTRADAY,TransactionRequestAccount(10,3jdVT1N-wWeawA-fTqLkr5vE0qHiQLkhjru2YvJ8F98),TransactionRequestBody(TransactionRequestAccount(,),AmountOfMoney(ILS,-1.00),הע. אינטרנט י),null,,COMPLETED,null,Wed Apr 05 02:00:00 CEST 2017,TransactionRequestChallenge(,0,),TransactionRequestCharge(,AmountOfMoney(ILS,0)),,,הע. אינטרנט י,10,3jdVT1N-wWeawA-fTqLkr5vE0qHiQLkhjru2YvJ8F98,,,,,,false), TransactionRequest(JSmoBrYB9almyWyrx2exHpYLHi2rc1XBnWPH4ZW2Kbc,_INTRADAY,TransactionRequestAccount(10,3jdVT1N-wWeawA-fTqLkr5vE0qHiQLkhjru2YvJ8F98),TransactionRequestBody(TransactionRequestAccount(,),AmountOfMoney(ILS,-2.00),הע. אינטרנט י),null,,COMPLETED,null,Wed Apr 05 02:00:00 CEST 2017,TransactionRequestChallenge(,0,),TransactionRequestCharge(,AmountOfMoney(ILS,0)),,,הע. אינטרנט י,10,3jdVT1N-wWeawA-fTqLkr5vE0qHiQLkhjru2YvJ8F98,,,,,,false), TransactionRequest(csB64WOXayYUCrbgB2DG3YVJ_HW5rlliZL110gR0kzo,_INTRADAY,TransactionRequestAccount(10,3jdVT1N-wWeawA-fTqLkr5vE0qHiQLkhjru2YvJ8F98),TransactionRequestBody(TransactionRequestAccount(,),AmountOfMoney(ILS,-100.00),הע. אינטרנט י),null,,COMPLETED,null,Wed May 17 02:00:00 CEST 2017,TransactionRequestChallenge(,0,),TransactionRequestCharge(,AmountOfMoney(ILS,0)),,,הע. אינטרנט י,10,3jdVT1N-wWeawA-fTqLkr5vE0qHiQLkhjru2YvJ8F98,,,,,,false), TransactionRequest(e40-iTQOP-tiP8hgO2J2bmycZ1EANTqy6s9000yNkaE,_INTRADAY,TransactionRequestAccount(10,3jdVT1N-wWeawA-fTqLkr5vE0qHiQLkhjru2YvJ8F98),TransactionRequestBody(TransactionRequestAccount(,),AmountOfMoney(ILS,-667.83),חשמל עצמי י),null,,COMPLETED,null,Thu May 18 02:00:00 CEST 2017,TransactionRequestChallenge(,0,),TransactionRequestCharge(,AmountOfMoney(ILS,0)),,,חשמל עצמי י,10,3jdVT1N-wWeawA-fTqLkr5vE0qHiQLkhjru2YvJ8F98,,,,,,false), TransactionRequest(jycANFm83do1WDIdDYdwtmxgXYtDcYFhXxf7kxLHlPM,_INTRADAY,TransactionRequestAccount(10,3jdVT1N-wWeawA-fTqLkr5vE0qHiQLkhjru2YvJ8F98),TransactionRequestBody(TransactionRequestAccount(,),AmountOfMoney(ILS,-126.85),בזק עצמי),null,,COMPLETED,null,Mon May 29 02:00:00 CEST 2017,TransactionRequestChallenge(,0,),TransactionRequestCharge(,AmountOfMoney(ILS,0)),,,בזק עצמי,10,3jdVT1N-wWeawA-fTqLkr5vE0qHiQLkhjru2YvJ8F98,,,,,,false))))
*/

  }
  


}

