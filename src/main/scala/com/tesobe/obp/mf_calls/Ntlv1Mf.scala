package com.tesobe.obp

import com.tesobe.obp.JoniMf.{config, replaceEmptyObjects}
import com.tesobe.obp.HttpClient.makePostRequest
import com.typesafe.scalalogging.StrictLogging
import net.liftweb.json.JValue
import net.liftweb.json.JsonAST.compactRender
import net.liftweb.json.JsonDSL._
import net.liftweb.json.JsonParser._
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.DefaultHttpClient

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
