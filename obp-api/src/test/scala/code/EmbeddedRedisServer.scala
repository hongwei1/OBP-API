package code

import code.util.Helper
import redis.embedded.RedisServer


object EmbeddedRedisServer {
  val availablePort = Helper.findAvailablePort()
  val redisServer: RedisServer = RedisServer.builder.port(availablePort).build
  redisServer.start()
}