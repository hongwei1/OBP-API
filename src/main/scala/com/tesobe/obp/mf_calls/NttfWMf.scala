package com.tesobe.obp

import com.tesobe.obp.JoniMf.{config, replaceEmptyObjects}
import com.tesobe.obp.HttpClient.makePostRequest
import com.typesafe.scalalogging.StrictLogging
import net.liftweb.json.JValue
import net.liftweb.json.JsonParser.parse

object NttfWMf extends StrictLogging{

  def getNttfWMf(branch: String,
                 accountType: String,
                 accountNumber: String,
                 cbsToken: String,
                          ) = {

    val path = "/ESBLeumiDigitalBank/PAPI/v1.0/NTTF/W/000/01.01"

    val json: JValue =parse(s"""
     {
      "NTTF_W_000": {
        "NtdriveCommonHeader": {
          "KeyArguments": {
            "Branch": "$branch",
            "AccountType": "$accountType",
            "AccountNumber": "$accountNumber"
          },
          "AuthArguments": {
            "MFToken": "$cbsToken"
          }
        }
      }
     }
    """)
    
    val result = makePostRequest(json, path)

    implicit val formats = net.liftweb.json.DefaultFormats
    parse(replaceEmptyObjects(result)).extract[NttfW]
  }

}