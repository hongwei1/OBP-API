package com.tesobe.obp
import com.tesobe.obp.JoniMf.{config, replaceEmptyObjects}
import com.tesobe.obp.{Ntbd1v105, Ntbd1v135}
import com.typesafe.scalalogging.StrictLogging
import net.liftweb.json.JsonAST.compactRender
import net.liftweb.json.{JValue, parse}
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.DefaultHttpClient

object Ntbd1v105Mf extends StrictLogging{

  def getNtbd1v105MfHttpApache(branch: String,
                               accountType: String,
                               accountNumber: String,
                               cbsToken: String,
                               cardNumber: String,
                               cardExpirationDate: String,
                               cardWithdrawalLimit: String,
                               mobileNumberOfMoneySender:String,
                               amount: String,
                               description: String,
                               idNumber: String,
                               idType: String,
                               nameOfMoneyReceiver: String,
                               birthDateOfMoneyReceiver: String,
                               mobileNumberOfMoneyReceiver: String): Ntbd1v105 = {

    val client = new DefaultHttpClient()
    val url = config.getString("bankserver.url")
    implicit val formats = net.liftweb.json.DefaultFormats

    val post = new HttpPost(url + "/ESBLeumiDigitalBank/PAPI/v1.0/NTBD/1/135/01.01")
    post.addHeader("Content-Type", "application/json;charset=utf-8")
        val json: JValue =parse(s"""
{
      "NTBD_1_105": {
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
        "KELET_1351": {
        "K135_MISPAR_KARTIS": "$cardNumber",
        "K135_TOKEF_KARTIS": "$cardExpirationDate",
        "K135_TIKRAT_KARTIS": "$cardWithdrawalLimit",
        "K135_NAYAD_BAAL_CHESHBON": "$mobileNumberOfMoneySender",
        "K135_SCHUM": "$amount",
        "K135_MATRAT_HAAVARA": "$description",
        "K135_MISPAR_ZIHUY_MUTAV": "$idNumber",
        "K135_SUG_ZIHUY_MUTAV": "$idType",
        "K135_ERETZ_MUTAV": "2121",
        "K135_SHEM_MUTAV": "$nameOfMoneyReceiver",
        "K135_TARICH_LEDA_MUTAV": "$birthDateOfMoneyReceiver",
        "K135_NAYAD_MUTAV": "$mobileNumberOfMoneyReceiver",
        "K135_SCHUM_HADASH": ""
      }
      }
    }""")

    val jsonBody = new StringEntity(compactRender(json))
    post.setEntity(jsonBody)
    logger.debug("NTBD_1_105--Request : "+post.toString +"\n Body is :" + compactRender(json))
    val response = client.execute(post)
    val inputStream = response.getEntity.getContent
    val result = scala.io.Source.fromInputStream(inputStream).mkString
    response.close()
    logger.debug("NTBD_1_105--Response : "+response.toString+ "\n Body is :"+result)
    parse(replaceEmptyObjects(result)).extract[Ntbd1v105]
  }

}
