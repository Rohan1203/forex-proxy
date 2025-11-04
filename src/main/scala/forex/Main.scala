package forex

import scala.concurrent.ExecutionContext
import cats.effect._
import forex.config._
import fs2.Stream
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.blaze.client.BlazeClientBuilder
import org.slf4j.LoggerFactory

// Application entry point - initializes and runs the forex proxy server
object Main extends IOApp {

  private val logger = LoggerFactory.getLogger(getClass)

  // Start application and handle lifecycle
  override def run(args: List[String]): IO[ExitCode] = {
    logger.info("starting forex proxy application")
    new Application[IO].stream(executionContext)
      .compile
      .drain
      .as(ExitCode.Success)
      .handleErrorWith { error =>
        IO(logger.error("application failed to start", error)) *>
          IO.pure(ExitCode.Error)
      }
  }

}

// Manages application lifecycle and initializes all services
class Application[F[_]: ConcurrentEffect: Timer: cats.effect.ContextShift] {

  private val logger = LoggerFactory.getLogger(getClass)

  // Build and run complete application stream
  def stream(ec: ExecutionContext): Stream[F, Unit] =
    for {
      // Load and log configuration
      _ <- Stream.eval(Sync[F].delay(logger.info("loading configuration")))
      config <- Config.stream("app")
      _ <- Stream.eval(Sync[F].delay(logger.info(s"configuration loaded: ${config.name} v${config.version}")))
      
      // Create HTTP client for OneFrame API
      _ <- Stream.eval(Sync[F].delay(logger.info("creating http client for oneframe api")))
      httpClient <- BlazeClientBuilder[F](ec)
        .withRequestTimeout(config.oneframe.timeout)
        .stream
      
      // Connect to Redis for caching
      _ <- Stream.eval(Sync[F].delay(logger.info(s"connecting to redis at ${config.redis.host}:${config.redis.port}")))
      redis <- Stream.resource(createRedisClient(config))
      _ <- Stream.eval(Sync[F].delay(logger.info("redis connection established")))
      
      // Connect to Cassandra for historical data
      _ <- Stream.eval(Sync[F].delay(logger.info("connecting to cassandra...")))
      cassandraSession <- Stream.resource(createCassandraSession(config))
      _ <- Stream.eval(Sync[F].delay(logger.info("cassandra connection established")))
      
      // Initialize application module with all dependencies
      _ <- Stream.eval(Sync[F].delay(logger.info("initializing application module")))
      result <- Stream.eval(Module.create[F](config, httpClient, redis, Some(cassandraSession)))
      (module, batchScheduler, healthChecker) = result
      
      // Start batch scheduler if enabled (periodic cache warm-up)
      _ <- Stream.eval(Sync[F].delay(if (config.batch.enabled) logger.info("starting oneframe batch scheduler") else logger.info("batch scheduler disabled in configuration")))
      _ <- Stream.eval(Concurrent[F].start(batchScheduler))
      _ <- Stream.eval(Sync[F].delay(if (config.batch.enabled) logger.info("oneframe batch scheduler started in background") else ()))
      
      // Start health checker (monitors API and triggers recovery)
      _ <- Stream.eval(Sync[F].delay(logger.info("starting oneframe health checker")))
      _ <- Stream.eval(Concurrent[F].start(healthChecker))
      _ <- Stream.eval(Sync[F].delay(logger.info("oneframe health checker started in background")))
      
      // Start HTTP server
      _ <- Stream.eval(Sync[F].delay(logger.info(s"starting http server on http://${config.http.host}:${config.http.port}")))
      _ <- BlazeServerBuilder[F](ec)
            .bindHttp(config.http.port, config.http.host)
            .withHttpApp(module.httpApp)
            .serve
    } yield ()

  // Create new redis client
  private def createRedisClient(appConfig: ApplicationConfig): cats.effect.Resource[F, dev.profunktor.redis4cats.RedisCommands[F, String, String]] = {
    import dev.profunktor.redis4cats._
    import dev.profunktor.redis4cats.effect.Log
    
    implicit val logger: Log[F] = new Log[F] {
      def debug(msg: => String): F[Unit] = Sync[F].delay(())
      def info(msg: => String): F[Unit] = Sync[F].delay(())
      def error(msg: => String): F[Unit] = Sync[F].delay(())
    }
    
    val uri = s"redis://${appConfig.redis.host}:${appConfig.redis.port}"
    Redis[F].utf8(uri)
  }

  // Create new cassandra session
  private def createCassandraSession(appConfig: ApplicationConfig): cats.effect.Resource[F, com.datastax.oss.driver.api.core.CqlSession] = {
    forex.services.cassandra.CassandraRatesRepository.createSession[F](appConfig.cassandra)
  }

}
