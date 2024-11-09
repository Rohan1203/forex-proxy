package forex

import akka.actor.ActorSystem
import scala.concurrent.ExecutionContext
import cats.effect._
import forex.config._
import forex.services.rates.interpreters._
import fs2.Stream
import org.http4s.blaze.server.BlazeServerBuilder
import org.slf4j.LoggerFactory

object Main extends IOApp {

  // Initialize ActorSystem and ExecutionContext
  implicit val system: ActorSystem = ActorSystem("forex-system")  // ActorSystem for Akka scheduling
  implicit val ec: ExecutionContext = system.dispatcher           // ExecutionContext for async operations

  private val logger = LoggerFactory.getLogger(getClass)
  logger.info("!!! Application started !!!")

  // Create an instance of CacheStore (implicit ActorSystem is required)
  private val cacheStore = new CacheStore()

  // init cachestore
  cacheStore.initCache()
  // Start the cleanup scheduler immediately after CacheStore is created
  cacheStore.startCleanupScheduler()

  override def run(args: List[String]): IO[ExitCode] =
    new Application[IO].stream(ec).compile.drain.as(ExitCode.Success)
}

class Application[F[_]: ConcurrentEffect: Timer] {

  def stream(ec: ExecutionContext): Stream[F, Unit] = {
    for {
      config <- Config.stream("app")
      module = new Module[F](config) // loading config
      _ <- BlazeServerBuilder[F](ec)
        .bindHttp(config.http.port, config.http.host) // getting the config from the file
        .withHttpApp(module.httpApp) // registering the route
        .serve
    } yield ()
  }
}
