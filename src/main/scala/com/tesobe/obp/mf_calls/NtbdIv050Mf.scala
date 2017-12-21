package com.tesobe.obp

import com.tesobe.obp.ErrorMessages.InvalidAmount
import com.tesobe.obp.HttpClient.makePostRequest
import com.tesobe.obp.JoniMf.replaceEmptyObjects
import net.liftweb.json.JValue
import net.liftweb.json.JsonParser.parse

import scala.util.control.NoStackTrace

object NtbdIv050Mf {
  
    def getNtbdIv050(branch: String,
                     accountType: String,
                     accountNumber: String,
                     cbsToken: String,
                     ntbdAv050Token: String,
                     transactionAmount: String
                    ): Either[PAPIErrorResponse, NtbdIv050] = {

      val path = "/ESBLeumiDigitalBank/PAPI/v1.0/NTBD/I/050/01.03"

      val constrainedTransactionAmount =  try { f"${transactionAmount.toDouble}%1.2f"
      } catch {
        case _: Throwable => throw new RuntimeException(InvalidAmount) with NoStackTrace
      }


      val json: JValue = parse(s"""
      {
        "NTBD_I_050": {
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
          "K050_SIYUMMUTAVIM": {
          "K050_TOKEN_S": "$ntbdAv050Token",
          "K050_SCUM_MIZTABER_S": "$constrainedTransactionAmount",
          "K050_SHLAV_PEULA_S": "2",
          "K050_MISPAR_MUTAVIM_S": "1"
        }
        }
      }""")

      val result = makePostRequest(json, path)
      implicit val formats = net.liftweb.json.DefaultFormats
      try {
        Right(parse(replaceEmptyObjects(result)).extract[NtbdIv050])
      } catch {
        case e: net.liftweb.json.MappingException => Left(parse(replaceEmptyObjects(result)).extract[PAPIErrorResponse])
      }
    }
}
