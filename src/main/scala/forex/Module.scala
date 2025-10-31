package forex

import cats.effect.{ Clock, Concurrent, Timer }
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
class Module[F[_]: Concurrent: Timer: Clock](config: ApplicationConfig)(httpClient: Client[F]) {

  private val ratesService: RatesService[F] = RatesServices.live[F](httpClient, config.oneframe)

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
