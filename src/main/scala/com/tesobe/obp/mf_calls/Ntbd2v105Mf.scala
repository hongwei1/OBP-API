package com.tesobe.obp
import com.tesobe.obp.JoniMf.{config, replaceEmptyObjects}
import com.tesobe.obp.{Ntbd1v105, Ntbd1v135}
import com.typesafe.scalalogging.StrictLogging
import net.liftweb.json.JsonAST.compactRender
import net.liftweb.json.{JValue, parse}
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.DefaultHttpClient

object Ntbd2v105Mf extends StrictLogging{

  def getNtbd2v105MfHttpApache(branch: String,
                               accountType: String,
                               accountNumber: String,
                               cbsToken: String,
                               ntbd1v105Token: String): Ntbd2v105 = {

    val client = new DefaultHttpClient()
    val url = config.getString("bankserver.url")
    implicit val formats = net.liftweb.json.DefaultFormats

    val post = new HttpPost(url + "/ESBLeumiDigitalBank/PAPI/v1.0/NTBD/2/105/01.01")
    post.addHeader("Content-Type", "application/json;charset=utf-8")
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
          "K135_KINUY_MAVIR": "",
          "K135_MELEL_LE_MUTAV": ""
        }
        }
      }""")

    val jsonBody = new StringEntity(compactRender(json))
    post.setEntity(jsonBody)
    logger.debug("NTBD_2_105--Request : "+post.toString +"\n Body is :" + compactRender(json))
    val response = client.execute(post)
    val inputStream = response.getEntity.getContent
    val result = scala.io.Source.fromInputStream(inputStream).mkString
    response.close()
    logger.debug("NTBD_2_105--Response : "+response.toString+ "\n Body is :"+result)
    parse(replaceEmptyObjects(result)).extract[Ntbd2v105]
  }

}
