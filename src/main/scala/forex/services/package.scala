package forex

import cats.effect.{Concurrent, Timer}
import dev.profunktor.redis4cats.RedisCommands
import forex.config.{OneFrameConfig, RedisConfig}
import org.http4s.client.Client

package object services {
  type RatesService[F[_]] = rates.RatesRepository[F]
  
  object RatesServices {
    def live[F[_]: Concurrent: Timer](
        client: Client[F],
        oneFrameConfig: OneFrameConfig,
        redisConfig: RedisConfig,
        redis: RedisCommands[F, String, String],
        circuitBreakerConfig: forex.config.CircuitBreakerConfig,
        batchConfig: forex.config.BatchConfig,
        coalescerConfig: forex.config.CoalescerConfig,
        cassandraRepo: Option[cassandra.CassandraRatesRepository[F]] = None,
        cassandraSession: Option[com.datastax.oss.driver.api.core.CqlSession] = None,
        rateLimiter: Option[ratelimiter.RateLimiter[F]] = None
    ): F[(RatesService[F], F[Unit], F[Unit], F[Unit])] = 
      rates.Interpreters.live[F](client, oneFrameConfig, redisConfig, redis, circuitBreakerConfig, batchConfig, coalescerConfig, cassandraRepo, cassandraSession, rateLimiter)
  }
}
