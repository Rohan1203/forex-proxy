package forex.services.apitracker

import cats.effect.Concurrent
import cats.syntax.applicativeError._
import cats.syntax.flatMap._
import cats.syntax.functor._
import com.datastax.oss.driver.api.core.CqlSession
import dev.profunktor.redis4cats.RedisCommands
import forex.logging.DbLogger
import org.slf4j.LoggerFactory
import java.time.{LocalDate, ZoneOffset}
import java.time.format.DateTimeFormatter
import scala.concurrent.duration._

/**
 * Tracks OneFrame API calls for quota management
 * - Uses Redis for fast daily counter (with auto-expiry)
 * - Logs every call to Cassandra for historical analysis
 * - Daily quota: 1000 calls
 */
class OneFrameApiTracker[F[_]: Concurrent](
    redis: RedisCommands[F, String, String],
    cassandraSession: CqlSession,
    dailyQuota: Int = 1000
) {

  private val logger = LoggerFactory.getLogger(getClass)
  private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

  private val insertStatement = cassandraSession.prepare(
    """INSERT INTO forex.oneframe_api_calls 
      |(date, timestamp, endpoint, pairs_count, success, error_message, duration_ms) 
      |VALUES (?, ?, ?, ?, ?, ?, ?)""".stripMargin
  )

  /**
   * Get current date key for Redis counter
   */
  private def getTodayKey: String = {
    val today = LocalDate.now(ZoneOffset.UTC).format(dateFormatter)
    s"oneframe:quota:$today"
  }

  /**
   * Get current API call count for today
   */
  def getCurrentCount: F[Int] = {
    redis.get(getTodayKey).map {
      case Some(count) => count.toInt
      case None => 0
    }
  }

  /**
   * Check if quota is available
   */
  def hasQuotaAvailable: F[Boolean] = {
    getCurrentCount.map(_ < dailyQuota)
  }

  /**
   * Get remaining quota for today
   */
  def getRemainingQuota: F[Int] = {
    getCurrentCount.map(count => math.max(0, dailyQuota - count))
  }

  /**
   * Track an API call (increment counter and log to Cassandra)
   */
  def trackCall(
      endpoint: String,
      pairsCount: Int,
      success: Boolean,
      errorMessage: Option[String] = None,
      durationMs: Long = 0
  ): F[Unit] = {
    val today = LocalDate.now(ZoneOffset.UTC).format(dateFormatter)
    val timestamp = System.currentTimeMillis()
    val redisKey = getTodayKey

    // Increment Redis counter with 25-hour TTL (auto-cleanup)
    val incrementCounter = redis.get(redisKey).flatMap {
      case Some(count) =>
        redis.setEx(redisKey, (count.toInt + 1).toString, 25.hours) // 25 hours
      case None =>
        redis.setEx(redisKey, "1", 25.hours) // 25 hours
    }

    // Log to Cassandra async
    val logToCassandra = Concurrent[F].delay {
      val bound = insertStatement.bind(
        today,
        java.lang.Long.valueOf(timestamp),
        endpoint,
        java.lang.Integer.valueOf(pairsCount),
        java.lang.Boolean.valueOf(success),
        errorMessage.orNull,
        java.lang.Long.valueOf(durationMs)
      )
      cassandraSession.execute(bound)
    }

    // Execute both: increment counter and log to Cassandra + database.log
    incrementCounter.flatMap { _ =>
      getCurrentCount.flatMap { newCount =>
        // Log to database.log using DbLogger
        val dbLogEntry = DbLogger.DbLogEntry(
          operation = DbLogger.DbOperation.INSERT,
          table = "oneframe_api_calls",
          executionTimeMs = durationMs,
          rowsAffected = Some(1),
          success = success,
          error = errorMessage,
          query = Some(s"OneFrame API: $endpoint (pairs=$pairsCount, quota=$newCount/$dailyQuota)")
        )
        
        DbLogger.log[F](dbLogEntry).flatMap { _ =>
          Concurrent[F].delay(
            logger.info(s"oneframe api call tracked: endpoint=$endpoint pairs=$pairsCount success=$success quota=$newCount/$dailyQuota")
          ).flatMap(_ =>
            Concurrent[F].start(logToCassandra.attempt.void).void // async, fire-and-forget
          )
        }
      }
    }
  }

  /**
   * Get quota status for monitoring
   */
  case class QuotaStatus(
      date: String,
      used: Int,
      remaining: Int,
      limit: Int,
      percentageUsed: Double
  )

  def getQuotaStatus: F[QuotaStatus] = {
    val today = LocalDate.now(ZoneOffset.UTC).format(dateFormatter)
    getCurrentCount.map { used =>
      val remaining = math.max(0, dailyQuota - used)
      val percentage = (used.toDouble / dailyQuota) * 100
      QuotaStatus(today, used, remaining, dailyQuota, percentage)
    }
  }
}

/**
  * Create new tracker instance
  */
object OneFrameApiTracker {
  def create[F[_]: Concurrent](
      redis: RedisCommands[F, String, String],
      cassandraSession: CqlSession,
      dailyQuota: Int = 1000
  ): F[OneFrameApiTracker[F]] = {
    Concurrent[F].pure(new OneFrameApiTracker[F](redis, cassandraSession, dailyQuota))
  }
}
