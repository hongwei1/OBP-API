package com.tesobe.obp

import com.tesobe.obp.JoniMf.{config, replaceEmptyObjects}
import com.typesafe.scalalogging.StrictLogging
import net.liftweb.json.JsonAST.compactRender
import net.liftweb.json.{JValue, parse}
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.DefaultHttpClient

object Ntbd1v135Mf extends StrictLogging{

  def getNtbd1v135MfHttpApache(branch: String,
                               accountType: String, 
                               accountNumber: String, 
                               username: String,
                               cbsToken: String,
                               mobileNumberOfMoneySender:String,
                               mobileNumberOfMoneyReceiver: String,
                               description: String,
                               transferAmount: String): Ntbd1v135 = {

    val client = new DefaultHttpClient()
    val url = config.getString("bankserver.url")
    implicit val formats = net.liftweb.json.DefaultFormats
    
    //OBP-Adapter_Leumi/Doc/MFServices/NTBD_1_135 Sample.txt
    val post = new HttpPost(url + "/ESBLeumiDigitalBank/PAPI/v1.0/NTBD/1/135/01.01")
    post.addHeader("Content-Type", "application/json;charset=utf-8")
    val json: JValue =parse(s"""
    {
    	"NTBD_1_135": {
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
    		"KELET_1351": {
    			"K135_MISPAR_KARTIS": "0000000000000000",
    			"K135_TOKEF_KARTIS": "",
    			"K135_TIKRAT_KARTIS": "",
    			"K135_NAYAD_BAAL_CHESHBON": "$mobileNumberOfMoneySender",
    			"K135_SCHUM": "",
    			"K135_MATRAT_HAAVARA": "$description",
    			"K135_MISPAR_ZIHUY_MUTAV": "",
    			"K135_SUG_ZIHUY_MUTAV": "",
    			"K135_ERETZ_MUTAV": "",
    			"K135_SHEM_MUTAV": "",
    			"K135_TARICH_LEDA_MUTAV": "",
    			"K135_NAYAD_MUTAV": "$mobileNumberOfMoneyReceiver",
    			"K135_SCHUM_HADASH": "$transferAmount"
    		}
    	}
    }""")
    
    val jsonBody = new StringEntity(compactRender(json))
    post.setEntity(jsonBody)
    logger.debug("NTBD_1_135--Request : "+post.toString +"\n Body is :" + compactRender(json))
    val response = client.execute(post)
    val inputStream = response.getEntity.getContent
    val result = scala.io.Source.fromInputStream(inputStream).mkString
    response.close()
    logger.debug("NTBD_1_135--Response : "+response.toString+ "\n Body is :"+result)
    parse(replaceEmptyObjects(result)).extract[Ntbd1v135]
  }

}
