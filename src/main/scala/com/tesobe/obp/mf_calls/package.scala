package com.tesobe.obp

import org.mockserver.integration.ClientAndServer.startClientAndServer
import org.mockserver.model.{Cookie, Header, Parameter}
import org.mockserver.model.HttpRequest.request
import org.mockserver.model.HttpResponse.response



package object RunMockServer {
  
  def startMockServer = {
    val mockServer = startClientAndServer(1080)
    mockServer
      .when(
        request()
          .withMethod("POST")
          .withPath("/login")
          .withBody("{username: 'foo', password: 'bar'}")
      )
      .respond(
        response()
          .withStatusCode(401)
          .withHeaders(
            new Header("Content-Type", "application/json; charset=utf-8"),
            new Header("Cache-Control", "public, max-age=86400")
          )
          .withBody("{ message: 'incorrect username and password combination' }")
      )
  }
  
  

}
