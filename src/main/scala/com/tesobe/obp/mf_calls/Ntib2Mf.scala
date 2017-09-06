package com.tesobe.obp

import com.typesafe.scalalogging.StrictLogging
import net.liftweb.json.JValue
import net.liftweb.json.JsonAST.compactRender
import net.liftweb.json.JsonDSL._
import net.liftweb.json.JsonParser._
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.DefaultHttpClient

/**
  * Created by work on 6/12/17.
  */
object Ntib2Mf extends Config with StrictLogging{
  
  def getNtib2Mf(mainframe: String): String = {
    val source = scala.io.Source.fromResource(mainframe)
    val lines = try source.mkString finally source.close()
    lines
  }

  def getNtib2MfHttpApache(branch: String, accountType: String, accountNumber: String, username: String, cbsToken: String): String = {

    val client = new DefaultHttpClient()
    val url = config.getString("bankserver.url")

    //OBP-Adapter_Leumi/Doc/MFServices/NTIB_2_000 Sample.txt
    val post = new HttpPost(url + "/ESBLeumiDigitalBank/PAPI/v1.0/NTIB/2/000/01.01")
    post.addHeader("Content-Type", "application/json;charset=utf-8")
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
    
    val jsonBody = new StringEntity(compactRender(json))
    post.setEntity(jsonBody)
    logger.debug("NTIB_2_000--Request : "+post.toString +"\n Body is :" + compactRender(json))
    val response = client.execute(post)
    val inputStream = response.getEntity.getContent
    val result = scala.io.Source.fromInputStream(inputStream).mkString
    response.close()
    logger.debug("NTIB_2_000--Response : "+response.toString+ "\n Body is :"+result)
    result
  }
  
  
  
  def getIban(branch: String, accountType: String, accountNumber: String, username: String, cbsToken: String) = {
    val parser = (p: Parser) => {
      def parse: String = p.nextToken match {
        case FieldStart("TS00_IBAN") => p.nextToken match {
          case StringVal(token) => token
          case _ => p.fail("expected string")
        }
        case End => p.fail("no field named 'TS00_IBAN'")
        case _ => parse
      }

      parse
    }
    parse(getNtib2MfHttpApache(branch,  accountType,  accountNumber, username, cbsToken), parser)
  }
}
