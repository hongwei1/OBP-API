package com.tesobe.obp

import com.tesobe.obp.JoniMf.{config, replaceEmptyObjects}
import com.tesobe.obp.HttpClient.makePostRequest
import com.tesobe.obp.Ntbd1v135Mf.logger
import com.typesafe.scalalogging.StrictLogging
import net.liftweb.json.JValue
import net.liftweb.json.JsonAST.compactRender
import net.liftweb.json.JsonDSL._
import net.liftweb.json.JsonParser._
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.DefaultHttpClient

package object Ntbd2v135Mf extends StrictLogging{

  def getNtbd2v135Mf(branch: String,
                     accountType: String,
                     accountNumber: String,
                     username: String,
                     cbsToken: String,
                     ntbd1v135_Token:String,
                     nicknameOfMoneySender: String,
                     messageToMoneyReceiver: String
                               ): Ntbd2v135 = {
    
    val path = "/ESBLeumiDigitalBank/PAPI/v1.0/NTBD/2/135/01.01"

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
            "K135_KINUY_MAVIR": "$nicknameOfMoneySender",
            "K135_MELEL_LE_MUTAV": "$messageToMoneyReceiver"
          }
        }
      }
    """)

    val result = makePostRequest(json, path)
    
    implicit val formats = net.liftweb.json.DefaultFormats
    parse(replaceEmptyObjects(result)).extract[Ntbd2v135]
  }

}
