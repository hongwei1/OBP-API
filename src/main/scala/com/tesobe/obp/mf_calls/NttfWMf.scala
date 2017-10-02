package com.tesobe.obp

import com.tesobe.obp.JoniMf.{config, replaceEmptyObjects}
import com.tesobe.obp.Ntlv7Mf.logger
import com.typesafe.scalalogging.StrictLogging
import net.liftweb.json.JValue
import net.liftweb.json.JsonAST.compactRender
import net.liftweb.json.JsonParser.parse
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.DefaultHttpClient

object NttfWMf extends StrictLogging{

  def getNttfWMMfHttpApache(branch: String,
                           accountType: String,
                           accountNumber: String,
                           username: String,
                           cbsToken: String,
                           ntlv1TargetMobileNumberPrefix: String,
                           ntlv1TargetMobileNumber: String
                          ) = {

    val client = new DefaultHttpClient()
    val url = config.getString("bankserver.url")

    //OBP-Adapter_Leumi/Doc/MFServices/NTLV_7_000 Sample.txt
    val post = new HttpPost(url + "/ESBLeumiDigitalBank/PAPI/v1.0/NTTF/W/000/01.01")
    post.addHeader("Content-Type", "application/json;charset=utf-8")
    val json: JValue =parse(s"""
     {
      "NTTF_W_000": {
        "NtdriveCommonHeader": {
          "KeyArguments": {
            "Branch": "$branch",
            "AccountType": "$accountType",
            "AccountNumber": "$accountNumber"
          },
          "AuthArguments": {
            "MFToken": "$cbsToken"
          }
        }
      }
     }
    """)

    val jsonBody = new StringEntity(compactRender(json))
    logger.debug("NTTF_W_000--Request : "+post.toString +"\n Body is :" + compactRender(json))
    post.setEntity(jsonBody)
    val response = client.execute(post)
    val inputStream = response.getEntity.getContent
    val result = scala.io.Source.fromInputStream(inputStream).mkString
    response.close()
    logger.debug("NTTF_W_000--Response : "+response.toString+ "\n Body is :"+result)

    implicit val formats = net.liftweb.json.DefaultFormats
    parse(replaceEmptyObjects(result)).extract[NttfW]
  }

}