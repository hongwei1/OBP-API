package com.tesobe.obp

import com.tesobe.obp.JoniMf.{config, replaceEmptyObjects}
import com.typesafe.scalalogging.StrictLogging
import net.liftweb.json.JValue
import net.liftweb.json.JsonAST.compactRender
import net.liftweb.json.JsonDSL._
import net.liftweb.json.JsonParser._
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.DefaultHttpClient

object Ntlv1Mf extends StrictLogging{

  def getNtlv1MfHttpApache(branch: String, idNumber: String, idType: String, idCounty: String, cbsToken: String): Ntlv1  = {

    //val url = "http://localhost"
    val url = config.getString("bankserver.url")
    logger.debug(url)

    val post = new HttpPost(url + "/ESBLeumiDigitalBank/PAPI/v1.0/NTLV/1/000/01.01")
    logger.debug(post.toString)
    post.addHeader("Content-Type", "application/json;charset=utf-8")

    val client = new DefaultHttpClient()

    val json: JValue = "NTLV_1_000" -> ("NtdriveCommonHeader" -> ("KeyArguments" -> ("Branch" -> branch) ~ ("IDNumber" -> 
      idNumber) ~ ("IDType" -> idType) ~ ("IDCounty" -> idCounty)) ~ ("AuthArguments" -> ("MFToken" -> cbsToken)))
    val jsonBody = new StringEntity(compactRender(json))
    post.setEntity(jsonBody)

    val response = client.execute(post)
    val inputStream = response.getEntity.getContent
    val result = scala.io.Source.fromInputStream(inputStream).mkString
    response.close()
    implicit val formats = net.liftweb.json.DefaultFormats
    parse(replaceEmptyObjects(result)).extract[Ntlv1]
  }

}
