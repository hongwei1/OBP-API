package com.tesobe.obp.cache

import com.tesobe.obp.Config
import scalacache.Flags

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.language.postfixOps

object Caching extends Config{
  
  private val zero: FiniteDuration = Duration.Zero
  def memoizeSyncWithProvider[A](cacheKey: Option[String])(f: => A)(implicit m: Manifest[A], flags: Flags): A = {
    (cacheKey) match {
      case Some(_) => // Caching a call
        config.getString("guava.cache.type") match {
          case value if value.toLowerCase == "redis" =>
            Redis.memoizeSyncWithRedis(cacheKey)(f)
          case value if value.toLowerCase == "in-memory" =>
            InMemory.memoizeSyncWithInMemory(cacheKey)(f)
          case _ =>
            InMemory.memoizeSyncWithInMemory(cacheKey)(f)
        }
      case _  => // Just forwarding a call, no caching .
        f
    }

  }

}
