package com.tesobe.obp

import org.mockserver.integration.ClientAndServer.startClientAndServer
import org.mockserver.model.{Cookie, Header, Parameter}
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response



package object RunMockServer {
  val mockServer = startClientAndServer(1080)
  
  def jsonToString(filename: String): String = {
    val source = scala.io.Source.fromResource(filename)
    val lines = try source.mkString finally source.close()
    lines
  }
  
  def startMockServer = {
    // JONI
    mockServer
      .when(
        request()
          .withMethod("POST")
          //.withHeader("Content-Type","application/json;charset=utf-8")
          .withPath("/ESBLeumiDigitalBank/PAPI/V1.0/JONI/0/000/01.01")
          //.withBody("body")
      )
      .respond(
        response()
          .withStatusCode(401)
          .withHeaders(
            new Header("Content-Type", "application/json; charset=utf-8")
          )
          .withBody(jsonToString("joni_result.json"))
      )
    //Nt1cB
    mockServer
      .when(
        request()
          .withMethod("POST")
          //.withHeader("Content-Type","application/json;charset=utf-8")
          .withPath("/ESBLeumiDigitalBank/PAPI/v1.0/NT1C/B/000/01.02")
        //.withBody("body")
      )
      .respond(
        response()
          .withStatusCode(401)
          .withHeaders(
            new Header("Content-Type", "application/json; charset=utf-8")
          )
          .withBody(jsonToString("nt1c_B_result.json"))
      )
    Nt1cT
    mockServer
      .when(
        request()
          .withMethod("POST")
          //.withHeader("Content-Type","application/json;charset=utf-8")
          .withPath("/ESBLeumiDigitalBank/PAPI/v1.0/NT1C/T/000/01.02")
        //.withBody("body")
      )
      .respond(
        response()
          .withStatusCode(401)
          .withHeaders(
            new Header("Content-Type", "application/json; charset=utf-8")
          )
          .withBody(jsonToString("nt1c_T_result.json"))
      )
    //Nt1c3
    mockServer
      .when(
        request()
          .withMethod("POST")
          //.withHeader("Content-Type","application/json;charset=utf-8")
          .withPath("/ESBLeumiDigitalBank/PAPI/v1.0/NT1C/3/000/01.02")
        //.withBody("body")
      )
      .respond(
        response()
          .withStatusCode(401)
          .withHeaders(
            new Header("Content-Type", "application/json; charset=utf-8")
          )
          .withBody(jsonToString("nt1c_3_result.json"))
      )
    //Nt1c4
    mockServer
      .when(
        request()
          .withMethod("POST")
          //.withHeader("Content-Type","application/json;charset=utf-8")
          .withPath("/ESBLeumiDigitalBank/PAPI/v1.0/NT1C/4/000/01.03")
        //.withBody("body")
      )
      .respond(
        response()
          .withStatusCode(401)
          .withHeaders(
            new Header("Content-Type", "application/json; charset=utf-8")
          )
          .withBody(jsonToString("nt1c_4_result.json"))
      )
    
    //NtIb2
    mockServer
      .when(
        request()
          .withMethod("POST")
          //.withHeader("Content-Type","application/json;charset=utf-8")
          .withPath("/ESBLeumiDigitalBank/PAPI/v1.0/NTIB/2/000/01.01")
        //.withBody("body")
      )
      .respond(
        response()
          .withStatusCode(401)
          .withHeaders(
            new Header("Content-Type", "application/json; charset=utf-8")
          )
          .withBody(jsonToString("ntib_result.json"))
      )
    
    //Ntlv1
    mockServer
      .when(
        request()
          .withMethod("POST")
          //.withHeader("Content-Type","application/json;charset=utf-8")
          .withPath("/ESBLeumiDigitalBank/PAPI/v1.0/NTLV/1/000/01.01")
        //.withBody("body")
      )
      .respond(
        response()
          .withStatusCode(401)
          .withHeaders(
            new Header("Content-Type", "application/json; charset=utf-8")
          )
          .withBody(jsonToString("ntlv_1_result.json")) 
      )
    
    //Ntbd1v135
    mockServer
      .when(
        request()
          .withMethod("POST")
          //.withHeader("Content-Type","application/json;charset=utf-8")
          .withPath("/ESBLeumiDigitalBank/PAPI/v1.0/NTBD/1/135/01.01")
        //.withBody("body")
      )
      .respond(
        response()
          .withStatusCode(401)
          .withHeaders(
            new Header("Content-Type", "application/json; charset=utf-8")
          )
          .withBody(jsonToString("ntbd1_135_result.json"))
      )


  }
  
  

}
