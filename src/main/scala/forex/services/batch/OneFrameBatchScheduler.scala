package forex.services.batch

import cats.effect.{Concurrent, Timer}
import cats.syntax.applicativeError._
import cats.syntax.flatMap._
import org.slf4j.LoggerFactory
import scala.concurrent.duration._

/**
  * Periodically fetches and caches exchange rates
  *
  * @param fetchAndCache
  * @param interval
  */
class OneFrameBatchScheduler[F[_]: Concurrent: Timer](
    fetchAndCache: F[Unit],
    interval: FiniteDuration
) {

  private val logger = LoggerFactory.getLogger(getClass)

  // Start scheduling periodic rate fetches
  def start: F[Unit] = {
    logger.info("starting oneframe batch scheduler")
    // Initial fetch on startup, then schedule periodic refreshes
    fetchAndCache.flatMap(_ => scheduleNextBatch)
  }

  // Schedule the next batch fetch
  private def scheduleNextBatch: F[Unit] =
    Timer[F].sleep(interval).flatMap(_ =>
      Concurrent[F].delay(
        logger.info(s"running scheduled batch fetch (interval: ${interval.toMinutes} minutes)")
      ).flatMap(_ =>
        fetchAndCache
          .handleErrorWith { error =>
            Concurrent[F].delay(
              logger.error(s"oneframe batch fetch failed: ${error.getMessage}", error)
            )
          }
          .flatMap(_ => scheduleNextBatch)
      )
    )
}
