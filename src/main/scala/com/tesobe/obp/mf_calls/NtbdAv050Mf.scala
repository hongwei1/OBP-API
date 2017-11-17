package com.tesobe.obp

import java.time.LocalDate
import java.time.format.DateTimeFormatter

import com.tesobe.obp.ErrorMessages._
import com.tesobe.obp.HttpClient.makePostRequest
import com.tesobe.obp.JoniMf.replaceEmptyObjects
import net.liftweb.json.JValue
import net.liftweb.json.JsonParser._

object NtbdAv050Mf {

  def getNtbdAv050(branch: String,
                 accountType: String,
                 accountNumber: String,
                 cbsToken: String, 
                 transferType: String, 
                 transferDateInFuture: String
                ): Either[PAPIErrorResponse, NtbdAv050] = {
    val formatter = DateTimeFormatter.BASIC_ISO_DATE


    val path = "/ESBLeumiDigitalBank/PAPI/v1.0/NTBD/A/050/01.03"
    if (transferType.toInt != 1 && transferType.toInt != 2) throw new InvalidIdTypeException()
    val isFutureTransfer = if (transferDateInFuture.trim != "") "1" else "0"
    if (transferDateInFuture.trim != "") try {
      val localDateForTransfer = LocalDate.parse(transferDateInFuture, formatter)
      } catch { case _: Throwable => throw new RuntimeException(InvalidTimeError)}
    

    val json: JValue =parse(s"""
    {
      "NTBD_A_050": {
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
        "K050_BDIKACHOVAIN": {
        "K050_OFEN_HAVARA": "$transferType",
        "K050_OFI_DISKET": "0",
        "K050_ID_HORAA": "0",
        "K050_SW_ATIDI": "$isFutureTransfer",
        "K050_TA_ATIDI": "$transferDateInFuture"
      }
      }
    }""")

    val result = makePostRequest(json, path)
    implicit val formats = net.liftweb.json.DefaultFormats
    try {
      Right(parse(replaceEmptyObjects(result)).extract[NtbdAv050])
    } catch {
      case e: net.liftweb.json.MappingException => Left(parse(replaceEmptyObjects(result)).extract[PAPIErrorResponse])
    }
  }
}
