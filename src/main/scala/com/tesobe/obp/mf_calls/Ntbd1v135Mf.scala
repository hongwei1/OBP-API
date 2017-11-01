package com.tesobe.obp

import com.tesobe.obp.HttpClient.{makePostRequest, createPapiErrorResponseFromSoapError}
import com.tesobe.obp.JoniMf.replaceEmptyObjects
import com.typesafe.scalalogging.StrictLogging
import net.liftweb.json.{JValue, parse}
import net.liftweb.json.JsonParser.ParseException


object Ntbd1v135Mf extends StrictLogging{

  def getNtbd1v135Mf(branch: String,
                     accountType: String,
                     accountNumber: String,
                     username: String,
                     cbsToken: String,
                     mobileNumberOfMoneySender:String,
                     mobileNumberOfMoneyReceiver: String,
                     description: String,
                     transferAmount: String): Either[PAPIErrorResponse,Ntbd1v135] = {

    val path = "/ESBLeumiDigitalBank/PAPI/v1.0/NTBD/1/135/01.01"
    
    val json: JValue =parse(s"""
    {
    	"NTBD_1_135": {
    		"NtdriveCommonHeader": {
    			"KeyArguments": {
    				"Branch": "$branch",
    				"AccountType": "$accountType",
    				"AccountNumber": "$accountNumber"
    			},
    			"AuthArguments": {
    				"User": "$username"
    				"MFToken": "$cbsToken"
    			}
    		},
    		"KELET_1351": {
    			"K135_MISPAR_KARTIS": "0000000000000000",
    			"K135_TOKEF_KARTIS": "",
    			"K135_TIKRAT_KARTIS": "",
    			"K135_NAYAD_BAAL_CHESHBON": "$mobileNumberOfMoneySender",
    			"K135_SCHUM": "",
    			"K135_MATRAT_HAAVARA": "$description",
    			"K135_MISPAR_ZIHUY_MUTAV": "",
    			"K135_SUG_ZIHUY_MUTAV": "",
    			"K135_ERETZ_MUTAV": "",
    			"K135_SHEM_MUTAV": "",
    			"K135_TARICH_LEDA_MUTAV": "",
    			"K135_NAYAD_MUTAV": "$mobileNumberOfMoneyReceiver",
    			"K135_SCHUM_HADASH": "$transferAmount"
    		}
    	}
    }""")

    val result = makePostRequest(json, path)
    implicit val formats = net.liftweb.json.DefaultFormats
    try {
      Right(parse(replaceEmptyObjects(result)).extract[Ntbd1v135])
    } catch {
      case e: net.liftweb.json.MappingException => Left(parse(replaceEmptyObjects(result)).extract[PAPIErrorResponse])
      case e: net.liftweb.json.JsonParser.ParseException => Left(createPapiErrorResponseFromSoapError(result))
    }  }

}
