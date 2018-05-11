package com.tesobe.obp.cache

import com.tesobe.obp.Config
import scalacache.Flags

import scala.concurrent.duration.Duration
import scala.language.postfixOps

object Caching extends Config{

  def memoizeSyncWithProvider[A](unique: Option[String])(f: => A)(implicit m: Manifest[A], flags: Flags): A = {
    (unique) match {
      case (t) if t == Duration.Zero  => // Just forwarding a call
        f
      case ( _) => // Caching a call
        config.getString("guava.cache.type") match {
          case value if value.toLowerCase == "redis" =>
            Redis.memoizeSyncWithRedis(unique)(f)
          case value if value.toLowerCase == "in-memory" =>
            InMemory.memoizeSyncWithInMemory(unique)(f)
          case _ =>
            InMemory.memoizeSyncWithInMemory(unique)(f)
        }
      case _  => // Just forwarding a call
        f
    }

  }

}
