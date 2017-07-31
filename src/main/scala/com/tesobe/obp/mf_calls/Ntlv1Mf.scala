package com.tesobe.obp

import com.tesobe.obp.JoniMf.config
import net.liftweb.json.JValue
import net.liftweb.json.JsonAST.compactRender
import net.liftweb.json.JsonDSL._
import net.liftweb.json.JsonParser._
import org.apache.http.client.methods.HttpPost
import org.apache.http.impl.client.DefaultHttpClient

package object Ntlv1Mf {

  def getNtlv1MfHttpApache(branch: String, idNumber: String, idType: String, idCounty: String, cbsToken: String): String = {

    //val url = "http://localhost"
    val url = config.getString("bankserver.url")
    println(url)

    val post = new HttpPost(url + "/ESBLeumiDigitalBank/PAPI/v1.0/NTLV/1/000/01.01")
    println(post)
    post.addHeader("Content-Type", "application/json;charset=utf-8")

    val client = new DefaultHttpClient()

    //val json: JValue = ("JONI_0_000" -> ("NtdriveCommonHeader" -> ("AuthArguments" -> ("UserName" -> username))))
    val json: JValue = "NTLV_1_000" -> ("NtdriveCommonHeader" -> ("KeyArguments" -> ("Branch" -> branch) ~ ("IDNumber" -> 
      idNumber) ~ ("IDType" -> idType) ~ ("IDCounty" -> idCounty)) ~ ("AuthArguments" -> ("MFToken" -> cbsToken)))
    println(compactRender(json))

    // send the post request
    val response = client.execute(post)
    //val response = client.execute(new HttpGet("http://localhost/V1.0/JONI/0/000/01.01"))
    val inputStream = response.getEntity.getContent
    val result = scala.io.Source.fromInputStream(inputStream).mkString
    response.close()
    println(result)
    result
  }

}
