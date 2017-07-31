package com.tesobe.obp

import com.tesobe.obp.JoniMf.replaceEmptyObjects
import net.liftweb.json.parse


object Nt1cTMf {
  //Read file To Simulate Mainframe Call
  implicit val formats = net.liftweb.json.DefaultFormats
  def getNt1cTMf(mainframe: String): String = {
    val source = scala.io.Source.fromResource(mainframe)
    val lines = try source.mkString finally source.close()
    lines
  }
  
  //@param: Filepath for json result stub
  def getCompletedTransactions(mainframe: String): Nt1cT = {
    val json = getNt1cTMf(mainframe)
    val jsonAst = parse(json)
    jsonAst.extract[Nt1cT]
  }
  
}
