package forex

import cats.effect.{ Clock, Concurrent, Timer }
import cats.syntax.flatMap._
import cats.syntax.functor._
import forex.config.ApplicationConfig
import forex.http.rates.RatesHttpRoutes
import forex.http.health.HealthHttpRoutes
import forex.services._
import forex.programs._
import org.http4s._
import org.http4s.client.Client
import org.http4s.implicits._
import org.http4s.server.Router
import org.http4s.server.middleware.{ AutoSlash, Timeout }

class Module[F[_]: Concurrent: Timer: Clock](
    config: ApplicationConfig,
    ratesService: RatesService[F]
) {

  private val ratesProgram: RatesProgram[F] = RatesProgram[F](ratesService)

  private val ratesHttpRoutes: HttpRoutes[F] = new RatesHttpRoutes[F](ratesProgram).routes
  private val healthHttpRoutes: HttpRoutes[F] = new HealthHttpRoutes[F](config).routes

  type PartialMiddleware = HttpRoutes[F] => HttpRoutes[F]
  type TotalMiddleware   = HttpApp[F] => HttpApp[F]

  private val routesMiddleware: PartialMiddleware = {
    { http: HttpRoutes[F] =>
      AutoSlash(http)
    }
  }

  private val appMiddleware: TotalMiddleware = { http: HttpApp[F] =>
    Timeout(config.http.timeout)(http)
  }

  private val http: HttpRoutes[F] = Router(
    "/" -> healthHttpRoutes,
    "/" -> ratesHttpRoutes
  )

  val httpApp: HttpApp[F] = appMiddleware(routesMiddleware(http).orNotFound)

}

object Module {
  // Create application module with all dependencies
  def create[F[_]: Concurrent: Timer: Clock](
      config: ApplicationConfig,
      httpClient: Client[F],
      redis: dev.profunktor.redis4cats.RedisCommands[F, String, String],
      cassandraSession: Option[com.datastax.oss.driver.api.core.CqlSession] = None
  ): F[(Module[F], F[Unit], F[Unit])] = {
    // Create Cassandra repository if session provided
    val cassandraRepoF: F[Option[forex.services.cassandra.CassandraRatesRepository[F]]] = 
      cassandraSession match {
        case Some(session) => 
          forex.services.cassandra.CassandraRatesRepository.create[F](session).map(Some(_))
        case None => 
          Concurrent[F].pure(None)
      }

    for {
      cassandraRepo <- cassandraRepoF
      rateLimiter <- forex.services.ratelimiter.RateLimiter.create[F](redis, config.rateLimiter)
      result <- RatesServices.live[F](
        httpClient,
        config.oneframe,
        config.redis,
        redis,
        config.circuitBreaker,
        config.batch,
        config.coalescer,
        cassandraRepo,
        cassandraSession,
        Some(rateLimiter)
      )
      (ratesService, batchScheduler, _, fetchAndCacheFn) = result
      module = new Module[F](config, ratesService)
      // Only trigger batch fetch on recovery if batch is enabled
      recoveryCallback = if (config.batch.enabled) fetchAndCacheFn else Concurrent[F].unit
      healthChecker <- forex.services.rates.Interpreters.createHealthChecker[F](
        httpClient,
        config.oneframe.uri,
        onRecovery = recoveryCallback,
        healthCheckInterval = config.oneframe.healthCheckInterval
      )
    } yield (module, batchScheduler, healthChecker.start)
  }
}
