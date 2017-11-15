package com.tesobe.obp



import com.google.common.cache.CacheBuilder
import com.typesafe.scalalogging.StrictLogging
import net.liftweb.json.JValue
import net.liftweb.json.JsonAST.{JArray, JField, JObject}
import net.liftweb.json.JsonParser._

import com.tesobe.obp.HttpClient.makePostRequest

import scalacache.ScalaCache
import scalacache.guava.GuavaCache





object JoniMf extends Config with StrictLogging{

  val underlyingGuavaCache = CacheBuilder.newBuilder().maximumSize(10000L).build[String, Object]
  implicit val scalaCache  = ScalaCache(GuavaCache(underlyingGuavaCache))


   def getJoniMfCore(username: String): Either[PAPIErrorResponse,JoniMfUser] = {

     val path = "/ESBLeumiDigitalBank/PAPI/V1.0/JONI/0/000/01.01"
     val json: JValue =parse(s"""
     {
       "JONI_0_000": {
         "NtdriveCommonHeader": {
           "AuthArguments": {
             "UserName": "$username"
           }
         }
       }
     }""")


     val result = makePostRequest(json, path)
     implicit val formats = net.liftweb.json.DefaultFormats
     try {
       Right(parse(replaceEmptyObjects(result)).extract[JoniMfUser])
     } catch {
       case e: net.liftweb.json.MappingException => Left(parse(replaceEmptyObjects(result)).extract[PAPIErrorResponse])
     }
   }

  def getJoniMf(username: String, isFirst: Boolean = true): Either[PAPIErrorResponse, JoniMfUser] = {

    import scalacache.Flags
    import scalacache.memoization.{cacheKeyExclude, memoizeSync}

    def getJoniMfCached(username: String)(implicit @cacheKeyExclude flags: Flags): Either[PAPIErrorResponse,JoniMfUser]  = memoizeSync {
      getJoniMfCore(username)
    }

    isFirst == true match {
      case true => // Call MF
        implicit val flags = Flags(readsEnabled = false)
        getJoniMfCached(username)
      case false => // Try to read from cache
        implicit val flags = Flags(readsEnabled = true)
        getJoniMfCached(username)
    }
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
}
  