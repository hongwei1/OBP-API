package com.tesobe.obp

import com.tesobe.obp.HttpClient.makePostRequest
import com.tesobe.obp.JoniMf.replaceEmptyObjects
import com.typesafe.scalalogging.StrictLogging
import net.liftweb.json.JValue
import net.liftweb.json.JsonParser._

object Ntlv1Mf extends StrictLogging{

  def getNtlv1Mf(username: String, idNumber: String, idType: String, cbsToken: String): Ntlv1  = {
  
    val path = "/ESBLeumiDigitalBank/PAPI/v1.0/NTLV/1/000/01.01"

    val json: JValue = parse(s"""
    {
      "NTLV_1_000": {
        "NtdriveCommonHeader": {
          "KeyArguments": {
            "Branch": "000",
            "IDNumber": "$idNumber",
            "IDType": "$idType",
            "IDCounty": "2121" 
          },
          "AuthArguments": {
            "User": "$username"
            "MFToken":"$cbsToken"
          }
        }
      }
    }
    """)
    val result = makePostRequest(json, path)
    

    implicit val formats = net.liftweb.json.DefaultFormats
    parse(replaceEmptyObjects(result)).extract[Ntlv1]
  }

}
