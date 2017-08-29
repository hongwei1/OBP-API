package com.tesobe.obp



import net.liftweb.json.JValue
import net.liftweb.json.JsonAST.{JArray, JField, JObject, compactRender}
import net.liftweb.json.JsonDSL._
import net.liftweb.json.JsonParser._
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.DefaultHttpClient

import scala.concurrent.Await
import scala.concurrent.duration._

/*//For akka-http
import scala.concurrent._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.stream.ActorMaterializer*/

//For apache client


object JoniMf extends Config{


   def getJoniMfHttpApache(username: String): String = {

     //val url = "http://localhost"
     val url = config.getString("bankserver.url")
     println(url)

     val post = new HttpPost(url + "/ESBLeumiDigitalBank/PAPI/V1.0/JONI/0/000/01.01")
     post.addHeader("Content-Type", "application/json;charset=utf-8")

     val client = new DefaultHttpClient()

     val json: JValue = ("JONI_0_000" -> ("NtdriveCommonHeader" -> ("AuthArguments" -> ("UserName" -> username))))
     val jsonBody = new StringEntity(compactRender(json))
     post.setEntity(jsonBody)
  
     val response = client.execute(post)
     //val response = client.execute(new HttpGet("http://localhost/V1.0/JONI/0/000/01.01"))
     val inputStream = response.getEntity.getContent
     val result = scala.io.Source.fromInputStream(inputStream).mkString
     response.close()
     result
   }
  
    
   // libweb-json parses the empty object to JObject(List()), but we need JString to extract to String
   def replaceEmptyObjects(string: String): String = string.replaceAll("\\{\\}", "\"\"")

   // Arrays with single element are not represented as Arrays in the MF json
   def correctArrayWithSingleElement(jsonAst: JValue): JValue = {
     jsonAst transformField {
       case JField("SDR_CHN", JObject(x)) => JField("SDR_CHN", JArray(List(JObject(x))))
       //case JField("SDRC_LINE", JObject(x)) => JField("SDRC_LINE",JArray(List(JObject(x)))) 
       case JField("SDRL_LINE", JObject(x)) => JField("SDRL_LINE", JArray(List(JObject(x))))

     }
   }

   //
   def getJoni(username: String): JValue = {
     correctArrayWithSingleElement(parse(replaceEmptyObjects(getJoniMfHttpApache(username))))
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

     parse(getJoniMfHttpApache(Username), parser)
   }

}
  