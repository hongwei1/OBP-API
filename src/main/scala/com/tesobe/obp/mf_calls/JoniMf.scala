package com.tesobe.obp

import net.liftweb.json.JValue
import net.liftweb.json.JsonAST.{JArray, JField, JObject}
import net.liftweb.json.JsonParser._

object JoniMf {
  // mainframe result is local .json for now
  def getJoniMf(mainframe: String): String = {
    val source = scala.io.Source.fromFile(mainframe)
    val lines = try source.mkString finally source.close()
    lines
  }

  // libweb-json parses the empty object to JObject(List()), but we need JString to extract to String
  // alternative: transform {case JObject(List()) => JString("") } will replace the JValues at json ast level
  def replaceEmptyObjects(string: String): String  = string.replaceAll("\\{\\}", "\"\"")

  // Arrays with single element are not represented as Arrays in the MF json
  def correctArrayWithSingleElement(jsonAst: JValue): JValue = {
     jsonAst transformField {
      case JField("SDR_CHN", JObject(x)) => JField("SDR_CHN",JArray(List(JObject(x))))
      //case JField("SDRC_LINE", JObject(x)) => JField("SDRC_LINE",JArray(List(JObject(x)))) 
      case JField("SDRL_LINE", JObject(x)) => JField("SDRL_LINE",JArray(List(JObject(x))))
         
    }
  }
  //
  def getJoni(mainframe: String): JValue = {
  correctArrayWithSingleElement(parse(replaceEmptyObjects(getJoniMf(mainframe))))
  }
  //userid is path to json file right now, only secondary accounts

  
  //getting just the MFToken without parsing the whole json and creating jsonAST

 
  def getMFToken(Username: String): String = {

    val parser = (p: Parser) => {
      def parse: String = p.nextToken match {
        case FieldStart("MFTOKEN") => p.nextToken match {
          case StringVal(token) => token
          case _ => p.fail("expected string")
        }
        case End => p.fail("no field named 'MFToken'")
        case _ => parse
      }

      parse
    } 
    
    parse(getJoniMf(Username), parser)
  }

}