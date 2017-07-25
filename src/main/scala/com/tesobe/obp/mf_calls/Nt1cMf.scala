package com.tesobe.obp

import com.tesobe.obp.NtibMf.getNtibMf
import net.liftweb.json.JsonDSL._
import net.liftweb.json.JsonParser._


/**
  * Created by work on 6/12/17.
  */
object Nt1cMf {
  //Read file To Simulate Mainframe Call
  def getNt1cMf(mainframe: String): String = {
    val source = scala.io.Source.fromResource(mainframe)
    val lines = try source.mkString finally source.close()
    lines
  }
  def getBalance(json: String): (String) = {
    val call = (getNtibMf(json)) 
    val parser = (p: Parser) => {
      def parse: (String) = p.nextToken match {
        case FieldStart("HH_ITRA_NOCHECHIT") => p.nextToken match {
          case StringVal(token) => token
          case _ => p.fail("expected string")
        }
        case End => p.fail("no field named 'HH_ITRA_NOCHECHIT'")
        case _ => parse
      }

      parse
    }
    parse(call, parser)
  }
  def getLimit(json: String): (String) = {
    val call = (getNtibMf(json))
    val parser = (p: Parser) => {
      
      def parse: (String) = p.nextToken match {
        case FieldStart("HH_MISGERET_ASHRAI") => p.nextToken match {
          case StringVal(token) => token
          case _ => p.fail("expected string")
        }
        case End => p.fail("no field named 'HH_MISGERET_ASHRAI'")
        case _ => parse
      }

      parse
    }
    parse(call, parser)
  }


  def getLimitJsonAst(json: String): (String) = {
    val call = (getNtibMf(json))
    (call \\ "HH_MISGERET_ASHRAI").toString
  }

  
}
