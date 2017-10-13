package com.tesobe.obp

import com.tesobe.obp.HttpClient.makePostRequest
import com.tesobe.obp.JoniMf.replaceEmptyObjects
import com.typesafe.scalalogging.StrictLogging
import net.liftweb.json.JValue
import net.liftweb.json.JsonParser.parse

object Ntg6KMf extends StrictLogging{
  
    def getNtg6K(
                 branch: String,
                 accountType: String,
                 accountNumber: String,
                 cbsToken: String
               ): Either[PAPIErrorResponse,Ntg6IandK] = {

      val path = "/ESBLeumiDigitalBank/PAPI/v1.0/NTG6/K/000/01.04"
      logger.debug("parsing json for getNtg6K")
      val json: JValue = parse(s"""
      {
        "NTG6_K_000": {
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
      try {
        Right(parse(replaceEmptyObjects(result)).extract[Ntg6IandK])
      } catch {
        case e: net.liftweb.json.MappingException  => Left(parse(replaceEmptyObjects(result)).extract[PAPIErrorResponse])
      }
    }
}
