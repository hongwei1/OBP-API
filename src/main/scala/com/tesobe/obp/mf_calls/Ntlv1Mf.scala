package com.tesobe.obp

import java.util.UUID
import com.google.common.cache.CacheBuilder
import com.tesobe.{CacheKeyFromArguments, CacheKeyOmit}
import com.tesobe.obp.ErrorMessages.{InvalidAmountException, InvalidIdTypeException, InvalidPassportOrNationalIdException}
import com.tesobe.obp.HttpClient.makePostRequest
import com.tesobe.obp.JoniMf.replaceEmptyObjects
import com.tesobe.obp.cache.Caching
import com.typesafe.scalalogging.StrictLogging
import net.liftweb.json.JValue
import net.liftweb.json.JsonParser._

object Ntlv1Mf extends Config with StrictLogging{

  def getNtlv1MfCore(username: String, idNumber: String, idType: String, cbsToken: String): Either[PAPIErrorResponse,Ntlv1]  = {

    val path = config.getString("backendCalls.NTLV_1_000")

    if (idNumber.length > 9) throw new InvalidPassportOrNationalIdException()
    if (idType != "1" && idType != "5") throw new InvalidIdTypeException()

    val json: JValue = parse(
      s"""
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
    try {
      Right(parse(replaceEmptyObjects(result)).extract[Ntlv1])
    } catch {
      case e: net.liftweb.json.MappingException => Left(parse(replaceEmptyObjects(result)).extract[PAPIErrorResponse])
    }
  }

  def getNtlv1Mf(username: String, idNumber: String, idType: String, cbsToken: String, isFirst: Boolean = true) = {

    import scalacache.Flags

    def getNtlv1MfCached(username: String, idNumber: String, idType: String, cbsToken: String)(implicit @CacheKeyOmit flags: Flags): Either[PAPIErrorResponse,Ntlv1]  = {
      var cacheKey = (UUID.randomUUID().toString, UUID.randomUUID().toString, UUID.randomUUID().toString)
      CacheKeyFromArguments.buildCacheKey{
        Caching.memoizeSyncWithProvider(Some(cacheKey.toString())){
          getNtlv1MfCore(username, idNumber, idType, cbsToken)
       }}}

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
