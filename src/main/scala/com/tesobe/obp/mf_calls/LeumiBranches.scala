package com.tesobe.obp

import com.tesobe.obp.HttpClient.getLeumiBranchesFromBank

import scala.collection.mutable.ListBuffer


object LeumiBranches extends Config{
  
  case class Shaot(ob1: String,
                   ob2: String,
                   ob3: String,
                   ob4: String,
                   ob5: String,
                   ob6: String,
                   ob7: String,
                   oe1: String,
                   oe2: String,
                   oe3: String,
                   oe4: String,
                   oe5: String,
                   oe6: String,
                   oe7: String,
                   cb1: String,
                   cb2: String,
                   cb3: String,
                   cb4: String,
                   cb5: String,
                   cb6: String,
                   cb7: String,
                   ce1: String,
                   ce2: String,
                   ce3: String,
                   ce4: String,
                   ce5: String,
                   ce6: String,
                   ce7: String
                  )
  
case class LeumiBranch(
                      branchCode : String,
                      name: String,
                      address: String,
                      zipcode: String,
                      city: String,
                      phone: String,
                      fax: String,
                      shaot: Shaot,
                      x: String,
                      y: String,
                      accessibility: Boolean
                      )
  
  def getLeumiBranches: List[LeumiBranch] = {
    //val branchXML = xml.XML.loadString(getLeumiBranchesFromBank())
    val branchXML = xml.XML.loadFile("/home/work/.IdeaProjects/OBP-Adapter_Leumi-GITLAB/src/main/resources/someBranches.xml")
    var result = new ListBuffer[LeumiBranch]
    for (y <- branchXML \\ "city") {
      val cityName = (y \ "@name").text

      for (i <- branchXML \\ "branch") {
        println(i)
        val branchCode = (i \ "@code").text
        val name = (i \ "name").text
        val address = (i \ "address").text
        val zipcode = (i \ "zipcode").text
        val countryCode = ""
        val phone = (i \ "phone").text
        val fax = (i \ "fax").text
        val shaot = Shaot(ob1 = (i \ "shaot" \ "@ob1").text,
          ob2 = (i \ "shaot" \ "@ob2").text,
          ob3 = (i \ "shaot" \ "@ob3").text,
          ob4 = (i \ "shaot" \ "@ob4").text,
          ob5 = (i \ "shaot" \ "@ob5").text,
          ob6 = (i \ "shaot" \ "@ob6").text,
          ob7 = (i \ "shaot" \ "@ob7").text,
          oe1 = (i \ "shaot" \ "@oe1").text,
          oe2 = (i \ "shaot" \ "@oe2").text,
          oe3 = (i \ "shaot" \ "@oe3").text,
          oe4 = (i \ "shaot" \ "@oe4").text,
          oe5 = (i \ "shaot" \ "@oe5").text,
          oe6 = (i \ "shaot" \ "@oe6").text,
          oe7 = (i \ "shaot" \ "@oe7").text,
          cb1 = (i \ "shaot" \ "@cb1").text,
          cb2 = (i \ "shaot" \ "@cb2").text,
          cb3 = (i \ "shaot" \ "@cb3").text,
          cb4 = (i \ "shaot" \ "@cb4").text,
          cb5 = (i \ "shaot" \ "@cb5").text,
          cb6 = (i \ "shaot" \ "@cb6").text,
          cb7 = (i \ "shaot" \ "@cb7").text,
          ce1 = (i \ "shaot" \ "@ce1").text,
          ce2 = (i \ "shaot" \ "@ce2").text,
          ce3 = (i \ "shaot" \ "@ce3").text,
          ce4 = (i \ "shaot" \ "@ce4").text,
          ce5 = (i \ "shaot" \ "@ce5").text,
          ce6 = (i \ "shaot" \ "@ce6").text,
          ce7 = (i \ "shaot" \ "@ce7").text
        )
        val x = (i \ "x").text
        val y = (i \ "y").text
        val accesibility = if ((i \\ "Access1").text == "נגישות לכסא גלגלים") true else false
        result += LeumiBranch(branchCode, name, address, zipcode,cityName, phone, fax, shaot, x, y, accesibility)
      }
    }
    result.toList
  }
}
