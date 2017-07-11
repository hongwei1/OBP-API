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
    val jsonAst: JValue = getJoni(UserId)
    val jsonExtract: JoniMfUser = jsonAst.extract[JoniMfUser]
    
    val allowedAccountTypes = List("330", "430", "110")
    var result = new ListBuffer[BasicBankAccount]()
    val leading_account = BasicBankAccount(
      jsonExtract.SDR_JONI.SDR_MANUI.SDRM_MOVIL_RASHI.SDRM_MOVIL_RASHI_CHN,
      jsonExtract.SDR_JONI.SDR_MANUI.SDRM_MOVIL_RASHI.SDRM_MOVIL_RASHI_SNIF,
      jsonExtract.SDR_JONI.SDR_MANUI.SDRM_MOVIL_RASHI.SDRM_MOVIL_RASHI_SUG,
      AccountPermissions("0","1","1") //TODO: Identify and extract leading account permissions
    )
    for ( i <- jsonExtract.SDR_JONI.SDR_LAK_SHEDER.SDRL_LINE) {
      for (u <- i.SDR_CHN) {
        if (allowedAccountTypes.contains(u.SDRC_LINE.SDRC_CHN.SDRC_CHN_SUG)) 
        result += BasicBankAccount(
          u.SDRC_LINE.SDRC_CHN.SDRC_CHN_CHN,
          u.SDRC_LINE.SDRC_CHN.SDRC_CHN_SNIF,
          u.SDRC_LINE.SDRC_CHN.SDRC_CHN_SUG,
          AccountPermissions(
            u.SDRC_LINE.SDRC_HARSHAOT.SDRC_MURSHE_MEIDA,
            u.SDRC_LINE.SDRC_HARSHAOT.SDRC_MURSHE_PEULOT,
            u.SDRC_LINE.SDRC_HARSHAOT.SDRC_MURSHE_TZAD_G)
        ) 
      }}
    if (!result.contains(leading_account)) result += leading_account //TODO: Broken by assuming leading account permissions
    result.toList
  }
  def getFullBankAccountsforUser(UserId: String): List[FullBankAccount] ={
    val basicAccounts = getBasicBankAccountsForUser(UserId)
    var result = new ListBuffer[FullBankAccount]
    for (i <- basicAccounts) {
      val balance = getBalance("./src/main/resources/nt1c_result.json")
      val limit = getLimit("./src/main/resources/nt1c_result.json")
      result += FullBankAccount(i,getIban("./src/main/resources/ntib_result.json"), balance, limit)
    }
    result.toList
  }
  
  def hex256(in : String): String = {
    hexEncode(MessageDigest.getInstance("SHA-256").digest(in.getBytes("UTF-8")))
  }
  //Create OBP Style ModeratedCoreAccountJSON
  def getModeratedCoreAccountJSON(account: FullBankAccount): ModeratedCoreAccountJSON = {
    //TODO: check if User is Account Owner
    val owners = List(UserJSONV121("63c7ddc1196587195580c2d89647e3f0642582827d8612a911ca905f2ae9a55f", "provider", "name"))
    val views = if (owners.nonEmpty) List(AccountView ("owner", "Owner View", false:Boolean) ) else List(AccountView("","",false))
    val routing = AccountRoutingJsonV121("IBAN", account.iban)
    val balance = AmountOfMoneyJsonV121("ILS", account.balance) 
    ModeratedCoreAccountJSON(
      hex256(account.basicBankAccount.accountNr + account.basicBankAccount.branchNr + account.basicBankAccount.accountType + "globalsalt"), //TODO: get global salt from props
       "leumi001", "label", account.basicBankAccount.accountNr, 
      owners, views, account.basicBankAccount.accountType,balance, routing)
  }
}
