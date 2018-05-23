package com.tesobe.obp.cache

import com.tesobe.obp.Config
import com.typesafe.scalalogging.StrictLogging
import scalacache.{Flags, ScalaCache, sync}
import scalacache.memoization.{cacheKeyExclude, memoizeSync}
import scalacache.redis.RedisCache
import scalacache.serialization.Codec

import scala.concurrent.duration.Duration
import scala.language.postfixOps

object Redis extends StrictLogging with Config {
  
  val url = config.getString("guava.cache.url")
  val port = config.getInt("guava.cache.port")

  implicit val scalaCache = ScalaCache(RedisCache(url, port))
  implicit val flags = Flags(readsEnabled = true, writesEnabled = true)

  implicit def anyToByte[T](implicit m: Manifest[T]) = new Codec[T, Array[Byte]] {

    import com.twitter.chill.KryoInjection

    def serialize(value: T): Array[Byte] = {
      logger.debug("KryoInjection started")
      val bytes: Array[Byte] = KryoInjection(value)
      logger.debug("KryoInjection finished")
      bytes
    }

    def deserialize(data: Array[Byte]): T = {
      import scala.util.{Failure, Success}
      logger.debug("Kryo deserialize started")
      val tryDecode: scala.util.Try[Any] = KryoInjection.invert(data)
      tryDecode match {
        case Success(v) => {
          logger.debug("Kryo deserialize finished")
          v.asInstanceOf[T]
        }
        case Failure(e) =>
          logger.debug(s"Kryo deserialize error:$e")
          "NONE".asInstanceOf[T]
      }
    }
  }

  def memoizeSyncWithRedis[A](cacheKey: Option[String])(@cacheKeyExclude f: => A)(implicit @cacheKeyExclude m: Manifest[A], flags: Flags): A = {
    memoizeSync(f)
  }
  
  def syncCachingWithRedis[V, Repr](keyParts: String*)(ttl: Duration)(f: => V)(implicit m: Manifest[V], flags: Flags): V = {
    sync.cachingWithTTL(keyParts)(ttl)(f)
  }
  
  def syncGetWithRedis[V](keyParts: String*)(implicit m: Manifest[V], flags: Flags): Option[V] = {
    sync.get(keyParts)
  }

}
