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

  def getNtlv1MfHttpApache(username: String, idNumber: String, idType: String, cbsToken: String): Ntlv1  = {
  
    val client = new DefaultHttpClient()
    val url = config.getString("bankserver.url")
    
    //OBP-Adapter_Leumi/Doc/MFServices/NTLV_1_000 Sample.txt
    val post = new HttpPost(url + "/ESBLeumiDigitalBank/PAPI/v1.0/NTLV/1/000/01.01")
    post.addHeader("Content-Type", "application/json;charset=utf-8")
    val json: JValue = parse(s"""
    {
      "NTLV_1_000": {
        "NtdriveCommonHeader": {
          "KeyArguments": {
            "Branch": "000",
            "IDNumber": "$idNumber",
            "IDType": "$idType",
            "IDCounty": "2121" 
          },
          "AuthArguments": {
            "User": "$username"
            "MFToken":"$cbsToken"
          }
        }
      }
    }
    """)
    
    val jsonBody = new StringEntity(compactRender(json))
    post.setEntity(jsonBody)
    logger.debug("NTLV_1_000--Request : "+post.toString +"\n Body is :" + compactRender(json))
    val response = client.execute(post)
    val inputStream = response.getEntity.getContent
    val result = scala.io.Source.fromInputStream(inputStream).mkString
    response.close()
    logger.debug("NTLV_1_000--Request : "+post.toString +"\n Body is :" + compactRender(json))
    implicit val formats = net.liftweb.json.DefaultFormats
    parse(replaceEmptyObjects(result)).extract[Ntlv1]
  }

}
