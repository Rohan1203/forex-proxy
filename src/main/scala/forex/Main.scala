package forex

import scala.concurrent.ExecutionContext
import cats.effect._
import forex.config._
import fs2.Stream
import org.http4s.blaze.server.BlazeServerBuilder
import org.http4s.blaze.client.BlazeClientBuilder
import org.slf4j.LoggerFactory
object Main extends IOApp {

  private val logger = LoggerFactory.getLogger(getClass)

  override def run(args: List[String]): IO[ExitCode] = {
    logger.info("Starting Forex Proxy Application...")
    new Application[IO].stream(executionContext)
      .compile
      .drain
      .as(ExitCode.Success)
      .handleErrorWith { error =>
        IO(logger.error("Application failed to start", error)) *>
          IO.pure(ExitCode.Error)
      }
  }

}

class Application[F[_]: ConcurrentEffect: Timer] {

  private val logger = LoggerFactory.getLogger(getClass)

  def stream(ec: ExecutionContext): Stream[F, Unit] =
    for {
      _ <- Stream.eval(Sync[F].delay(logger.info("Loading configuration...")))
      config <- Config.stream("app")
      _ <- Stream.eval(Sync[F].delay(logger.info(s"Configuration loaded: ${config.name} v${config.version}")))
      _ <- Stream.eval(Sync[F].delay(logger.info("Creating HTTP client for OneFrame API...")))
      httpClient <- BlazeClientBuilder[F](ec)
        .withRequestTimeout(config.oneframe.timeout)
        .stream
      _ <- Stream.eval(Sync[F].delay(logger.info(s"Initializing HTTP server on ${config.http.host}:${config.http.port}")))
      module = new Module[F](config)(httpClient)
      _ <- Stream.eval(Sync[F].delay(logger.info(s"Starting HTTP server on http://${config.http.host}:${config.http.port}")))
      _ <- BlazeServerBuilder[F](ec)
            .bindHttp(config.http.port, config.http.host)
            .withHttpApp(module.httpApp)
            .serve
    } yield ()

}
