package com.tesobe.obp

import com.tesobe.obp.JoniMf.{config, replaceEmptyObjects}
import net.liftweb.json.JValue
import net.liftweb.json.JsonAST.compactRender
import net.liftweb.json.JsonDSL._
import net.liftweb.json.JsonParser._
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.DefaultHttpClient

package object Ntbd2v135Mf {

  def getNtbd2v135MfHttpApache(branch: String,
                               accountType: String, 
                               accountNumber: String, 
                               username: String,
                               cbsToken: String,
                               ntbd1v135_Token:String,
                               nicknameOfMoneySender: String,
                               messageToMoneyReceiver: String,
                               ): Ntbd2v135 = {

    val url = config.getString("bankserver.url")
    implicit val formats = net.liftweb.json.DefaultFormats
    val post = new HttpPost(url + "/ESBLeumiDigitalBank/PAPI/v1.0/NTBD/2/135/01.01")
    post.addHeader("Content-Type", "application/json;charset=utf-8")

    val client = new DefaultHttpClient()

    val json: JValue = "NTBD_1_135" -> (("NtdriveCommonHeader" -> ("KeyArguments" -> ("Branch" -> branch) ~ ("AccountType" -> 
      accountType) ~ ("AccountNumber" -> accountNumber)) ~ ("AuthArguments" -> (("User" -> username) ~("MFToken" -> cbsToken)))) ~
      ("KELET_1352" -> ("K135_TOKEN_ISHUR" -> ntbd1v135_Token) ~ ("K135_BAKASH_TASHL" -> "1") ~ 
        ("K135_KINUY_MAVIR" -> nicknameOfMoneySender) ~ ("K135_MELEL_LE_MUTAV" -> messageToMoneyReceiver)))
    val jsonBody = new StringEntity(compactRender(json))
    post.setEntity(jsonBody)
    
    val response = client.execute(post)
    val inputStream = response.getEntity.getContent
    val result = scala.io.Source.fromInputStream(inputStream).mkString
    response.close()
    parse(replaceEmptyObjects(result)).extract[Ntbd2v135]
  }

}
