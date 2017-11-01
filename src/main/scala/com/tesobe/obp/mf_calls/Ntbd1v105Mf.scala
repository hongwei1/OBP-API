package com.tesobe.obp
import com.tesobe.obp.HttpClient.makePostRequest
import com.tesobe.obp.JoniMf.replaceEmptyObjects
import com.typesafe.scalalogging.StrictLogging
import net.liftweb.json.{JValue, parse}


object Ntbd1v105Mf extends StrictLogging{

  def getNtbd1v105Mf(branch: String,
                     accountType: String,
                     accountNumber: String,
                     cbsToken: String,
                     cardNumber: String,
                     cardExpirationDate: String,
                     cardWithdrawalLimit: String,
                     mobileNumberOfMoneySender:String,
                     amount: String,
                     description: String,
                     idNumber: String,
                     idType: String,
                     nameOfMoneyReceiver: String,
                     birthDateOfMoneyReceiver: String,
                     mobileNumberOfMoneyReceiver: String): Either[PAPIErrorResponse,Ntbd1v105] = {

    val path = "/ESBLeumiDigitalBank/PAPI/v1.0/NTBD/1/105/01.01"
    
    val json: JValue =parse(s"""
{
      "NTBD_1_105": {
        "NtdriveCommonHeader": {
        "KeyArguments": {
        "Branch": "$branch",
        "AccountType": "$accountType",
        "AccountNumber": "$accountNumber"
      },
        "AuthArguments": {
        "MFToken": "$cbsToken"
      }
      },
        "KELET_1351": {
        "K135_MISPAR_KARTIS": "$cardNumber",
        "K135_TOKEF_KARTIS": "$cardExpirationDate",
        "K135_TIKRAT_KARTIS": "$cardWithdrawalLimit",
        "K135_NAYAD_BAAL_CHESHBON": "$mobileNumberOfMoneySender",
        "K135_SCHUM": "$amount",
        "K135_MATRAT_HAAVARA": "$description",
        "K135_MISPAR_ZIHUY_MUTAV": "$idNumber",
        "K135_SUG_ZIHUY_MUTAV": "$idType",
        "K135_ERETZ_MUTAV": "2121",
        "K135_SHEM_MUTAV": "$nameOfMoneyReceiver",
        "K135_TARICH_LEDA_MUTAV": "$birthDateOfMoneyReceiver",
        "K135_NAYAD_MUTAV": "$mobileNumberOfMoneyReceiver",
        "K135_SCHUM_HADASH": ""
      }
      }
    }""")

    val result = makePostRequest(json, path)
    implicit val formats = net.liftweb.json.DefaultFormats
    try {
      Right(parse(replaceEmptyObjects(result)).extract[Ntbd1v105])
    } catch {
      case e: net.liftweb.json.MappingException => Left(parse(replaceEmptyObjects(result)).extract[PAPIErrorResponse])
    }
  }

}
