package com.tesobe.obp

import org.mockserver.integration.ClientAndServer.startClientAndServer
import org.mockserver.model.{Cookie, Header, Parameter}
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response
import com.tesobe.obp.JoniMf.getJoniMf



package object RunMockServer {
  val mockServer = startClientAndServer(1080)
  def startMockServer = {
    //val mockServer = startClientAndServer(1080)
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
            new Header("Content-Type", "application/json; charset=utf-8"),
          )
          .withBody(getJoniMf("joni_result.json"))
      )

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
            new Header("Content-Type", "application/json; charset=utf-8"),
          )
          .withBody(getJoniMf("nt1c_B_result.json"))
      )


  }
  
  

}
