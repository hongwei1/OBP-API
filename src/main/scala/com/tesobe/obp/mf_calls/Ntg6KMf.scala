package com.tesobe.obp

import com.google.common.cache.CacheBuilder
import com.tesobe.obp.HttpClient.makePostRequest
import com.tesobe.obp.JoniMf.replaceEmptyObjects
import com.typesafe.scalalogging.StrictLogging
import net.liftweb.json.JValue
import net.liftweb.json.JsonParser.parse

import scalacache.ScalaCache
import scalacache.guava.GuavaCache

object Ntg6KMf extends StrictLogging{

  val underlyingGuavaCache = CacheBuilder.newBuilder().maximumSize(10000L).build[String, Object]
  implicit val scalaCache  = ScalaCache(GuavaCache(underlyingGuavaCache))
  
    def getNtg6KMfCore(branch: String, accountType: String, accountNumber: String, cbsToken: String): Either[PAPIErrorResponse,Ntg6IandK] = {

      val path = "/ESBLeumiDigitalBank/PAPI/v1.0/NTG6/K/000/01.04"
      logger.debug("parsing json for getNtg6K")
      val json: JValue = parse(s"""
      {
        "NTG6_K_000": {
          "NtdriveCommonHeader": {
          "KeyArguments": {
          "Branch": "$branch",
          "AccountType": "$accountType",
          "AccountNumber": "$accountNumber"
        },
          "AuthArguments": {
          "MFToken": "$cbsToken"
        }
        }
        }
      }""")

      val result = makePostRequest(json, path)
      
      implicit val formats = net.liftweb.json.DefaultFormats
      try {
        Right(parse(replaceEmptyObjects(result)).extract[Ntg6IandK])
      } catch {
        case e: net.liftweb.json.MappingException  => Left(parse(replaceEmptyObjects(result)).extract[PAPIErrorResponse])
      }
    }
  def getNtg6KMf(
                  branch: String,
                  accountType: String,
                  accountNumber: String,
                  cbsToken: String,
                  isFirst: Boolean = true) = {

    import scalacache.Flags
    import scalacache.memoization.{cacheKeyExclude, memoizeSync}

    def getNtg6KMfCached(branch: String, accountType: String, accountNumber: String, cbsToken: String)(implicit @cacheKeyExclude flags: Flags): Either[PAPIErrorResponse,Ntg6IandK]  = memoizeSync {
      getNtg6KMfCore(branch, accountType, accountNumber, cbsToken)
    }

    isFirst == true match {
      case true => // Call MF
        implicit val flags = Flags(readsEnabled = false)
        getNtg6KMfCached(branch, accountType, accountNumber, cbsToken)
      case false => // Try to read from cache
        implicit val flags = Flags(readsEnabled = true)
        getNtg6KMfCached(branch, accountType, accountNumber, cbsToken)
    }
  }
}
