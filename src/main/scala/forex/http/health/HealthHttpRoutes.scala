package forex.http.health

import cats.effect.Sync
import forex.health.{CurrentTimestamp, Health, Server, Version}
import io.circe.syntax.EncoderOps
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.circe._
import org.slf4j.LoggerFactory
import java.time.OffsetDateTime

/**
 *
 * Endpoint for healthcheck
 */
class HealthHttpRoutes[F[_]: Sync] extends Http4sDsl[F] {
  private val logger = LoggerFactory.getLogger(getClass)
  private val healthRoutes: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root =>
      val server = Server("ok")
      val timestamp = CurrentTimestamp(OffsetDateTime.now)
      val version = Version("1.0.0")
      val healthStatus = Health(server, timestamp, version)
      Ok(healthStatus.asJson)
  }
  logger.info("/health:ok")

  // Expose routes directly
  val routes: HttpRoutes[F] = healthRoutes
}
