package com.tesobe.obp

import com.tesobe.obp.JoniMf.replaceEmptyObjects
import net.liftweb.json.JsonAST.compactRender
import net.liftweb.json.{JValue, parse}
import net.liftweb.json.JsonDSL._
import net.liftweb.json.JsonParser._
import org.apache.http.client.methods.HttpPost
import org.apache.http.impl.client.DefaultHttpClient


object Nt1c4Mf extends Config{
  //Read file To Simulate Mainframe Call
  implicit val formats = net.liftweb.json.DefaultFormats
  def getNt1c4Mf(mainframe: String): String = {
    val source = scala.io.Source.fromResource(mainframe)
    val lines = try source.mkString finally source.close()
    lines
  }
  
  def getNt1c4MfHttpApache(branch: String, accountType: String, accountNumber: String, username: String, mfToken: String): String = {

    val url = config.getString("bankserver.url")


    val post = new HttpPost(url + "/ESBLeumiDigitalBank/PAPI/v1.0/NT1C/4/000/01.03")
    println(post)
    post.addHeader("application/json;charset=utf-8","application/json;charset=utf-8")

    val client = new DefaultHttpClient()

    val json: JValue = "NT1C_4_000" -> ("NtdriveCommonHeader" -> ("KeyArguments" -> ("Branch" -> branch) ~ ("AccountType" ->
      accountType) ~ ("AccountNumber" -> accountNumber)) ~ ("AuthArguments" ->( ("User" -> username) ~ ("MFToken" -> mfToken))))
    println(compactRender(json))

    // send the post request
    val response = client.execute(post)
    val inputStream = response.getEntity.getContent
    val result = scala.io.Source.fromInputStream(inputStream).mkString
    response.close()
    result
  }
  //@param: Filepath for json result stub
  def getIntraDayTransactions(mainframe: String) = {
    val json = replaceEmptyObjects(getNt1c4Mf(mainframe))
    val jsonAst = parse(json)
    println(jsonAst)
    val nt1c4Call: Nt1c4 = jsonAst.extract[Nt1c4]
    nt1c4Call
  }
  
}
