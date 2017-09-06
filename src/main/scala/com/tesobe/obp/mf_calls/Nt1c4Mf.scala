package com.tesobe.obp

import com.typesafe.scalalogging.StrictLogging
import net.liftweb.json.JsonAST.compactRender
import net.liftweb.json.{JValue, parse}
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.DefaultHttpClient


object Nt1c4Mf extends Config with StrictLogging{
  //Read file To Simulate Mainframe Call
  implicit val formats = net.liftweb.json.DefaultFormats
  def getNt1c4Mf(mainframe: String): String = {
    val source = scala.io.Source.fromResource(mainframe)
    val lines = try source.mkString finally source.close()
    lines
  }
  
  def getNt1c4MfHttpApache(branch: String, accountType: String, accountNumber: String, username: String, mfToken: String): String = {
    
    val client = new DefaultHttpClient()
    val url = config.getString("bankserver.url")
  
    //OBP-Adapter_Leumi/Doc/MFServices/NT1C_4_000 Sample.txt
    val post = new HttpPost(url + "/ESBLeumiDigitalBank/PAPI/v1.0/NT1C/4/000/01.03")
    post.addHeader("Content-Type", "application/json;charset=utf-8")
    val json: JValue = parse(s"""
      {
        "NT1C_4_000": {
          "NtdriveCommonHeader": {
            "KeyArguments": {
              "Branch": "$branch",
              "AccountType": "$accountType",
              "AccountNumber": "$accountNumber"
            },
            "AuthArguments": {
              "User": "$username"
              "MFToken":"$mfToken"
            }
          }
        }
      }""")
    
    val jsonBody = new StringEntity(compactRender(json))
    post.setEntity(jsonBody)
    logger.debug("NT1C_4_000--Request : "+post.toString +"\n Body is :" + compactRender(json))
    val response = client.execute(post)
    val inputStream = response.getEntity.getContent
    val result = scala.io.Source.fromInputStream(inputStream).mkString
    response.close()
    logger.debug("NT1C_4_000--Response : "+response.toString+ "\n Body is :"+result)
    result
  }
  //@param: Filepath for json result stub
  def getIntraDayTransactions(mainframe: String) = {
    val json = getNt1c4MfHttpApache("","","","","")
    val jsonAst = parse(json)
    val nt1c4Call: Nt1c4 = jsonAst.extract[Nt1c4]
    nt1c4Call
  }
  
}
