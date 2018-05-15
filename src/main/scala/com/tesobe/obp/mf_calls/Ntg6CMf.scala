package com.tesobe.obp

import com.tesobe.obp.HttpClient.makePostRequest
import com.tesobe.obp.JoniMf.replaceEmptyObjects
import com.typesafe.scalalogging.StrictLogging
import net.liftweb.json.JValue
import net.liftweb.json.JsonParser.parse

object Ntg6CMf extends Config with StrictLogging{
  
    def getNtg6C(
                 branch: String,
                 accountType: String,
                 accountNumber: String,
                 cbsToken: String,
                 counterpartyBranchNumber: String,
                 counterpartyAccountNumber: String,
                 counterpartyName: String,
                 counterpartyDescription: String,
                 counterpartyIBAN: String,
                 counterpartyNameInEnglish: String,
                 counterpartyDescriptionInEnglish: String
               ): Either[PAPIErrorResponse, Ntg6C] = {

      val path = config.getString("backendCalls.NTG6_C_000")
      logger.debug("parsing json for getNtg6C")
      val json: JValue = parse(s"""
      {
        "NTG6_C_000": {
          "NtdriveCommonHeader": {
          "KeyArguments": {
          "Branch": "$branch",
          "AccountType": "$accountType",
          "AccountNumber": "$accountNumber"
        },
          "AuthArguments": {
          "MFToken": "$cbsToken"
        }
        },
          "KMUT_IDKUNIN": {
          "KMUT_OLD": {
          "KMUT_ERETZ_MUTAV": "2121",
          "KMUT_BANK_MUTAV": "10",
          "KMUT_SNIF_MUTAV": "$counterpartyBranchNumber",
          "KMUT_SUG_CHEN_MUTAV": "0",
          "KMUT_CHEN_MUTAV": "$counterpartyAccountNumber",
          "KMUT_SHEM_MUTAV": "$counterpartyName",
          "KMUT_SUG_MUTAV": "0",
          "KMUT_TEUR_MUTAV": "$counterpartyDescription",
          "KMUT_IBAN": "$counterpartyIBAN",
          "KMUT_SHEM_MUTAV_ANGLIT": "$counterpartyNameInEnglish",
          "KMUT_TEUR_MUTAV_ANGLIT": "$counterpartyDescriptionInEnglish"
        },
          "KMUT_TOSEFET": {
          "KMUT_ZIHUI_MUTAV1": "0",
          "KMUT_ZIHUI_MUTAV2": "0",
          "KMUT_SHEM_MUTAV2": " ",
          "KMUT_SHEM_MUTAV2_E": " "
        }
        }
        }
      }""")
      logger.debug("Ntg6C----makePostRequest")

      val result = makePostRequest(json, path)
      
      logger.debug("Ntg6C ---extracting case class")

      implicit val formats = net.liftweb.json.DefaultFormats
      try {
        Right(parse(replaceEmptyObjects(result)).extract[Ntg6C])
      } catch {
        case e: net.liftweb.json.MappingException  => Left(parse(replaceEmptyObjects(result)).extract[PAPIErrorResponse])
      }    }
}
