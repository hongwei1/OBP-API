package com.tesobe.obp

import net.liftweb.json.JsonParser._

/**
  * Created by work on 6/12/17.
  */
object NtibMf {
  
  def getNtibMf(mainframe: String): String = {
    val source = scala.io.Source.fromFile(mainframe)
    val lines = try source.mkString finally source.close()
    lines
  }
  
  
  
  def getIban(mainframe: String) = {
    val parser = (p: Parser) => {
      def parse: String = p.nextToken match {
        case FieldStart("TS00_IBAN") => p.nextToken match {
          case StringVal(token) => token
          case _ => p.fail("expected string")
        }
        case End => p.fail("no field named 'TS00_IBAN'")
        case _ => parse
      }

      parse
    }
    parse(getNtibMf(mainframe), parser)
  }
}
