package com.tesobe.obp

import com.tesobe.obp.HttpClient.makePostRequest
import com.tesobe.obp.JoniMf.replaceEmptyObjects
import com.typesafe.scalalogging.StrictLogging
import net.liftweb.json.JValue
import net.liftweb.json.JsonParser._


object Ntlv7Mf extends Config with StrictLogging{

  def getNtlv7Mf(branch: String,
                 accountType: String,
                 accountNumber: String,
                 username: String,
                 cbsToken: String,
                 ntlv1TargetMobileNumberPrefix: String,
                 ntlv1TargetMobileNumber: String
                              ): Ntlv7 = {

    val path = config.getString("backendCalls.NTLV_7_000")

    val json: JValue =parse(s"""
    { 
    	"NTLV_7_000":
    	{
    		"NtdriveCommonHeader":
    		{
    			"KeyArguments":
    			{
    				"Branch": "$branch",
    				"AccountType": "$accountType",
    				"AccountNumber": "$accountNumber"
    			},
    			"AuthArguments":
    			{
    				"User": "$username"
    				"MFToken":"$cbsToken"
    			}
    		},
    		"DFH_KLT":
    		{
    			"DFH_TEL_KID": "$ntlv1TargetMobileNumberPrefix",
    			"DFH_TEL_MIS": "$ntlv1TargetMobileNumber"
    		}
    	}
    }
    """)

    val result = makePostRequest(json,path)
    implicit val formats = net.liftweb.json.DefaultFormats
    parse(replaceEmptyObjects(result)).extract[Ntlv7]
  }

}