package forex.services.ratelimiter

import cats.effect.Concurrent
import cats.syntax.flatMap._
import cats.syntax.functor._
import dev.profunktor.redis4cats.RedisCommands
import forex.config.RateLimiterConfig
import org.slf4j.LoggerFactory

/**
  * Redis-based rate limiter using sliding window counter
  *
  * @param redis
  * @param config
  */
class RateLimiter[F[_]: Concurrent](
    redis: RedisCommands[F, String, String],
    config: RateLimiterConfig
) {

  private val logger = LoggerFactory.getLogger(getClass)

  /**
   * Check if the client is allowed to make a request
   * Returns true if within rate limit, false if exceeded
   */
  def isAllowed(clientId: String): F[Boolean] = {
    if (!config.enabled) {
      Concurrent[F].pure(true)
    } else if (config.whitelist.contains(clientId)) {
      // Client is whitelisted, bypass rate limiting
      Concurrent[F].delay(
        logger.debug(s"rate limit bypassed for whitelisted client: $clientId")
      ).map(_ => true)
    } else {
      val key = s"ratelimit:$clientId"

      // Get current count and increment
      redis.get(key).flatMap {
        case Some(countStr) =>
          val count = countStr.toInt
          if (count >= config.maxRequests) {
            Concurrent[F].delay(
              logger.warn(s"rate limit exceeded for client: $clientId ($count/${config.maxRequests})")
            ).map(_ => false)
          } else {
            // Increment count
            redis.set(key, (count + 1).toString).flatMap { _ =>
              redis.expire(key, config.window).map { _ =>
                logger.debug(s"rate limit check: $clientId (${count + 1}/${config.maxRequests})")
                true
              }
            }
          }
        case None =>
          // First request in window
          redis.set(key, "1").flatMap { _ =>
            redis.expire(key, config.window).map { _ =>
              logger.debug(s"rate limit check: $clientId (1/${config.maxRequests})")
              true
            }
          }
      }
    }
  }

  /**
   * Get current request count for a client
   */
  def getCount(clientId: String): F[Int] = {
    val key = s"ratelimit:$clientId"
    redis.get(key).map {
      case Some(countStr) => countStr.toInt
      case None => 0
    }
  }

  /**
   * Reset rate limit for a client (admin operation)
   */
  def reset(clientId: String): F[Unit] = {
    val key = s"ratelimit:$clientId"
    redis.del(key).void
  }
}

object RateLimiter {
  def create[F[_]: Concurrent](
      redis: RedisCommands[F, String, String],
      config: RateLimiterConfig
  ): F[RateLimiter[F]] = {
    Concurrent[F].pure(new RateLimiter[F](redis, config))
  }
}
