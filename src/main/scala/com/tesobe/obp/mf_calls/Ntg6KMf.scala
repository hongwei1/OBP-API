package com.tesobe.obp

import java.util.UUID

import com.tesobe.obp.HttpClient.makePostRequest
import com.tesobe.obp.JoniMf.replaceEmptyObjects
import com.tesobe.obp.cache.Caching
import com.tesobe.{CacheKeyFromArguments, CacheKeyOmit}
import com.typesafe.scalalogging.StrictLogging
import net.liftweb.json.JValue
import net.liftweb.json.JsonParser.parse

object Ntg6KMf extends Config with StrictLogging{

    def getNtg6KMfCore(branch: String, accountType: String, accountNumber: String, cbsToken: String): Either[PAPIErrorResponse,Ntg6IandK] = {

      val path = config.getString("backendCalls.NTG6_K_000")
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

    def getNtg6KMfCached(branch: String, accountType: String, accountNumber: String, cbsToken: String)(implicit @CacheKeyOmit flags: Flags): Either[PAPIErrorResponse,Ntg6IandK]  = {
      var cacheKey = (UUID.randomUUID().toString, UUID.randomUUID().toString, UUID.randomUUID().toString)
      CacheKeyFromArguments.buildCacheKey{
        Caching.memoizeSyncWithProvider(Some(cacheKey.toString())){
          getNtg6KMfCore(branch, accountType, accountNumber, cbsToken)
       }}}

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
