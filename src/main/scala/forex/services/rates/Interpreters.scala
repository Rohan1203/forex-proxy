package forex.services.rates

import cats.effect.{Concurrent, Timer}
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.datastax.oss.driver.api.core.CqlSession
import dev.profunktor.redis4cats.RedisCommands
import forex.config._
import forex.domain.Rate
import forex.services.apitracker.OneFrameApiTracker
import forex.services.batch.OneFrameBatchScheduler
import forex.services.cassandra.CassandraRatesRepository
import forex.services.circuitbreaker.{CircuitBreaker, CircuitBreakerConfig => CBConfig}
import forex.services.coalescer.RequestCoalescer
import forex.services.health.OneFrameHealthChecker
import org.http4s.client.Client
import interpreters._
import scala.concurrent.duration._

// factory/setup class that wires all application dependencies together

// Wires all rate service dependencies based on configuration
// Creates circuit breaker, coalescer, API tracker, batch scheduler, and health checker
object Interpreters {
  // Build and configure all rate service components
  def live[F[_]: Concurrent: Timer](
      client: Client[F],
      oneFrameConfig: OneFrameConfig,
      redisConfig: RedisConfig,
      redis: RedisCommands[F, String, String],
      circuitBreakerConfig: CircuitBreakerConfig,
      batchConfig: BatchConfig,
      coalescerConfig: CoalescerConfig,
      cassandraRepo: Option[CassandraRatesRepository[F]] = None,
      cassandraSession: Option[CqlSession] = None,
      rateLimiter: Option[forex.services.ratelimiter.RateLimiter[F]] = None
  ): F[(RatesRepository[F], F[Unit], F[Unit], F[Unit])] = {
    val cbConfig = CBConfig(
      maxFailures = circuitBreakerConfig.maxFailures,
      resetTimeout = circuitBreakerConfig.resetTimeout
    )
    for {
      // Create circuit breaker to prevent cascading failures
      circuitBreaker <- if (circuitBreakerConfig.enabled) {
        CircuitBreaker.create[F](cbConfig)
      } else {
        CircuitBreaker.disabled[F]
      }
      // Create coalescer to merge duplicate concurrent requests for same pair
      coalescer <- if (coalescerConfig.enabled) {
        RequestCoalescer.create[F, String, Rate]
      } else {
        RequestCoalescer.disabled[F, String, Rate]
      }
      // Create API tracker if Cassandra session available
      apiTracker <- cassandraSession match {
        case Some(session) => 
          OneFrameApiTracker.create[F](redis, session, dailyQuota = 1000).map(Some(_))
        case None => 
          Concurrent[F].pure(None)
      }
      // Assemble all dependencies into OneFrame client
      baseClient = new OneFrameClient[F](client, oneFrameConfig, redisConfig, redis, circuitBreaker, coalescer, cassandraRepo, apiTracker, rateLimiter)
      ratesClient = baseClient: RatesRepository[F]
      
      // Create batch scheduler with configurable interval
      batchScheduler = new OneFrameBatchScheduler[F](baseClient.fetchAndCache, batchConfig.interval)
      batchSchedulerStart = if (batchConfig.enabled) batchScheduler.start else Concurrent[F].unit
    } yield {
      // Return fetchAndCache function for health checker recovery
      (ratesClient, batchSchedulerStart, Concurrent[F].unit, baseClient.fetchAndCache)
    }
  }

  // Create health checker that monitors API availability and triggers recovery
  def createHealthChecker[F[_]: Concurrent: Timer](
      client: Client[F],
      oneFrameUri: String,
      onRecovery: F[Unit],
      healthCheckInterval: FiniteDuration = 30.seconds
  ): F[OneFrameHealthChecker[F]] = {
    OneFrameHealthChecker.create[F](client, oneFrameUri, healthCheckInterval, onRecovery)
  }
}
