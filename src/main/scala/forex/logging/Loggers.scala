package forex.logging

import org.slf4j.LoggerFactory
import cats.effect.Sync
import cats.effect.Clock
import cats.syntax.flatMap._
import cats.syntax.functor._
import java.util.concurrent.TimeUnit

object AccessLogger {
  private val logger = LoggerFactory.getLogger("forex.logging.AccessLogger")

  case class AccessLogEntry(
      method: String,
      path: String,
      queryParams: String,
      statusCode: Int,
      responseTimeMs: Long,
      clientIp: Option[String] = None,
      userAgent: Option[String] = None
  ) {
    def toLogString: String =
      s"method=$method path=$path query=$queryParams status=$statusCode responseTime=${responseTimeMs}ms clientIp=${clientIp.getOrElse("N/A")} userAgent=${userAgent.getOrElse("N/A")}"
  }

  def log[F[_]: Sync](entry: AccessLogEntry): F[Unit] =
    Sync[F].delay(logger.info(entry.toLogString))

  /**
   * Helper to time and log HTTP requests with one-liner usage
   */
  def logRequest[F[_]: Sync: Clock, A](
      method: String,
      path: String,
      queryParams: String,
      clientIp: Option[String] = None,
      userAgent: Option[String] = None
  )(fa: F[(Int, A)]): F[A] =
    for {
      start <- Clock[F].monotonic(TimeUnit.MILLISECONDS)
      statusAndResult <- fa
      (status, result) = statusAndResult
      end <- Clock[F].monotonic(TimeUnit.MILLISECONDS)
      _ <- log(
        AccessLogEntry(
          method,
          path,
          queryParams,
          status,
          end - start,
          clientIp,
          userAgent
        )
      )
    } yield result
}

object DbLogger {
  private val logger = LoggerFactory.getLogger("forex.logging.DbLogger")

  sealed trait DbOperation
  object DbOperation {
    case object INSERT extends DbOperation
    case object UPDATE extends DbOperation
    case object DELETE extends DbOperation
    case object SELECT extends DbOperation
    case object BATCH extends DbOperation
  }

  case class DbLogEntry(
      operation: DbOperation,
      table: String,
      executionTimeMs: Long,
      rowsAffected: Option[Int] = None,
      success: Boolean = true,
      error: Option[String] = None,
      query: Option[String] = None
  ) {
    def toLogString: String = {
      val baseLog = s"operation=${operation.toString} table=$table duration_ms=$executionTimeMs success=$success"
      val rowsLog = rowsAffected.map(r => s" rowsAffected=$r").getOrElse("")
      val errorLog = error.map(e => s" error=$e").getOrElse("")
      val queryLog = query.map(q => s" query=$q").getOrElse("")
      baseLog + rowsLog + errorLog + queryLog
    }
  }

  def log[F[_]: Sync](entry: DbLogEntry): F[Unit] =
    Sync[F].delay(logger.info(entry.toLogString))

  def logSlow[F[_]: Sync](entry: DbLogEntry, thresholdMs: Long = 1000): F[Unit] =
    Sync[F].delay {
      if (entry.executionTimeMs > thresholdMs) {
        logger.warn(s"SLOW_QUERY ${entry.toLogString}")
      } else {
        logger.info(entry.toLogString)
      }
    }
}


/**
 * Helper for timing database operations
 */
object TimedDbOperation {
  def apply[F[_]: Sync: Clock, A](
      operation: DbLogger.DbOperation,
      table: String,
      query: Option[String] = None
  )(fa: F[A]): F[A] =
    for {
      start <- Clock[F].monotonic(TimeUnit.MILLISECONDS)
      result <- fa
      end <- Clock[F].monotonic(TimeUnit.MILLISECONDS)
      executionTime = end - start
      _ <- DbLogger.log(
        DbLogger.DbLogEntry(
          operation,
          table,
          executionTime,
          success = true,
          query = query
        )
      )
    } yield result
}

object CacheLogger {
  private val logger = LoggerFactory.getLogger("forex.logging.CacheLogger")

  sealed trait CacheOperation
  object CacheOperation {
    case object HIT extends CacheOperation
    case object MISS extends CacheOperation
    case object PUT extends CacheOperation
    case object EVICT extends CacheOperation
    case object CLEAR extends CacheOperation
  }

  case class CacheLogEntry(
      operation: CacheOperation,
      key: String,
      cacheName: String,
      executionTimeMs: Option[Long] = None,
      cacheSize: Option[Int] = None,
      ttl: Option[Long] = None,
      metadata: Map[String, String] = Map.empty
  ) {
    def toLogString: String = {
      val baseLog = s"operation=${operation.toString} cacheName=$cacheName key=$key"
      val timeLog = executionTimeMs.map(t => s" duration_ms=$t").getOrElse("")
      val sizeLog = cacheSize.map(s => s" cacheSize=$s").getOrElse("")
      val ttlLog = ttl.map(t => s" ttl=${t}ms").getOrElse("")
      val metaLog = if (metadata.nonEmpty) {
        metadata.map { case (k, v) => s"$k=$v" }.mkString(" ", " ", "")
      } else ""
      baseLog + timeLog + sizeLog + ttlLog + metaLog
    }
  }

  def log[F[_]: Sync](entry: CacheLogEntry): F[Unit] =
    Sync[F].delay(logger.info(entry.toLogString))

  def logHit[F[_]: Sync](cacheName: String, key: String, executionTimeMs: Option[Long] = None, metadata: Map[String, String] = Map.empty): F[Unit] =
    log(CacheLogEntry(CacheOperation.HIT, key, cacheName, executionTimeMs = executionTimeMs, metadata = metadata))

  def logMiss[F[_]: Sync](cacheName: String, key: String, metadata: Map[String, String] = Map.empty): F[Unit] =
    log(CacheLogEntry(CacheOperation.MISS, key, cacheName, metadata = metadata))

  def logPut[F[_]: Sync](cacheName: String, key: String, ttl: Option[Long] = None, metadata: Map[String, String] = Map.empty): F[Unit] =
    log(CacheLogEntry(CacheOperation.PUT, key, cacheName, ttl = ttl, metadata = metadata))

  def logEvict[F[_]: Sync](cacheName: String, key: String, metadata: Map[String, String] = Map.empty): F[Unit] =
    log(CacheLogEntry(CacheOperation.EVICT, key, cacheName, metadata = metadata))

  def logClear[F[_]: Sync](cacheName: String, cacheSize: Option[Int] = None): F[Unit] =
    log(CacheLogEntry(CacheOperation.CLEAR, "ALL", cacheName, cacheSize = cacheSize))

  /**
   * Logs cache statistics
   */
  case class CacheStats(
      cacheName: String,
      totalRequests: Long,
      hits: Long,
      misses: Long,
      size: Int,
      evictions: Long
  ) {
    def hitRate: Double = if (totalRequests > 0) hits.toDouble / totalRequests else 0.0
    
    def toJsonString: String =
      f"""{\"type\":\"CACHE_STATS\",\"cacheName\":\"$cacheName\",\"totalRequests\":$totalRequests,\"hits\":$hits,\"misses\":$misses,\"hitRate\":${hitRate * 100}%.2f,\"size\":$size,\"evictions\":$evictions}"""
  }

  def logStats[F[_]: Sync](stats: CacheStats): F[Unit] =
    Sync[F].delay(logger.info(stats.toJsonString))
}
