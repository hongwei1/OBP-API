package com.tesobe.obpatms

import com.sun.net.httpserver.HttpServer

import java.net.InetSocketAddress
import java.util.concurrent.Executors

/**
  * `/health/live`: this JVM is up (always 200 once bound — a liveness probe should restart the
  * pod only when the process itself is stuck, not when a dependency is briefly unreachable).
  * `/health/ready`: both the database and the RabbitMQ connection are actually usable — a
  * readiness probe failing here is exactly what should pull the pod out of the k3s Service before
  * traffic reaches a connector reply it cannot make.
  *
  * Deliberately the JDK's built-in HttpServer, not a framework dependency: this module's whole
  * point is a small, auditable dependency graph rooted at obp-commons.
  */
class HealthServer(port: Int, db: Database, mq: RabbitMqServer) {

  private val server = HttpServer.create(new InetSocketAddress(port), 0)
  server.setExecutor(Executors.newFixedThreadPool(2))

  server.createContext("/health/live", exchange => {
    val body = "ok".getBytes("UTF-8")
    exchange.sendResponseHeaders(200, body.length)
    val os = exchange.getResponseBody
    try os.write(body) finally os.close()
  })

  server.createContext("/health/ready", exchange => {
    val ready = db.isHealthy && mq.isHealthy
    val status = if (ready) 200 else 503
    val body = (if (ready) "ready" else "not ready").getBytes("UTF-8")
    exchange.sendResponseHeaders(status, body.length)
    val os = exchange.getResponseBody
    try os.write(body) finally os.close()
  })

  def start(): Unit = server.start()
  def stop(): Unit = server.stop(1)
}
