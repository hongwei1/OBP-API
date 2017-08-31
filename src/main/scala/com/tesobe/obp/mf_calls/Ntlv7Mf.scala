package com.tesobe.obp

import com.tesobe.obp.JoniMf.{config, replaceEmptyObjects}
import net.liftweb.json.JValue
import net.liftweb.json.JsonAST.compactRender
import net.liftweb.json.JsonDSL._
import net.liftweb.json.JsonParser._
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.DefaultHttpClient

object Ntlv7Mf {

  def getNtlv7MfHttpApache(branch: String,
                               accountType: String,
                               accountNumber: String,
                               username: String,
                               cbsToken: String,
                               ntlv1TargetMobileNumberPrefix: String,
                               ntlv1TargetMobileNumber: String
                              ): Ntlv7 = {

    val url = config.getString("bankserver.url")
    implicit val formats = net.liftweb.json.DefaultFormats
    val post = new HttpPost(url + "/ESBLeumiDigitalBank/PAPI/v1.0/NTLV/7/000/01.01")
    post.addHeader("Content-Type", "application/json;charset=utf-8")

    val client = new DefaultHttpClient()

    val json: JValue = "NTBD_1_135" -> (("NtdriveCommonHeader" -> ("KeyArguments" -> ("Branch" -> branch) ~ ("AccountType" ->
      accountType) ~ ("AccountNumber" -> accountNumber)) ~ ("AuthArguments" -> (("User" -> username) ~ ("MFToken" -> cbsToken)))) ~
      ("DFH_KLT" -> ("DFH_TEL_KID" -> ntlv1TargetMobileNumberPrefix) ~ ("DFH_TEL_MIS" -> ntlv1TargetMobileNumber)))
    val jsonBody = new StringEntity(compactRender(json))
    post.setEntity(jsonBody)

    val response = client.execute(post)
    val inputStream = response.getEntity.getContent
    val result = scala.io.Source.fromInputStream(inputStream).mkString
    response.close()
    parse(replaceEmptyObjects(result)).extract[Ntlv7]
  }

}