package com.tesobe.obp

import com.tesobe.obp.JoniMf.replaceEmptyObjects
import net.liftweb.json.parse


object Nt1c4Mf {
  //Read file To Simulate Mainframe Call
  implicit val formats = net.liftweb.json.DefaultFormats
  def getNt1c4Mf(mainframe: String): String = {
    val source = scala.io.Source.fromFile(mainframe)
    val lines = try source.mkString finally source.close()
    lines
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
