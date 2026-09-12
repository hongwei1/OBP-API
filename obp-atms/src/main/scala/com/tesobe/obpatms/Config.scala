package com.tesobe.obpatms

/**
  * Every setting comes from an environment variable, not a props file: this process ships in a
  * container image, not on obp-api's classpath, so it has no APIUtil.getPropsValue to read from.
  * Names mirror the obp-api prop names one-for-one (rabbitmq_connector.host -> RABBITMQ_HOST, and
  * so on) so an operator who already knows the core's rabbitmq_connector.* props recognises these.
  */
case class RabbitMqConfig(
  host: String,
  port: Int,
  username: String,
  password: String,
  virtualHost: String,
  requestQueue: String,
  replyQueuePrefix: String
)

case class DbConfig(
  jdbcUrl: String,
  username: String,
  password: String,
  maxPoolSize: Int
)

case class AppConfig(
  rabbitMq: RabbitMqConfig,
  db: DbConfig,
  healthPort: Int
)

object AppConfig {

  private def env(name: String, default: String): String =
    sys.env.getOrElse(name, default)

  private def envRequired(name: String): String =
    sys.env.getOrElse(name, throw new IllegalStateException(s"mandatory environment variable $name is missing"))

  private def envInt(name: String, default: Int): Int =
    sys.env.get(name).map(_.toInt).getOrElse(default)

  def fromEnv(): AppConfig = AppConfig(
    rabbitMq = RabbitMqConfig(
      host = envRequired("RABBITMQ_HOST"),
      port = envInt("RABBITMQ_PORT", 5672),
      username = envRequired("RABBITMQ_USERNAME"),
      password = envRequired("RABBITMQ_PASSWORD"),
      virtualHost = env("RABBITMQ_VIRTUAL_HOST", "/"),
      // Must match the core's rabbitmq_connector.request_queue (default obp_rpc_queue) exactly:
      // both sides publish/consume the same named queue, there is no negotiation.
      requestQueue = env("RABBITMQ_REQUEST_QUEUE", "obp_rpc_queue"),
      replyQueuePrefix = env("RABBITMQ_REPLY_QUEUE_PREFIX", "obp_reply_queue")
    ),
    db = DbConfig(
      jdbcUrl = envRequired("ATMS_DB_JDBC_URL"),
      username = envRequired("ATMS_DB_USERNAME"),
      password = envRequired("ATMS_DB_PASSWORD"),
      maxPoolSize = envInt("ATMS_DB_MAX_POOL_SIZE", 10)
    ),
    healthPort = envInt("HEALTH_PORT", 8081)
  )
}
