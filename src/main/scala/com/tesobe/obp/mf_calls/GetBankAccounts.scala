package com.tesobe.obp

import java.security.MessageDigest


import com.tesobe.obp.JoniMf.getJoniMf
import net.liftweb.util.SecurityHelpers._


import scala.collection.mutable.ListBuffer

object GetBankAccounts extends Config {

  def getBasicBankAccountsForUser(username: String, useCache: Boolean): Either[PAPIErrorResponse, List[BasicBankAccount]] = {

    val joniMfCall = getJoniMf(username, !useCache)
    joniMfCall match {
      case Right(x) =>

        val allowedAccountTypes = config.getString("accountTypes.allowed").split(",").map(_.trim).toList
        //now extract values from JoniMFUser object into List of BasicBankAccount
        var result = new ListBuffer[BasicBankAccount]()

        for (i <- x.SDR_JONI.SDR_LAK_SHEDER.SDRL_LINE) {
          for (u <- i.SDR_CHN) {
            if (allowedAccountTypes.contains(u.SDRC_LINE.SDRC_CHN.SDRC_CHN_SUG)) {
              val hasMursheMeida = u.SDRC_LINE.SDRC_HARSHAOT.SDRC_MURSHE_MEIDA == "1"
              val hasMurshePeulot = u.SDRC_LINE.SDRC_HARSHAOT.SDRC_MURSHE_PEULOT == "1"
              val hasmursheTzadG = u.SDRC_LINE.SDRC_HARSHAOT.SDRC_MURSHE_TZAD_G == "1"
              result += BasicBankAccount(
                u.SDRC_LINE.SDRC_CHN.SDRC_CHN_CHN,
                u.SDRC_LINE.SDRC_CHN.SDRC_CHN_SNIF,
                u.SDRC_LINE.SDRC_CHN.SDRC_CHN_SUG,
                x.SDR_JONI.MFTOKEN,
                AccountPermissions(
                  hasMursheMeida,
                  hasMurshePeulot,
                  hasmursheTzadG
                )
              )
            }
          }
        }
        Right(result.toList)

      case Left(x) =>
        Left(x)
    }
  }

  
  def hexEncodedSha256(in : String): String = {
    hexEncode(MessageDigest.getInstance("SHA-256").digest(in.getBytes("UTF-8")))
  }
  
  def base64EncodedSha256(in: String): String = {
    base64EncodeURLSafe(MessageDigest.getInstance("SHA-256").digest(in.getBytes("UTF-8"))).stripSuffix("=")
  }

}
