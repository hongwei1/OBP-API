package com.tesobe.obp

import java.security.MessageDigest

import com.tesobe.obp.JoniMf.getJoni
import com.tesobe.obp.Nt1cMf._
import com.tesobe.obp.NtibMf.getIban
import net.liftweb.json.JValue
import net.liftweb.util.SecurityHelpers._

import scala.collection.mutable.ListBuffer


object GetBankAccounts {

  def getBasicBankAccountsForUser(UserId: String): List[BasicBankAccount] = {
    //Simulating mainframe call
    implicit val formats = net.liftweb.json.DefaultFormats
    //Creating JSON AST
    val jsonAst: JValue = getJoni(UserId)
    //Create case class object JoniMfUser
    val jsonExtract: JoniMfUser = jsonAst.extract[JoniMfUser]
    //By specification
    //TODO: Should this be in the props?
    val allowedAccountTypes = List("330", "430", "110")
    //now extract values from JoniMFUser object into List of BasicBankAccount
    // (will be changed to inboundBankAccount 2017 objects 
    var result = new ListBuffer[BasicBankAccount]()
    val leading_account = BasicBankAccount(
      jsonExtract.SDR_JONI.SDR_MANUI.SDRM_MOVIL_RASHI.SDRM_MOVIL_RASHI_CHN,
      jsonExtract.SDR_JONI.SDR_MANUI.SDRM_MOVIL_RASHI.SDRM_MOVIL_RASHI_SNIF,
      jsonExtract.SDR_JONI.SDR_MANUI.SDRM_MOVIL_RASHI.SDRM_MOVIL_RASHI_SUG,
      AccountPermissions(true,false,false) //TODO: Identify and extract leading account permissions
    )
    for ( i <- jsonExtract.SDR_JONI.SDR_LAK_SHEDER.SDRL_LINE) {
      for (u <- i.SDR_CHN) {
        if (allowedAccountTypes.contains(u.SDRC_LINE.SDRC_CHN.SDRC_CHN_SUG)) {
          val hasMursheMeida = u.SDRC_LINE.SDRC_HARSHAOT.SDRC_MURSHE_MEIDA == "0"
          val hasMurshePeulot = u.SDRC_LINE.SDRC_HARSHAOT.SDRC_MURSHE_PEULOT == "0"
          val hasmursheTzadG = u.SDRC_LINE.SDRC_HARSHAOT.SDRC_MURSHE_TZAD_G == "0"
          result += BasicBankAccount(
            u.SDRC_LINE.SDRC_CHN.SDRC_CHN_CHN,
            u.SDRC_LINE.SDRC_CHN.SDRC_CHN_SNIF,
            u.SDRC_LINE.SDRC_CHN.SDRC_CHN_SUG,
            AccountPermissions(
              //TODO: Check that the permission management is o.k. 
              //User with MEIDA right will get Accountant View
              hasMursheMeida,
              //User with PEULOT right will get Owner View
              hasMurshePeulot,
              //User with TZAD_G right will get Owner View
              hasmursheTzadG)
        ) }
      }}
    if (!result.contains(leading_account)) result += leading_account //TODO: Broken by assuming leading account permissions
    result.toList
  }
  def getFullBankAccountsforUser(UserId: String): List[FullBankAccount] ={
    val basicAccounts = getBasicBankAccountsForUser(UserId)
    var result = new ListBuffer[FullBankAccount]
    for (i <- basicAccounts) {
      val balance = getBalance("./src/test/resources/nt1c_result.json")
      val limit = getLimit("./src/test/resources/nt1c_result.json")
      result += FullBankAccount(i,getIban("./src/test/resources/ntib_result.json"), balance, limit)
    }
    result.toList
  }
  
  def hexEncodedSha256(in : String): String = {
    hexEncode(MessageDigest.getInstance("SHA-256").digest(in.getBytes("UTF-8")))
  }
  
  def base64EncodedSha256(in: String): String = {
    base64EncodeURLSafe(MessageDigest.getInstance("SHA-256").digest(in.getBytes("UTF-8"))).stripSuffix("=")
  }

}
