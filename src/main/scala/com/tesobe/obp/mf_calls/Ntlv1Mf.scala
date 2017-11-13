package com.tesobe.obp

import com.google.common.cache.CacheBuilder
import com.tesobe.obp.HttpClient.makePostRequest
import com.tesobe.obp.JoniMf.replaceEmptyObjects
import com.typesafe.scalalogging.StrictLogging
import net.liftweb.json.JValue
import net.liftweb.json.JsonParser._

import scalacache.ScalaCache
import scalacache.guava.GuavaCache

object Ntlv1Mf extends StrictLogging{

  val underlyingGuavaCache = CacheBuilder.newBuilder().maximumSize(10000L).build[String, Object]
  implicit val scalaCache  = ScalaCache(GuavaCache(underlyingGuavaCache))


  def getNtlv1MfCore(username: String, idNumber: String, idType: String, cbsToken: String): Ntlv1  = {
  
    val path = "/ESBLeumiDigitalBank/PAPI/v1.0/NTLV/1/000/01.01"

    val json: JValue = parse(s"""
    {
      "NTLV_1_000": {
        "NtdriveCommonHeader": {
          "KeyArguments": {
            "Branch": "000",
            "IDNumber": "$idNumber",
            "IDType": "$idType",
            "IDCounty": "2121" 
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
    

    implicit val formats = net.liftweb.json.DefaultFormats
    parse(replaceEmptyObjects(result)).extract[Ntlv1]
  }

  def getNtlv1Mf(username: String, idNumber: String, idType: String, cbsToken: String, isFirst: Boolean = true) = {

    import scalacache.Flags
    import scalacache.memoization.{cacheKeyExclude, memoizeSync}

    def getNtlv1MfCached(username: String, idNumber: String, idType: String, cbsToken: String)(implicit @cacheKeyExclude flags: Flags): Ntlv1  = memoizeSync {
      getNtlv1MfCore(username, idNumber, idType, cbsToken)
    }

    isFirst == true match {
      case true => // Call MF
        implicit val flags = Flags(readsEnabled = false)
        getNtlv1MfCached(username, idNumber, idType, cbsToken)
      case false => // Try to read from cache
        implicit val flags = Flags(readsEnabled = true)
        getNtlv1MfCached(username, idNumber, idType, cbsToken)
    }
  }

}
