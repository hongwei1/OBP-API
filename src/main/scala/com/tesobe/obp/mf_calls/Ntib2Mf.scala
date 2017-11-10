package com.tesobe.obp

import com.tesobe.obp.HttpClient.makePostRequest
import com.tesobe.obp.JoniMf.replaceEmptyObjects
import com.typesafe.scalalogging.StrictLogging
import net.liftweb.json.{JValue, parse}
import net.liftweb.json.JsonParser._

/**
  * Created by work on 6/12/17.
  */
object Ntib2Mf extends Config with StrictLogging{
  

  def getNtib2Mf(branch: String, accountType: String, accountNumber: String, username: String, cbsToken: String): Either[PAPIErrorResponse,Ntib2] = {

    val path = "/ESBLeumiDigitalBank/PAPI/v1.0/NTIB/2/000/01.01"

    val json: JValue = parse(s"""
    {
      "NTIB_2_000": {
        "NtdriveCommonHeader": {
          "KeyArguments": {
            "Branch": "$branch",
            "AccountType": "$accountType",
            "AccountNumber": "$accountNumber"
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
    try {
      Right(parse(replaceEmptyObjects(result)).extract[Ntib2])
    } catch {
      case e: net.liftweb.json.MappingException => Left(parse(replaceEmptyObjects(result)).extract[PAPIErrorResponse])
    }
  }

}
