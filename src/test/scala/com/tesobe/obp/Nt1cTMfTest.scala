package com.tesobe.obp

import com.tesobe.obp.Nt1cTMf._

class Nt1cTMfTest extends ServerSetup {

  implicit val formats = net.liftweb.json.DefaultFormats
  
/*  test("Nt1cTMf gets a result"){
    val result = getNt1cTMfHttpApache("","","","",List("","",""),List("","",""),"")
    logger.debug(result)
  }*/
  
  test("getCompletedTransactions works") {
    val result = getCompletedTransactions(
      "N7jut8d",
      "814",
      "330",
      "20102612",
      "<~9          81433020102612",
      List("2016",
        "01",
        "01"),
      List("2017", "06", "01"),
      "15"
    )
    logger.debug(result.toString)
  }

}
