package com.tesobe.obpatms

object Main {

  def main(args: Array[String]): Unit = {
    val config = AppConfig.fromEnv()

    val db = new Database(config.db)
    db.migrate()
    println(s"[obp-atms] schema ready at ${config.db.jdbcUrl}")

    val atmRepo = new AtmRepo(db)
    val branchRepo = new BranchRepo(db)
    val handler = new MessageHandler(atmRepo, branchRepo)

    val mq = new RabbitMqServer(config.rabbitMq, handler)
    mq.start()
    println(s"[obp-atms] consuming ${config.rabbitMq.requestQueue} on ${config.rabbitMq.host}:${config.rabbitMq.port}")

    val health = new HealthServer(config.healthPort, db, mq)
    health.start()
    println(s"[obp-atms] health server on :${config.healthPort}")

    sys.addShutdownHook {
      println("[obp-atms] shutting down")
      health.stop()
      mq.stop()
      db.close()
    }

    // The RabbitMQ client and the JDK HttpServer both run their own thread pools; keep the main
    // thread parked so the JVM does not exit the moment main() returns.
    val park = new Object
    park.synchronized { park.wait() }
  }
}
