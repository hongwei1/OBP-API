package com.tesobe.obp
import com.tesobe.obp.ErrorMessages.InputTooLongException
import com.tesobe.obp.HttpClient.makePostRequest
import com.tesobe.obp.JoniMf.replaceEmptyObjects
import com.typesafe.scalalogging.StrictLogging
import net.liftweb.json.{JValue, parse}

import scala.util.control.NoStackTrace


object Ntbd2v105Mf extends Config with StrictLogging{

  def getNtbd2v105Mf(branch: String,
                     accountType: String,
                     accountNumber: String,
                     cbsToken: String,
                     ntbd1v105Token: String,
                     nicknameOfSender: String,
                     messageToReceiver: String
                    ): Either[PAPIErrorResponse,Ntbd2v105] = {
    
    if (nicknameOfSender.length > 20) throw new InputTooLongException() with NoStackTrace
    if (messageToReceiver.length > 50) throw new InputTooLongException() with NoStackTrace

    val path = config.getString("backendCalls.NTBD_2_105")

    val json: JValue =parse(s"""
      {
        "NTBD_2_105": {
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
          "KELET_1352": {
          "K135_TOKEN_ISHUR": "$ntbd1v105Token",
          "K135_BAKASH_TASHL": "1",
          "K135_KINUY_MAVIR": "$nicknameOfSender",
          "K135_MELEL_LE_MUTAV": "$messageToReceiver"
        }
        }
      }""")

    val result = makePostRequest(json, path)
    implicit val formats = net.liftweb.json.DefaultFormats
    try {
      Right(parse(replaceEmptyObjects(result)).extract[Ntbd2v105])
    } catch {
      case e: net.liftweb.json.MappingException  => Left(parse(replaceEmptyObjects(result)).extract[PAPIErrorResponse])
    }
  }

}
