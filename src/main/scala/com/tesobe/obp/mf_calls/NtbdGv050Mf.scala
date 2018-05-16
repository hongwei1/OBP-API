package com.tesobe.obp

import com.tesobe.obp.ErrorMessages.InvalidIdTypeException
import com.tesobe.obp.HttpClient.makePostRequest
import com.tesobe.obp.JoniMf.replaceEmptyObjects
import net.liftweb.json.JValue
import net.liftweb.json.JsonParser.parse

import scala.util.control.NoStackTrace

object NtbdGv050Mf extends Config {

  def getNtbdGv050(branch: String,
                   accountType: String,
                   accountNumber: String,
                   cbsToken: String,
                   ntbdAv050Token: String,
                   bankTypeOfTo: String
                  ): Either[PAPIErrorResponse, NtbdGv050] = {

    val path = config.getString("backendCalls.NTBD_G_050")
    if (bankTypeOfTo != "0" && bankTypeOfTo != "1") throw new Exception("invalid bank type") with NoStackTrace
    val json: JValue = parse(s"""
    {
      "NTBD_G_050": {
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
        "K050_AMALOTIN": {
        "K050_TOKEN": "$ntbdAv050Token",
        "K050_SUG_BANK_YAAD": "$bankTypeOfTo",
        "K050_SEMEL_KVUTZA": "0"
      }
      }
    }""")

    val result = makePostRequest(json, path)
    implicit val formats = net.liftweb.json.DefaultFormats
    try {
      Right(parse(replaceEmptyObjects(result)).extract[NtbdGv050])
    } catch {
      case e: net.liftweb.json.MappingException => Left(parse(replaceEmptyObjects(result)).extract[PAPIErrorResponse])
    }
  }

}