package forex.services.rates.interpreters

import akka.actor.{ActorSystem, Cancellable}
import forex.config.ApplicationConfig
import forex.domain.{Currency, Price, Rate, Timestamp}
import org.slf4j.LoggerFactory
import pureconfig.ConfigSource
import pureconfig.generic.auto.exportReader
import java.sql.{Connection, DriverManager}
import java.time.{Instant, OffsetDateTime, ZoneOffset}
import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

/**
 * CacheStore is responsible for caching exchange rates in an SQLite database,
 * handling cache expiration, and cleaning expired entries periodically.
 *
 * @param system Implicit ActorSystem for scheduling tasks.
 * @param ec Implicit ExecutionContext for asynchronous operations.
 */
class CacheStore(implicit system: ActorSystem, ec: ExecutionContext) {

  // Logger for this class.
  private val logger = LoggerFactory.getLogger(getClass)

  // Load configuration values.
  val config: ApplicationConfig = ConfigSource.default.at("app").loadOrThrow[ApplicationConfig]

  // Cache expiry duration in milliseconds.
  private val cacheExpiryDuration: Long = config.cache.expiry.toLong

  // Database URI (SQLite database).
  private val dbUri: String = config.cache.uri

  // Initialize the SQLite database and ensure the cache table exists.
  //  initCache()

  /**
   * Initializes the SQLite database and creates the cache table if it doesn't already exist.
   */
  def initCache(): Unit = {
    logger.info("Initializing CacheStore")

    val connection = getConnection
    try {
      // Create the forex_cache table if it doesn't exist.
      val stmt = connection.createStatement()
      val createTableQuery =
        """
          CREATE TABLE IF NOT EXISTS forex_cache (
            pair_from TEXT NOT NULL,
            pair_to TEXT NOT NULL,
            rate REAL NOT NULL,
            timestamp TEXT NOT NULL,
            PRIMARY KEY (pair_from, pair_to)
          );
        """
      stmt.executeUpdate(createTableQuery)
      logger.info("Cache table initialized successfully")
    } catch {
      case ex: Exception =>
        logger.error("Error during CacheStore initialization", ex)
    } finally {
      connection.close()
    }
  }

  /**
   * Saves a currency pair exchange rate to the cache (SQLite database).
   *
   * @param pair The currency pair.
   * @param rate The exchange rate for the currency pair.
   */
  def saveToCache(pair: Rate.Pair, rate: Rate): Unit = {
    logger.warn(s"[CACHE MISS] for ${pair} Storing rate to cache")

    val connection = getConnection
    try {
      val stmt = connection.prepareStatement(
        """
          INSERT OR REPLACE INTO forex_cache (pair_from, pair_to, rate, timestamp)
          VALUES (?, ?, ?, ?)
        """
      )

      stmt.setString(1, Currency.show.show(pair.from))
      stmt.setString(2, Currency.show.show(pair.to))
      stmt.setBigDecimal(3, rate.price.value.underlying())

      // Store the timestamp in UTC format.
      val timestamp = OffsetDateTime.now(ZoneOffset.UTC)
      stmt.setString(4, timestamp.toString)

      val rowsAffected = stmt.executeUpdate()
      logger.info(s"Rate stored successfully, $rowsAffected rows affected")
    } catch {
      case ex: Exception =>
        logger.error("Error while saving rate to cache", ex)
    } finally {
      connection.close()
    }
  }

  /**
   * Retrieves a currency pair's exchange rate from the cache (SQLite database).
   *
   * @param pair The currency pair.
   * @return Option of Rate if found and not expired, None if cache miss or expired as per cacheExpiryDuration.
   */
  def getFromCache(pair: Rate.Pair): Option[Rate] = {
    logger.info(s"Retrieving ${pair} rate from cache")

    val connection = getConnection
    try {
      val stmt = connection.prepareStatement(
        """
          SELECT rate, timestamp FROM forex_cache
          WHERE pair_from = ? AND pair_to = ?
        """
      )

      stmt.setString(1, Currency.show.show(pair.from))
      stmt.setString(2, Currency.show.show(pair.to))

      val rs = stmt.executeQuery()
      if (rs.next()) {
        val rate = rs.getBigDecimal("rate")
        val timestampStr = rs.getString("timestamp")
        val timestamp = OffsetDateTime.parse(timestampStr)

        val timeElapsed = Instant.now().toEpochMilli - timestamp.toInstant.toEpochMilli

        // Check if the cache is still valid.
        if (timeElapsed < cacheExpiryDuration) {
          logger.info(s"[CACHE HIT] rate found for $pair. Time elapsed: $timeElapsed ms.")
          Some(Rate(pair, Price(rate), Timestamp(timestamp)))
        } else {
          logger.warn(s"Cache expired for $pair. Time elapsed: $timeElapsed ms, Expiry: $cacheExpiryDuration ms.")
          None
        }
      } else {
        logger.warn(s"No cache found for $pair")
        None
      }
    } catch {
      case ex: Exception =>
        logger.error("Error while retrieving rate from cache", ex)
        None
    } finally {
      connection.close()
    }
  }

  /**
   * Cleans expired entries from the cache (SQLite database).
   * Removes entries where the timestamp is older than the cache expiry duration.
   */
  private def cleanExpiredCache(): Unit = {
    logger.info("Cleaning expired cache entries!")

    val connection = getConnection
    try {
      val stmt = connection.prepareStatement(
        """
          DELETE FROM forex_cache
          WHERE timestamp < ?
        """
      )

      val expiryTimestamp = Instant.now().minusMillis(cacheExpiryDuration)
      stmt.setString(1, expiryTimestamp.toString)

      val rowsAffected = stmt.executeUpdate()
      if (rowsAffected > 0) {
        logger.info(s"Cleaned up $rowsAffected expired cache entries!")
      } else {
        logger.info("No expired cache entries found to clean")
      }
    } catch {
      case ex: Exception =>
        logger.error("Error while cleaning expired cache entries", ex)
    } finally {
      connection.close()
    }
  }

  /**
   *
   * Schedules the cleanup of expired cache entries to run at fixed intervals.
   * [NOTE] Cleanup is having some extra buffer than cache expiry logic for safe execution
   */
  def startCleanupScheduler(): Cancellable = {
    logger.info("Initializing cleanup scheduler")

    system.scheduler.scheduleAtFixedRate(
      initialDelay = config.cache.cleanup.millis, // Initial delay before first run
      interval = config.cache.cleanup.millis // Interval between subsequent runs
    ) { () =>
      cleanExpiredCache()
    }
  }

  /**
   * Helper function to obtain a database connection.
   */
  private def getConnection: Connection = {
    try {
      DriverManager.getConnection(dbUri)
    } catch {
      case ex: Exception =>
        logger.error("Failed to connect to CacheStore", ex)
        throw ex
    }
  }
}
