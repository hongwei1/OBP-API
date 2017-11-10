package com.tesobe.obp

import com.tesobe.obp.HttpClient.makePostRequest
import com.tesobe.obp.JoniMf.replaceEmptyObjects
import com.typesafe.scalalogging.StrictLogging
import net.liftweb.json.JsonAST.compactRender
import net.liftweb.json.{JValue, parse}
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.DefaultHttpClient


object Nt1cTMf extends Config with StrictLogging{
  //Read file To Simulate Mainframe Call
  implicit val formats = net.liftweb.json.DefaultFormats
  def getNt1cTMf(mainframe: String): String = {
    val source = scala.io.Source.fromResource(mainframe)
    val lines = try source.mkString finally source.close()
    lines
  }

  def getNt1cT(username: String,
                           branch: String,
                           accountType: String,
                           accountNumber: String,
                           cbsToken: String, 
                           startDate: List[String],
                           endDate: List[String], 
                           maxNumberOfTransactions: String): Either[PAPIErrorResponse, Nt1cT] = {
    
    //OBP-Adapter_Leumi/Doc/MFServices/NT1C_T_000 Sample.txt
    val path = "/ESBLeumiDigitalBank/PAPI/v1.0/NT1C/T/000/01.02"
    val json: JValue =parse(s"""
    { 
       "NT1C_T_000": {
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
          },
        "KELET_TAARICHIM": {
          "KELET_ME_TAAR": {
            "KELET_ME_YYYY": "${startDate(0)}",
            "KELET_ME_MM"  : "${startDate(1)}",
            "KELET_ME_DD"  : "${startDate(2)}"
          },
          "KELET_AD_TAAR": {
            "KELET_AD_YYYY": "${endDate(0)}",
            "KELET_AD_MM": "${endDate(1)}",
            "KELET_AD_DD": "${endDate(2)}"
          },
          "KELET_TN_MIS_TNUOT": "$maxNumberOfTransactions"
        }
      }
    }""")

    val result = makePostRequest(json, path)
    implicit val formats = net.liftweb.json.DefaultFormats
    try {
      Right(parse(replaceEmptyObjects(result)).extract[Nt1cT])
    } catch {
      case e: net.liftweb.json.MappingException => Left(parse(replaceEmptyObjects(result)).extract[PAPIErrorResponse])
    }
  }

  
  
  
}
