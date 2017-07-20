package com.tesobe.obp


import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer
import net.liftweb.json.JValue
import net.liftweb.json.JsonAST.{JArray, JField, JObject, compactRender}
import net.liftweb.json.JsonDSL._
import net.liftweb.json.JsonParser._

import scala.concurrent.Future


object JoniMf extends Config{
  // mainframe result is local .json for now
  def getJoniMf(mainframe: String): String = {
    val source = scala.io.Source.fromFile(mainframe)
    val lines = try source.mkString finally source.close()
    lines
  }
  
 def getJoniMfHttp(username: String): String = {

   implicit val system = ActorSystem()
   implicit val materializer = ActorMaterializer()

   val json: JValue = ("JONI_0_000" ->  ("NtdriveCommonHeader" -> ("AuthArguments" -> ("UserName" -> username))))
   val data: String  = (compactRender(json))
   var contentType: ContentType = ContentType(MediaType.applicationWithFixedCharset("application/json",HttpCharsets.`UTF-8`))


   val responseFuture: Future[HttpResponse] =
     Http().singleRequest(HttpRequest(
       method = HttpMethods.POST,
       uri = "http://localhost:7800/ESBLeumiDigitalBank/PAPI/V1.0/JONI/0/000/01.01",
       entity = HttpEntity.apply(contentType, data.getBytes())
      ))
   
    "finished"
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