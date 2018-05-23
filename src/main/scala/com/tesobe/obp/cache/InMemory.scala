package com.tesobe.obp.cache

import com.google.common.cache.CacheBuilder
import scalacache.{Flags, ScalaCache, sync}
import scalacache.guava.GuavaCache
import scalacache.memoization.{cacheKeyExclude, memoizeSync}

import scala.concurrent.duration.Duration
import scala.language.postfixOps

object InMemory {

  val underlyingGuavaCache = CacheBuilder.newBuilder().maximumSize(10000L).build[String, Object]
  implicit val scalaCache  = ScalaCache(GuavaCache(underlyingGuavaCache))

  def memoizeSyncWithInMemory[A](cacheKey: Option[String])(@cacheKeyExclude f: => A)(implicit flags: Flags): A = {
    memoizeSync(f)
  }

  def syncCachingWithInMemory[V, Repr](keyParts: String*)(ttl: Duration)(f: => V)(implicit m: Manifest[V], flags: Flags): V = {
    sync.cachingWithTTL(keyParts)(ttl)(f)
  }
  
  def syncGetWithInMemory[V](keyParts: String*)(implicit m: Manifest[V], flags: Flags): Option[V] = {
    sync.get(keyParts)
  }
  
}
