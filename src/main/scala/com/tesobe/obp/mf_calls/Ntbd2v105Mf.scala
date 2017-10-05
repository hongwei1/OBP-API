package com.tesobe.obp
import com.tesobe.obp.JoniMf.{config, replaceEmptyObjects}
import com.tesobe.obp.HttpClient.makePostRequest
import com.typesafe.scalalogging.StrictLogging
import net.liftweb.json.{JValue, parse}


object Ntbd2v105Mf extends StrictLogging{

  def getNtbd2v105Mf(branch: String,
                     accountType: String,
                     accountNumber: String,
                     cbsToken: String,
                     ntbd1v105Token: String,
                     nicknameOfSender: String,
                     messageToReceiver: String
                    ): Ntbd2v105 = {

    val path = "/ESBLeumiDigitalBank/PAPI/v1.0/NTBD/2/105/01.01"

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
    parse(replaceEmptyObjects(result)).extract[Ntbd2v105]
  }

}
