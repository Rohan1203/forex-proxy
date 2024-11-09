package forex

//import akka.actor.ActorSystem
import cats.effect.{Concurrent, Timer}
import forex.config.ApplicationConfig
import forex.http.health.HealthHttpRoutes
import forex.http.rates.RatesHttpRoutes
import forex.services._
import forex.programs._
import org.http4s._
import org.http4s.implicits._
import org.http4s.server.middleware.{AutoSlash, Timeout}
import org.http4s.server.Router
import org.slf4j.LoggerFactory

//import scala.concurrent.ExecutionContext

class Module[F[_]: Concurrent: Timer](config: ApplicationConfig) {
  private val logger = LoggerFactory.getLogger(getClass)
  private val ratesService: RatesService[F] = RatesServices.OneFrameInterpreter[F]
  private val ratesProgram: RatesProgram[F] = RatesProgram[F](ratesService)

  // Create instances of the routes
  private val healthHttpRoutes: HttpRoutes[F] = new HealthHttpRoutes[F]().routes
  private val ratesHttpRoutes: HttpRoutes[F] = new RatesHttpRoutes[F](ratesProgram).routes

  // Combine the health and rates routes using Router
  private val httpRoutes: HttpRoutes[F] = Router(
    "/health" -> healthHttpRoutes,
    "/rates"  -> ratesHttpRoutes
  )

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

  // Combine the routes and apply middleware
  val httpApp: HttpApp[F] = appMiddleware(routesMiddleware(httpRoutes).orNotFound)

  logger.debug("all middleware registered")
}
