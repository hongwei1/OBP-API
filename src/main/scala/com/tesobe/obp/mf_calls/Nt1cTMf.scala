package com.tesobe.obp

import com.tesobe.obp.JoniMf.replaceEmptyObjects
import net.liftweb.json.JsonAST.compactRender
import net.liftweb.json.{JValue, parse}
import net.liftweb.json.JsonDSL._
import net.liftweb.json.JsonParser._
import org.apache.http.client.methods.HttpPost
import org.apache.http.impl.client.DefaultHttpClient
import java.util.Date


object Nt1cTMf {
  //Read file To Simulate Mainframe Call
  implicit val formats = net.liftweb.json.DefaultFormats
  def getNt1cTMf(mainframe: String): String = {
    val source = scala.io.Source.fromFile(mainframe)
    val lines = try source.mkString finally source.close()
    lines
  }

  def getNt1cTMfHttpApache(branch: String,
                           accountType: String,
                           accountNumber: String, 
                           mfToken: String,
                           startDate: List[String],
                           endDate: List[String],
                           maxNumberOfTransactions: String): String = {

    val url = "http://localhost"


    val post = new HttpPost(url + "/ESBLeumiDigitalBank/PAPI/v1.0/NT1C/4/000/01.03")
    println(post)
    post.addHeader("application/json;charset=utf-8","application/json;charset=utf-8")

    val client = new DefaultHttpClient()

    val json: JValue = "NT1C_T_000" -> ("NtdriveCommonHeader" -> ("KeyArguments" -> ("Branch" -> branch) ~ ("AccountType" ->
      accountType) ~ ("AccountNumber" -> accountNumber)) ~ ("AuthArguments" ->("MFToken" -> mfToken))) ~ ("KELET_TAARICHIM" ->
      ("KELET_ME_TAAR" -> ("KELET_ME_YYYY" -> startDate(0)) ~ ("KELET_ME_MM" -> startDate(1)) ~ ("KELET_ME_DD" -> startDate(2))) ~
        ("KELET_AD_TAAR" -> ("KELET_AD_YYYY" -> endDate(0)) ~ ("KELET_AD_MM" -> endDate(1)) ~ ("KELET_AD_DD" -> endDate(2))) ~
        ("KELET_TN_MIS_TNUOT" -> maxNumberOfTransactions))
        
    println(compactRender(json))

    // send the post request
    val response = client.execute(post)
    val inputStream = response.getEntity.getContent
    val result = scala.io.Source.fromInputStream(inputStream).mkString
    response.close()
    result
  }
  //@param: Filepath for json result stub
  def getCompletedTransactions(mainframe: String): Nt1cT = {
    val json = replaceEmptyObjects(getNt1cTMf(mainframe))
    val jsonAst = parse(json)
    println(jsonAst)
    jsonAst.extract[Nt1cT]
  }
  
}
