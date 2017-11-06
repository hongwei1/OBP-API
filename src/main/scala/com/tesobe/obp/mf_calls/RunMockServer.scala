package com.tesobe.obp

import com.typesafe.scalalogging.StrictLogging
import org.mockserver.integration.ClientAndServer.startClientAndServer
import org.mockserver.model.{Body, Header, JsonBody}
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import net.liftweb.json.JsonParser.parse
import org.mockserver.model.Body.Type
import org.mockserver.matchers.MatchType
import org.mockserver.model
import com.google.common.net.MediaType.JSON_UTF_8

import scala.util.parsing.json.JSON



object RunMockServer extends StrictLogging{
  val mockServer = startClientAndServer(1080)
  
  def jsonToString(filename: String): String = {
    val source = scala.io.Source.fromResource(filename)
    val lines = try source.mkString finally source.close()
    lines
  }
  
  def startMockServer = {
    //1a JONI for "N7jut8d"
    mockServer
      .when(
        request()
          .withMethod("POST")
          .withHeader("Content-Type","application/json;charset=utf-8")
          .withPath("/ESBLeumiDigitalBank/PAPI/V1.0/JONI/0/000/01.01")
          .withBody(jsonToString("joni_request.json").replace(" ","").replace("\n",""))
      )
      .respond(
        response()
          .withStatusCode(401)
          .withHeaders(
            new Header("Content-Type", "application/json; charset=utf-8")
          )
          .withBody(jsonToString("joni_result.json"))
          //.withBody(jsonToString("error_result.json"))
      )

    //1b JONI for "4CWWTZQ"
   mockServer
      .when(
        request()
          .withMethod("POST")
          .withHeader("Content-Type","application/json;charset=utf-8")
          .withPath("/ESBLeumiDigitalBank/PAPI/V1.0/JONI/0/000/01.01")
          .withBody(jsonToString("joni_4cwwtzq_request.json").replace(" ","").replace("\n",""))
      )
      .respond(
        response()
          .withStatusCode(401)
          .withHeaders(
            new Header("Content-Type", "application/json; charset=utf-8")
          )
          .withBody(jsonToString("joni_4cwwtzq_result.json").replace(" ","").replace("\n",""))
        //.withBody(jsonToString("error_result.json"))
      )

    //1c JONI for "FM4FZDE"
    mockServer
      .when(
        request()
          .withMethod("POST")
          .withHeader("Content-Type","application/json;charset=utf-8")
          .withPath("/ESBLeumiDigitalBank/PAPI/V1.0/JONI/0/000/01.01")
          .withBody(jsonToString("joni_fm4dzde_request.json").replace(" ","").replace("\n",""))
      )
      .respond(
        response()
          .withStatusCode(401)
          .withHeaders(
            new Header("Content-Type", "application/json; charset=utf-8")
          )
          .withBody(jsonToString("joni_fm4dzde_result.json").replace(" ","").replace("\n",""))
        //.withBody(jsonToString("error_result.json"))
      )

    //1d JONI for "P747XTF"
    mockServer
      .when(
        request()
          .withMethod("POST")
          .withHeader("Content-Type","application/json;charset=utf-8")
          .withPath("/ESBLeumiDigitalBank/PAPI/V1.0/JONI/0/000/01.01")
          .withBody(jsonToString("joni_p747xtf_request.json").replace(" ","").replace("\n",""))
      )
      .respond(
        response()
          .withStatusCode(401)
          .withHeaders(
            new Header("Content-Type", "application/json; charset=utf-8")
          )
          .withBody(jsonToString("joni_p747xtf_result.json"))
        //.withBody(jsonToString("error_result.json"))
      )
    //2 Nt1cB
    mockServer
      .when(
        request()
          .withMethod("POST")
          .withHeader("Content-Type","application/json;charset=utf-8")
          .withPath("/ESBLeumiDigitalBank/PAPI/v1.0/NT1C/B/000/01.02")
          //.withBody("", MatchType.ONLY_MATCHING_FIELDS)
          //.withBody(new Body(JSON,JSON_UTF_8),MatchType.ONLY_MATCHING_FIELDS)
          //.withBody(new JsonBody("{User: 'N7jut8d'}", MatchType.ONLY_MATCHING_FIELDS)
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
    
    //18 Ntvd2v050
    mockServer
      .when(
        request()
          .withMethod("POST")
          .withHeader("Content-Type","application/json;charset=utf-8")
          .withPath("/ESBLeumiDigitalBank/PAPI/v1.0/NTBD/2/050/01.01")
        //.withBody("body")
      )
      .respond(
        response()
          .withStatusCode(401)
          .withHeaders(
            new Header("Content-Type", "application/json; charset=utf-8")
          )
          .withBody(jsonToString("ntbd2_050_result.json"))
      )

    //19 Ntg6A
    mockServer
      .when(
        request()
          .withMethod("POST")
          .withHeader("Content-Type","application/json;charset=utf-8")
          .withPath("/ESBLeumiDigitalBank/PAPI/v1.0/NTG6/A/000/01.02")
        //.withBody("body")
      )
      .respond(
        response()
          .withStatusCode(401)
          .withHeaders(
            new Header("Content-Type", "application/json; charset=utf-8")
          )
          .withBody(jsonToString("ntg6_A_result.json"))
          //.withBody(jsonToString("error_result.json"))
      )
    //20 Ntg6B
    mockServer
      .when(
        request()
          .withMethod("POST")
          .withHeader("Content-Type","application/json;charset=utf-8")
          .withPath("/ESBLeumiDigitalBank/PAPI/v1.0/NTG6/B/000/01.02")
        //.withBody("body")
      )
      .respond(
        response()
          .withStatusCode(401)
          .withHeaders(
            new Header("Content-Type", "application/json; charset=utf-8")
          )
          .withBody(jsonToString("ntg6_B_result.json"))
      )
    
    //21 Ntg6C
    mockServer
      .when(
        request()
          .withMethod("POST")
          .withHeader("Content-Type","application/json;charset=utf-8")
          .withPath("/ESBLeumiDigitalBank/PAPI/v1.0/NTG6/C/000/01.02")
        //.withBody("body")
      )
      .respond(
        response()
          .withStatusCode(401)
          .withHeaders(
            new Header("Content-Type", "application/json; charset=utf-8")
          )
          .withBody(jsonToString("ntg6_C_result.json"))
      )
    //22 Ntg6D
    mockServer
      .when(
        request()
          .withMethod("POST")
          .withHeader("Content-Type","application/json;charset=utf-8")
          .withPath("/ESBLeumiDigitalBank/PAPI/v1.0/NTG6/D/000/01.02")
        //.withBody("body")
      )
      .respond(
        response()
          .withStatusCode(401)
          .withHeaders(
            new Header("Content-Type", "application/json; charset=utf-8")
          )
          .withBody(jsonToString("ntg6_D_result.json"))
      )
    
    //23 Ntg6I
    mockServer
      .when(
        request()
          .withMethod("POST")
          .withHeader("Content-Type","application/json;charset=utf-8")
          .withPath("/ESBLeumiDigitalBank/PAPI/v1.0/NTG6/I/000/01.04")
        //.withBody("body")
      )
      .respond(
        response()
          .withStatusCode(401)
          .withHeaders(
            new Header("Content-Type", "application/json; charset=utf-8")
          )
          .withBody(jsonToString("ntg6_I_result.json"))
      )
    
    //24 Ntg6K

    mockServer
      .when(
        request()
          .withMethod("POST")
          .withHeader("Content-Type","application/json;charset=utf-8")
          .withPath("/ESBLeumiDigitalBank/PAPI/v1.0/NTG6/K/000/01.04")
        //.withBody("body")
      )
      .respond(
        response()
          .withStatusCode(401)
          .withHeaders(
            new Header("Content-Type", "application/json; charset=utf-8")
          )
          .withBody(jsonToString("ntg6_K_result.json"))
      )

  }
  
  

}
