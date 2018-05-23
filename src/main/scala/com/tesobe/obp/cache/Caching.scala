package com.tesobe.obp.cache

import com.tesobe.obp.Config
import scalacache.Flags

import scala.concurrent.duration.{Duration, FiniteDuration}
import scala.language.postfixOps

object Caching extends Config{
  
  private val zero: FiniteDuration = Duration.Zero
  private val GUAVA_CACHE_TYPE: String = config.getString("guava.cache.type")
  def memoizeSyncWithProvider[A](cacheKey: Option[String])(f: => A)(implicit m: Manifest[A], flags: Flags): A = {
    (cacheKey) match {
      case Some(_) => // Caching a call
        GUAVA_CACHE_TYPE match {
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
  
  def syncCachingWithProvider[A](cacheKey: String)(ttl: Duration)(f: => A)(implicit m: Manifest[A]): A = {
    GUAVA_CACHE_TYPE match {
      case value if value.toLowerCase == "redis" =>
        Redis.syncCachingWithRedis(cacheKey)(ttl)(f)
      case value if value.toLowerCase == "in-memory" =>
        InMemory.syncCachingWithInMemory(cacheKey)(ttl)(f)
      case _ =>
        InMemory.syncCachingWithInMemory(cacheKey)(ttl)(f)
    }
  }
  
  def syncGetWithProvider[A](cacheKey: String)(implicit m: Manifest[A]): Option[A] = {
    GUAVA_CACHE_TYPE match {
      case value if value.toLowerCase == "redis" =>
        Redis.syncGetWithRedis(cacheKey)
      case value if value.toLowerCase == "in-memory" =>
        InMemory.syncGetWithInMemory(cacheKey)
      case _ =>
        InMemory.syncGetWithInMemory(cacheKey)
    }
  }

}
