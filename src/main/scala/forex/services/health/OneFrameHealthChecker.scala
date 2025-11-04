package forex.services.health

import cats.effect.{Concurrent, Timer}
import cats.effect.concurrent.Ref
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.applicativeError._
import org.http4s.{Method, Request, Uri}
import org.http4s.client.Client
import org.slf4j.LoggerFactory
import scala.concurrent.duration._

sealed trait HealthStatus
object HealthStatus {
  case object Healthy extends HealthStatus
  case object Unhealthy extends HealthStatus
}
/**
  * Monitors API health and triggers recovery actions
  *
  * @param httpClient
  * @param oneFrameUri
  * @param healthCheckInterval
  * @param statusRef
  * @param onRecovery
  */
class OneFrameHealthChecker[F[_]: Concurrent: Timer](
    httpClient: Client[F],
    oneFrameUri: String,
    healthCheckInterval: FiniteDuration,
    statusRef: Ref[F, HealthStatus],
    onRecovery: F[Unit]
) {

  private val logger = LoggerFactory.getLogger(getClass)

  // Start health check loop
  def start: F[Unit] = {
    logger.info(s"starting oneframe health checker (interval: ${healthCheckInterval.toSeconds}s)")
    scheduleNextCheck
  }

  // Schedule next health check after interval
  private def scheduleNextCheck: F[Unit] = {
    Timer[F].sleep(healthCheckInterval).flatMap(_ =>
      checkHealth.flatMap(_ => scheduleNextCheck)
    )
  }

  // Check if API is reachable
  private def checkHealth: F[Unit] = {
    val healthCheck: F[(HealthStatus, Option[Int])] = Uri.fromString(oneFrameUri).map { uri =>
      val request = Request[F](method = Method.GET, uri = uri)
      httpClient.status(request).map { status =>
        (HealthStatus.Healthy: HealthStatus, Option(status.code))
      }
    }.getOrElse(Concurrent[F].pure((HealthStatus.Unhealthy: HealthStatus, Option.empty[Int])))

    healthCheck.flatMap { case (newStatus, statusCode) =>
      statusRef.get.flatMap { oldStatus =>
        if (oldStatus == HealthStatus.Unhealthy && newStatus == HealthStatus.Healthy) {
          statusRef.set(newStatus).flatMap { _ =>
            Concurrent[F].delay(
              logger.info(s"oneframe api recovered (status: ${statusCode.getOrElse("unknown")}), triggering immediate batch fetch")
            ).flatMap(_ => 
              onRecovery.handleErrorWith { error =>
                Concurrent[F].delay(
                  logger.error(s"failed to trigger recovery batch: ${error.getMessage}")
                )
              }
            )
          }
        } else if (oldStatus == HealthStatus.Healthy && newStatus == HealthStatus.Unhealthy) {
          statusRef.set(newStatus).flatMap { _ =>
            Concurrent[F].delay(
              logger.warn("oneframe api is unhealthy")
            )
          }
        } else {
          statusRef.set(newStatus).flatMap { _ =>
            Concurrent[F].delay(
              logger.info(s"oneframe api status: ${newStatus.toString.toLowerCase} (status: ${statusCode.getOrElse("unknown")})")
            )
          }
        }
      }
    }.handleErrorWith { error =>
      statusRef.get.flatMap { oldStatus =>
        if (oldStatus == HealthStatus.Healthy) {
          statusRef.set(HealthStatus.Unhealthy).flatMap { _ =>
            Concurrent[F].delay(
              logger.error(s"oneframe health check failed: ${error.getMessage}")
            )
          }
        } else {
          Concurrent[F].delay(
            logger.warn(s"oneframe still unhealthy: ${error.getMessage}")
          )
        }
      }
    }
  }

  // Get current health status
  def getCurrentStatus: F[HealthStatus] = statusRef.get
}

object OneFrameHealthChecker {
  // Create new health checker instance
  def create[F[_]: Concurrent: Timer](
      httpClient: Client[F],
      oneFrameUri: String,
      healthCheckInterval: FiniteDuration,
      onRecovery: F[Unit]
  ): F[OneFrameHealthChecker[F]] = {
    Ref.of[F, HealthStatus](HealthStatus.Healthy).map { ref =>
      new OneFrameHealthChecker[F](httpClient, oneFrameUri, healthCheckInterval, ref, onRecovery)
    }
  }
}
