package com.tesobe.obp

import com.tesobe.obp.JoniMf.replaceEmptyObjects
import net.liftweb.json.parse


object Nt1c3Mf {
  //Read file To Simulate Mainframe Call
  implicit val formats = net.liftweb.json.DefaultFormats
  def getNt1c3Mf(mainframe: String): String = {
    val source = scala.io.Source.fromFile(mainframe)
    val lines = try source.mkString finally source.close()
    lines
  }
  //@param: Filepath for json result stub
  def getFutureTransactions(mainframe: String) = {
    val json = replaceEmptyObjects(getNt1c3Mf(mainframe))
    val jsonAst = parse(json)
    println(jsonAst)
    val nt1c3Call: Nt1c3 = jsonAst.extract[Nt1c3]
    nt1c3Call
  }
  
}
