package com.tesobe.obp

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

  def getNt1cTMfHttpApache(username: String, branch: String, accountType: String, accountNumber: String, cbsToken: String, startDate: List[String], endDate: List[String], maxNumberOfTransactions: String): String = {
  
    val client = new DefaultHttpClient()
    val url = config.getString("bankserver.url")

    //OBP-Adapter_Leumi/Doc/MFServices/NT1C_T_000 Sample.txt
    val post = new HttpPost(url + "/ESBLeumiDigitalBank/PAPI/v1.0/NT1C/T/000/01.02")
    post.addHeader("Content-Type","application/json;charset=utf-8")
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
    
    val jsonBody = new StringEntity(compactRender(json))
    post.setEntity(jsonBody)
    logger.debug("NT1C_T_000--Request : "+post.toString +"\n Body is :" + compactRender(json))
    val response = client.execute(post)
    val inputStream = response.getEntity.getContent
    val result = scala.io.Source.fromInputStream(inputStream).mkString
    response.close()
    logger.debug("NT1C_T_000--Response : "+response.toString+ "\n Body is :"+result)
    result
  }
  //@param: Filepath for json result stub
  def getCompletedTransactions(username: String, branchId: String, accountType: String, accountNumber: String, cbsToken: String, startDate: List[String], endDate: List[String], maxNumberOfTransactions: String): Nt1cT = {
    val json = replaceEmptyObjects(getNt1cTMfHttpApache(username, branchId, accountType, accountNumber, cbsToken, startDate, endDate, maxNumberOfTransactions))
    val jsonAst = parse(json)
    logger.debug(jsonAst.toString)
    jsonAst.extract[Nt1cT]
  }
  
}
