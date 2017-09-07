package com.tesobe.obp

import com.tesobe.obp.Nt1c4Mf.logger
import com.tesobe.obp.Ntib2Mf.getNtib2Mf
import com.typesafe.scalalogging.StrictLogging
import net.liftweb.json.JValue
import net.liftweb.json.JsonAST.compactRender
import net.liftweb.json.JsonDSL._
import net.liftweb.json.JsonParser._
import org.apache.http.client.methods.{HttpGet, HttpPost}
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.DefaultHttpClient


/**
  * Created by work on 6/12/17.
  */
object Nt1cBMf extends Config with StrictLogging{


  def getNt1cBMfHttpApache(username: String, branch: String, accountType: String, accountNumber: String, cbsToken: String): String = {

    val client = new DefaultHttpClient()
    val url = config.getString("bankserver.url")

    //OBP-Adapter_Leumi/Doc/MFServices/NT1C_B_000 Sample.txt
    val post = new HttpPost(url + "/ESBLeumiDigitalBank/PAPI/v1.0/NT1C/B/000/01.02")
    post.addHeader("Content-Type", "application/json;charset=utf-8")
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
    val jsonBody = new StringEntity(compactRender(json))
    post.setEntity(jsonBody)
    logger.debug("NT1C_B_000--Request : "+post.toString +"\n Body is :" + compactRender(json))
    val response = client.execute(post)
    val inputStream = response.getEntity.getContent
    val result = scala.io.Source.fromInputStream(inputStream).mkString
    response.close()
    logger.debug("NT1C_B_000--Response : "+response.toString+ "\n Body is :"+result)
    result
  }

  def getBalance(username: String, branch: String, accountType: String, accountNumber: String, cbsToken: String): (String) = {
    val call = (getNt1cBMfHttpApache(username, branch, accountType, accountNumber, cbsToken)) 
    val parser = (p: Parser) => {
      def parse: (String) = p.nextToken match {
        case FieldStart("HH_ITRA_NOCHECHIT") => p.nextToken match {
          case StringVal(token) => token
          case _ => p.fail("expected string")
        }
        case End => p.fail("no field named 'HH_ITRA_NOCHECHIT'")
        case _ => parse
      }

      parse
    }
    parse(call, parser)
  }
  def getLimit(username: String, branch: String, accountType: String, accountNumber: String, cbsToken: String): (String) = {
    val call = (getNt1cBMfHttpApache(username, branch, accountType, accountNumber, cbsToken))
    val parser = (p: Parser) => {
      
      def parse: (String) = p.nextToken match {
        case FieldStart("HH_MISGERET_ASHRAI") => p.nextToken match {
          case StringVal(token) => token
          case _ => p.fail("expected string")
        }
        case End => p.fail("no field named 'HH_MISGERET_ASHRAI'")
        case _ => parse
      }

      parse
    }
    parse(call, parser)
  }


  def getLimitJsonAst(json: String): (String) = {
    val call = (getNtib2Mf(json))
    (call \\ "HH_MISGERET_ASHRAI").toString
  }

  
}
