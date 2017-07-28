package com.tesobe.obp

import com.tesobe.obp.Ntib2Mf.getNtib2Mf
import net.liftweb.json.JValue
import net.liftweb.json.JsonAST.compactRender
import net.liftweb.json.JsonDSL._
import net.liftweb.json.JsonParser._
import org.apache.http.client.methods.{HttpGet, HttpPost}
import org.apache.http.impl.client.DefaultHttpClient


/**
  * Created by work on 6/12/17.
  */
object Nt1cBMf extends Config{


  def getNt1cBMfHttpApache(branch: String, accountType: String, accountNumber: String, cbsToken: String): String = {

    val url = config.getString("bankserver.url")


    val post = new HttpPost(url + "/ESBLeumiDigitalBank/PAPI/v1.0/NT1C/B/000/01.02")
    println(post)
    post.addHeader("application/json;charset=utf-8","application/json;charset=utf-8")

    val client = new DefaultHttpClient()

    val json: JValue = "NT1C_B_000" -> ("NtdriveCommonHeader" -> ("KeyArguments" -> ("Branch" -> branch) ~ ("AccountType" ->
    accountType) ~ ("AccountNumber" -> accountNumber)) ~ ("AuthArguments" -> ("MFToken" -> cbsToken)))
    println(compactRender(json))

    // send the post request
    val response = client.execute(post)
    //val response = client.execute(new HttpGet("http://localhost/ESBLeumiDigitalBank/PAPI/v1.0/NTIB/2/000/01.01"))
    val inputStream = response.getEntity.getContent
    val result = scala.io.Source.fromInputStream(inputStream).mkString
    response.close()
    result
  }

  def getBalance(branch: String, accountType: String, accountNumber: String, cbsToken: String): (String) = {
    val call = (getNt1cBMfHttpApache(branch, accountType, accountNumber, cbsToken)) 
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
  def getLimit(branch: String, accountType: String, accountNumber: String, cbsToken: String): (String) = {
    val call = (getNt1cBMfHttpApache(branch, accountType, accountNumber, cbsToken))
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
