package com.tesobe.obp

import com.tesobe.obp.JoniMf.replaceEmptyObjects
import com.tesobe.obp.HttpClient.makePostRequest
import com.typesafe.scalalogging.StrictLogging
import net.liftweb.json.JValue
import net.liftweb.json.JsonParser._


/**
  * Created by work on 6/12/17.
  */
object Nt1cBMf extends Config with StrictLogging{


  def getNt1cB(username: String, branch: String, accountType: String, accountNumber: String, cbsToken: String): Either[PAPIErrorResponse,Nt1cB] = {


    val path = config.getString("backendCalls.NT1C_B_000")
    val json: JValue =parse(s"""
    {
      "NT1C_B_000": {
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
      Right(parse(replaceEmptyObjects(result)).extract[Nt1cB])
    } catch {
      case e: net.liftweb.json.MappingException => Left(parse(replaceEmptyObjects(result)).extract[PAPIErrorResponse])
    }
  }

  
}
