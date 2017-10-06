package com.tesobe.obp

import com.typesafe.scalalogging.StrictLogging
import org.mockserver.integration.ClientAndServer.startClientAndServer
import org.mockserver.model.{Cookie, Header, Parameter}
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response



object RunMockServer extends StrictLogging{
  val mockServer = startClientAndServer(1080)
  
  def jsonToString(filename: String): String = {
    val source = scala.io.Source.fromResource(filename)
    val lines = try source.mkString finally source.close()
    lines
  }
  
  def startMockServer = {
    //1 JONI
    mockServer
      .when(
        request()
          .withMethod("POST")
          .withHeader("Content-Type","application/json;charset=utf-8")
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
    //2 Nt1cB
    mockServer
      .when(
        request()
          .withMethod("POST")
          .withHeader("Content-Type","application/json;charset=utf-8")
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
    //3 Nt1cT
    mockServer
      .when(
        request()
          .withMethod("POST")
          .withHeader("Content-Type","application/json;charset=utf-8")
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
    //4 Nt1c3
    mockServer
      .when(
        request()
          .withMethod("POST")
          .withHeader("Content-Type","application/json;charset=utf-8")
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
    //5 Nt1c4
    mockServer
      .when(
        request()
          .withMethod("POST")
          .withHeader("Content-Type","application/json;charset=utf-8")
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
    
    //6 NtIb2
    mockServer
      .when(
        request()
          .withMethod("POST")
          .withHeader("Content-Type","application/json;charset=utf-8")
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
    
    //7 Ntlv1
    mockServer
      .when(
        request()
          .withMethod("POST")
          .withHeader("Content-Type","application/json;charset=utf-8")
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
    
    //8 Ntbd1v135
    mockServer
      .when(
        request()
          .withMethod("POST")
          .withHeader("Content-Type","application/json;charset=utf-8")
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
    
    //9 Ntbd2v135
    mockServer
      .when(
        request()
          .withMethod("POST")
          .withHeader("Content-Type","application/json;charset=utf-8")
          .withPath("/ESBLeumiDigitalBank/PAPI/v1.0/NTBD/2/135/01.01")
        //.withBody("body")
      )
      .respond(
        response()
          .withStatusCode(401)
          .withHeaders(
            new Header("Content-Type", "application/json; charset=utf-8")
          )
          .withBody(jsonToString("ntbd2_135_result.json"))
      )

    //10 Ntlv7
    mockServer
      .when(
        request()
          .withMethod("POST")
          .withHeader("Content-Type","application/json;charset=utf-8")
          .withPath("/ESBLeumiDigitalBank/PAPI/v1.0/NTLV/7/000/01.01")
        //.withBody("body")
      )
      .respond(
        response()
          .withStatusCode(401)
          .withHeaders(
            new Header("Content-Type", "application/json; charset=utf-8")
          )
          .withBody(jsonToString("ntlv7_result.json"))
      )

    //11 NttfW
    mockServer
      .when(
        request()
          .withMethod("POST")
          .withHeader("Content-Type","application/json;charset=utf-8")
          .withPath("/ESBLeumiDigitalBank/PAPI/v1.0/NTTF/W/000/01.01")
        //.withBody("body")
      )
      .respond(
        response()
          .withStatusCode(401)
          .withHeaders(
            new Header("Content-Type", "application/json; charset=utf-8")
          )
          .withBody(jsonToString("nttfW_result.json"))
      )
    //12 Ntbd1v105
    mockServer
      .when(
        request()
          .withMethod("POST")
          .withHeader("Content-Type","application/json;charset=utf-8")
          .withPath("/ESBLeumiDigitalBank/PAPI/v1.0/NTBD/1/105/01.01")
        //.withBody("body")
      )
      .respond(
        response()
          .withStatusCode(401)
          .withHeaders(
            new Header("Content-Type", "application/json; charset=utf-8")
          )
          .withBody(jsonToString("ntbd1_105_result.json"))
      )
    
    //13 Ntbd2v105
    mockServer
      .when(
        request()
          .withMethod("POST")
          .withHeader("Content-Type","application/json;charset=utf-8")
          .withPath("/ESBLeumiDigitalBank/PAPI/v1.0/NTBD/2/105/01.01")
        //.withBody("body")
      )
      .respond(
        response()
          .withStatusCode(401)
          .withHeaders(
            new Header("Content-Type", "application/json; charset=utf-8")
          )
          .withBody(jsonToString("ntbd2_105_result.json"))
      )


    //14 NtbdAv050
    mockServer
      .when(
        request()
          .withMethod("POST")
          .withHeader("Content-Type","application/json;charset=utf-8")
          .withPath("/ESBLeumiDigitalBank/PAPI/v1.0/NTBD/A/050/01.03")
        //.withBody("body")
      )
      .respond(
        response()
          .withStatusCode(401)
          .withHeaders(
            new Header("Content-Type", "application/json; charset=utf-8")
          )
          .withBody(jsonToString("ntbdA_050_result.json"))
      )
    
    //15 NtbdBv050
    mockServer
      .when(
        request()
          .withMethod("POST")
          .withHeader("Content-Type","application/json;charset=utf-8")
          .withPath("/ESBLeumiDigitalBank/PAPI/v1.0/NTBD/B/050/01.03")
        //.withBody("body")
      )
      .respond(
        response()
          .withStatusCode(401)
          .withHeaders(
            new Header("Content-Type", "application/json; charset=utf-8")
          )
          .withBody(jsonToString("ntbdB_050_result.json"))
      )
    
    //16 NtbdIv050
    mockServer
      .when(
        request()
          .withMethod("POST")
          .withHeader("Content-Type","application/json;charset=utf-8")
          .withPath("/ESBLeumiDigitalBank/PAPI/v1.0/NTBD/I/050/01.03")
        //.withBody("body")
      )
      .respond(
        response()
          .withStatusCode(401)
          .withHeaders(
            new Header("Content-Type", "application/json; charset=utf-8")
          )
          .withBody(jsonToString("ntbdI_050_result.json"))
      )
    
    //17 NtbdGv050
    mockServer
      .when(
        request()
          .withMethod("POST")
          .withHeader("Content-Type","application/json;charset=utf-8")
          .withPath("/ESBLeumiDigitalBank/PAPI/v1.0/NTBD/G/050/01.03")
        //.withBody("body")
      )
      .respond(
        response()
          .withStatusCode(401)
          .withHeaders(
            new Header("Content-Type", "application/json; charset=utf-8")
          )
          .withBody(jsonToString("ntbdG_050_result.json"))
      )

  }
  
  

}
