package forex.http
package health

import cats.effect.{Clock, Sync}
import cats.syntax.flatMap._
import cats.syntax.functor._
import forex.config.ApplicationConfig
import io.circe.generic.auto._
import io.circe.syntax._
import org.http4s.HttpRoutes
import org.http4s.circe._
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router
case class HealthResponse(
    status: String,
    version: String,
    name: String,
    timestamp: Long
)

class HealthHttpRoutes[F[_]: Sync: Clock](config: ApplicationConfig) extends Http4sDsl[F] {

  private[http] val prefixPath = "/health"

  private val httpRoutes: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root =>
      for {
        timestamp <- Clock[F].realTime(java.util.concurrent.TimeUnit.MILLISECONDS)
        response <- Ok(
          HealthResponse(
            status = "UP",
            version = config.version,
            name = config.name,
            timestamp = timestamp
          ).asJson
        )
      } yield response
  }

  val routes: HttpRoutes[F] = Router(
    prefixPath -> httpRoutes
  )

}
