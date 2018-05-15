package com.tesobe.obp

import com.tesobe.obp.HttpClient.makePostRequest
import com.tesobe.obp.JoniMf.replaceEmptyObjects
import com.typesafe.scalalogging.StrictLogging
import net.liftweb.json.{JValue, parse}


object Nt1c3Mf extends Config with StrictLogging {


  def getNt1c3(branch: String, accountType: String, accountNumber: String, username: String, cbsToken: String): Either[PAPIErrorResponse,Nt1c3] = {


    //OBP-Adapter_Leumi/Doc/MFServices/NT1C_4_000 Sample.txt
    val path = config.getString("backendCalls.NT1C_3_000")

    val json: JValue = parse(s"""
    {
      "NT1C_3_000": {
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
      }
      }
    }""")
    val result = makePostRequest(json, path)
    implicit val formats = net.liftweb.json.DefaultFormats
    try {
      Right(parse(replaceEmptyObjects(result)).extract[Nt1c3])
    } catch {
      case e: net.liftweb.json.MappingException => Left(parse(replaceEmptyObjects(result)).extract[PAPIErrorResponse])
    }  }

}
