package com.tesobe.obp

import java.security.MessageDigest

import com.tesobe.obp.Encryption.encryptToken
import com.tesobe.obp.ErrorMessages._
import com.tesobe.obp.JoniMf.{correctArrayWithSingleElement, getJoniMfHttpApache, replaceEmptyObjects}
import com.tesobe.obp.june2017.LeumiDecoder.cachedJoni
import net.liftweb.json.JValue
import net.liftweb.json.JsonParser.parse
import net.liftweb.util.SecurityHelpers._
import collection.JavaConverters

import scala.collection.mutable.ListBuffer

object GetBankAccounts extends Config {

  def getBasicBankAccountsForUser(username: String, useCache: Boolean): List[BasicBankAccount] = {
    //Simulating mainframe call
    implicit val formats = net.liftweb.json.DefaultFormats
    //Creating JSON AST
    val json: String = useCache match {
      case true => cachedJoni.get(username).getOrElse(throw new JoniCacheEmptyException(s"$JoniCacheEmpty The Joni Cache Input Key =$username "))
      case false => 
        val result = getJoniMfHttpApache(username)
        if (result.contains("PAPIErrorResponse")) throw new JoniFailedExeption(result) else
        cachedJoni.set(username, result)
        cachedJoni.get(username).getOrElse(throw new JoniCacheEmptyException(s"$JoniCacheEmpty The Joni Cache Input Key =$username "))
    }
    val jsonAst: JValue = correctArrayWithSingleElement(parse(replaceEmptyObjects(json)))
    //Create case class object JoniMfUser
    val jsonExtract: JoniMfUser = jsonAst.extract[JoniMfUser]
    //By specification
    //TODO: Should this be in the props?
    //val allowedAccountTypes = List("330", "340", "110")
    val allowedAccountTypes = config.getString("accountTypes.allowed").split(",").map(_.trim).toList
    //now extract values from JoniMFUser object into List of BasicBankAccount
    // (will be changed to inboundBankAccount 2017 objects 
    var result = new ListBuffer[BasicBankAccount]()
    val leading_account = BasicBankAccount(
        jsonExtract.SDR_JONI.SDR_MANUI.SDRM_MOVIL_RASHI.SDRM_MOVIL_RASHI_CHN,
        jsonExtract.SDR_JONI.SDR_MANUI.SDRM_MOVIL_RASHI.SDRM_MOVIL_RASHI_SNIF,
        jsonExtract.SDR_JONI.SDR_MANUI.SDRM_MOVIL_RASHI.SDRM_MOVIL_RASHI_SUG,
        encryptToken(jsonExtract.SDR_JONI.MFTOKEN),
        AccountPermissions(true,false,false)
    )
    for ( i <- jsonExtract.SDR_JONI.SDR_LAK_SHEDER.SDRL_LINE) {
      for (u <- i.SDR_CHN) {
        if (allowedAccountTypes.contains(u.SDRC_LINE.SDRC_CHN.SDRC_CHN_SUG)) {
          val hasMursheMeida = u.SDRC_LINE.SDRC_HARSHAOT.SDRC_MURSHE_MEIDA == "1"
          val hasMurshePeulot = u.SDRC_LINE.SDRC_HARSHAOT.SDRC_MURSHE_PEULOT == "1"
          val hasmursheTzadG = u.SDRC_LINE.SDRC_HARSHAOT.SDRC_MURSHE_TZAD_G == "1"
          result += BasicBankAccount(
            u.SDRC_LINE.SDRC_CHN.SDRC_CHN_CHN,
            u.SDRC_LINE.SDRC_CHN.SDRC_CHN_SNIF,
            u.SDRC_LINE.SDRC_CHN.SDRC_CHN_SUG,
            encryptToken(jsonExtract.SDR_JONI.MFTOKEN),
            AccountPermissions(
                        //TODO: Check that the permission management is o.k. 
                        //User with MEIDA right will get Accountant View
                        hasMursheMeida,
                        //User with PEULOT right will get Owner View
                        hasMurshePeulot,
                        //User with TZAD_G right will get Owner View
                        hasmursheTzadG
            )
          ) }
      }}
    if (!result.contains(leading_account)) result += leading_account //TODO: Broken by assuming leading account permissions
    result.toList
  }

  
  def hexEncodedSha256(in : String): String = {
    hexEncode(MessageDigest.getInstance("SHA-256").digest(in.getBytes("UTF-8")))
  }
  
  def base64EncodedSha256(in: String): String = {
    base64EncodeURLSafe(MessageDigest.getInstance("SHA-256").digest(in.getBytes("UTF-8"))).stripSuffix("=")
  }

}
