package com.tesobe.obp

import com.tesobe.obp.HttpClient.makePostRequest
import com.tesobe.obp.JoniMf.replaceEmptyObjects
import com.typesafe.scalalogging.StrictLogging
import net.liftweb.json.JValue
import net.liftweb.json.JsonParser._


package object Ntbd2v135Mf extends Config with StrictLogging{

  def getNtbd2v135Mf(branch: String,
                     accountType: String,
                     accountNumber: String,
                     username: String,
                     cbsToken: String,
                     ntbd1v135_Token:String,
                     nicknameOfMoneySender: String,
                     messageToMoneyReceiver: String
                               ): Either[PAPIErrorResponse,Ntbd2v135] = {
    
    val path = config.getString("backendCalls.NTBD_2_135")
    val constrainedNickname = if (nicknameOfMoneySender.length <= 20) nicknameOfMoneySender else nicknameOfMoneySender.substring(0,20)
    val constrainedMessage = if (messageToMoneyReceiver.length <= 50) messageToMoneyReceiver else messageToMoneyReceiver.substring(0,50)
    val json: JValue = parse(s"""{
        "NTBD_2_135": {
          "NtdriveCommonHeader": {
            "KeyArguments": {
              "Branch": "$branch",
              "AccountType": "$accountType",
              "AccountNumber": "$accountNumber"
            },
            "AuthArguments": {
              "User": "$username"
              "MFToken": "$cbsToken"
            }
          },
          "KELET_1352": {
            "K135_TOKEN_ISHUR": "$ntbd1v135_Token",
            "K135_BAKASH_TASHL": "1",
            "K135_KINUY_MAVIR": "$constrainedNickname",
            "K135_MELEL_LE_MUTAV": "$constrainedMessage"
          }
        }
      }
    """)

    val result = makePostRequest(json, path)
    
    implicit val formats = net.liftweb.json.DefaultFormats
    try {
      Right(parse(replaceEmptyObjects(result)).extract[Ntbd2v135])
    } catch {
      case e: net.liftweb.json.MappingException => Left(parse(replaceEmptyObjects(result)).extract[PAPIErrorResponse])
    }  }

}
