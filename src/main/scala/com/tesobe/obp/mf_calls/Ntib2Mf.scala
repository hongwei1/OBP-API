package com.tesobe.obp

import com.typesafe.scalalogging.StrictLogging
import com.tesobe.obp.HttpClient.makePostRequest
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

  def getNtib2Mf(branch: String, accountType: String, accountNumber: String, username: String, cbsToken: String): String = {

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
    parse(getNtib2Mf(branch,  accountType,  accountNumber, username, cbsToken), parser)
  }
}
