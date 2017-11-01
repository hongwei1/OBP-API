package com.tesobe.obp

import com.tesobe.obp.HttpClient.makePostRequest
import com.tesobe.obp.JoniMf.replaceEmptyObjects
import net.liftweb.json.JValue
import net.liftweb.json.JsonParser.parse

object Ntbd2v050Mf {
  
    def getNtbd2v050(branch: String,
                     accountType: String,
                     accountNumber: String,
                     cbsToken: String,
                     username: String,
                     ntbdAv050Token: String,
                     ntbdAv050fromAccountOwnerName: String
                    ): Either[PAPIErrorResponse,Ntbd2v050] = {

      val path = "/ESBLeumiDigitalBank/PAPI/v1.0/NTBD/2/050/01.01"
      
      val finalFromAccountOwnerName = if (ntbdAv050fromAccountOwnerName.trim == "") "CustomerName" else ntbdAv050fromAccountOwnerName

      val json: JValue = parse(s"""
      {
        "NTBD_2_050": {
          "NtdriveCommonHeader": {
          "KeyArguments": {
          "Branch": "$branch",
          "AccountType": "$accountType",
          "AccountNumber": "$accountNumber"
        },
          "AuthArguments": {
          "MFToken": "$cbsToken",
          "User": "$username"
        }
        },
          "K050_ISHURIN": {
          "K050_TOKEN_ISHUR": "$ntbdAv050Token",
          "K050_KOD_NOSE": "0",
          "K050_SHEM_LAK_MEUDKAN": "$finalFromAccountOwnerName"
        }
        }
      }""")

      val result = makePostRequest(json, path)
      implicit val formats = net.liftweb.json.DefaultFormats
      try {
        Right(parse(replaceEmptyObjects(result)).extract[Ntbd2v050])
      } catch {
        case e: net.liftweb.json.MappingException => Left(parse(replaceEmptyObjects(result)).extract[PAPIErrorResponse])
      }
    }
}
