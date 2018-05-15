package com.tesobe.obp

import com.tesobe.obp.HttpClient.makePostRequest
import com.tesobe.obp.JoniMf.replaceEmptyObjects
import com.typesafe.scalalogging.StrictLogging
import net.liftweb.json.JValue
import net.liftweb.json.JsonParser.parse

object NttfWMf extends Config with StrictLogging{

  def getNttfWMf(branch: String,
                 accountType: String,
                 accountNumber: String,
                 cbsToken: String
                          ) = {

    val path = config.getString("backendCalls.NTTF_W_000")

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