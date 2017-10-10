package com.tesobe.obp
import com.tesobe.obp.HttpClient.makePostRequest
import com.tesobe.obp.JoniMf.replaceEmptyObjects
import com.typesafe.scalalogging.StrictLogging
import net.liftweb.json.JValue
import net.liftweb.json.JsonParser.parse

object Ntg6IMf extends StrictLogging{
  
    def getNtg6I(
                 branch: String,
                 accountType: String,
                 accountNumber: String,
                 cbsToken: String
               ) = {

      val path = "/ESBLeumiDigitalBank/PAPI/v1.0/NTG6/I/000/01.04"
      logger.debug("parsing json for getNtg6I")
      val json: JValue = parse(s"""
      {
        "NTG6_I_000": {
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
      }""")

      val result = makePostRequest(json, path)
      
      implicit val formats = net.liftweb.json.DefaultFormats
      parse(replaceEmptyObjects(result)).extract[Ntg6IandK]
    }
}
