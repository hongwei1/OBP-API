package com.tesobe.obp

import com.tesobe.obp.HttpClient.makePostRequest
import com.tesobe.obp.JoniMf.replaceEmptyObjects
import com.tesobe.obp.ErrorMessages._
import net.liftweb.json.JValue
import net.liftweb.json.JsonParser.parse

import scala.util.control.NoStackTrace

object NtbdBv050Mf {
  def getNtbdBv050(branch: String,
                   accountType: String,
                   accountNumber: String,
                   cbsToken: String,
                   ntbdAv050Token: String,
                   toAccountBankId: String,
                   toAccountBranchId: String,
                   toAccountAccountNumber: String,
                   toAccountIban: String,
                   transactionAmount: String,
                   description: String,
                   referenceNameOfTo: String
                  ): Either[PAPIErrorResponse, NtbdBv050] = {

    val path = "/ESBLeumiDigitalBank/PAPI/v1.0/NTBD/B/050/01.03"
    
    //TODO: reference name has to be in english for RTGS transfer. Ask leumi for definition of "english".
    val finalReferenceNameOfTo = if (referenceNameOfTo == "") "TargetAccount" else referenceNameOfTo
    val constrainedReferenceNameOfTo = if (finalReferenceNameOfTo.length <= 28) finalReferenceNameOfTo else finalReferenceNameOfTo.substring(0,28)
   
      val constrainedTransactionAmount =  try { f"${transactionAmount.toDouble}%1.2f"
    } catch {
      case _: Throwable => throw new RuntimeException(InvalidAmount) with NoStackTrace
    }
      val constrainedDescription = if (description.length <= 28) description else description.substring(0,28)

      val json: JValue =parse(s"""
    {
      "NTBD_B_050": {
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
        "K050_BDIKAZCHUTIN": {
        "K050_TOKEN_ZCHUT": "$ntbdAv050Token",
        "K050_BANK_ZCUT": "$toAccountBankId",
        "K050_SNIF_ZCUT": "$toAccountBranchId",
        "K050_SUG_ZCUT": "000",
        "K050_CHN_ZCUT": "$toAccountAccountNumber",
        "K050_IBAN_ZCUT": "$toAccountIban",
        "K050_SCUM": "$constrainedTransactionAmount",
        "K050_MATRAT_HAVARA": "$constrainedDescription",
        "K050_SHEM_MUTAV": "$constrainedReferenceNameOfTo",
        "K050_SHLAV_PEULA": "0",
        "K050_MISPAR_SIDURI": "1"
      }
      }
    }""")


      val result = makePostRequest(json, path)
      implicit val formats = net.liftweb.json.DefaultFormats
      try {
        Right(parse(replaceEmptyObjects(result)).extract[NtbdBv050])
      } catch {
        case e: net.liftweb.json.MappingException => Left(parse(replaceEmptyObjects(result)).extract[PAPIErrorResponse])
      }
    }
  }

