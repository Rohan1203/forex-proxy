package forex.services.cassandra

import cats.effect.{Concurrent, Resource, Sync}
import cats.syntax.applicativeError._
import cats.syntax.flatMap._
import com.datastax.oss.driver.api.core.CqlSession
import com.datastax.oss.driver.api.core.cql.PreparedStatement
import forex.config.CassandraConfig
import forex.domain.{Currency, Rate}
import forex.logging.DbLogger
import org.slf4j.LoggerFactory
import java.net.InetSocketAddress
import scala.jdk.CollectionConverters._

/**
 * Cassandra repository for storing historical forex rates
 * Table schema:
 *   CREATE KEYSPACE IF NOT EXISTS forex WITH replication = {'class':'SimpleStrategy', 'replication_factor':1};
 *   CREATE TABLE IF NOT EXISTS forex.rates_history (
 *     pair_from text,
 *     pair_to text,
 *     timestamp bigint,
 *     price decimal,
 *     bid decimal,
 *     ask decimal,
 *     last_updated text,
 *     PRIMARY KEY ((pair_from, pair_to), timestamp)
 *   ) WITH CLUSTERING ORDER BY (timestamp DESC);
 */
class CassandraRatesRepository[F[_]: Concurrent](
    session: CqlSession
) {

  private val logger = LoggerFactory.getLogger(getClass)

  private val insertStatement: PreparedStatement = {
    val query = 
      """INSERT INTO forex.rates_history 
        |(pair_from, pair_to, timestamp, price, bid, ask, last_updated) 
        |VALUES (?, ?, ?, ?, ?, ?, ?)""".stripMargin
    session.prepare(query)
  }

  /**
   * Store a rate in Cassandra for historical data
   */
  def storeRate(rate: Rate): F[Unit] = {
    val startTime = System.currentTimeMillis()
    Concurrent[F].delay {
      val bound = insertStatement.bind(
        Currency.show.show(rate.pair.from),
        Currency.show.show(rate.pair.to),
        java.lang.Long.valueOf(rate.timestamp.value.toInstant.toEpochMilli),
        rate.price.value.bigDecimal,
        rate.bid.value.bigDecimal,
        rate.ask.value.bigDecimal,
        rate.last_updated
      )
      session.execute(bound)
      logger.debug(s"stored rate in cassandra: ${Currency.show.show(rate.pair.from)}${Currency.show.show(rate.pair.to)}")
    }.flatMap { _ =>
      val duration = System.currentTimeMillis() - startTime
      DbLogger.log[F](DbLogger.DbLogEntry(
        DbLogger.DbOperation.INSERT,
        "rates_history",
        duration,
        rowsAffected = Some(1),
        success = true
      ))
    }.handleErrorWith { error =>
      val duration = System.currentTimeMillis() - startTime
      DbLogger.log[F](DbLogger.DbLogEntry(
        DbLogger.DbOperation.INSERT,
        "rates_history",
        duration,
        success = false,
        error = Some(error.getMessage)
      )).flatMap(_ =>
        Concurrent[F].delay(
          logger.error(s"failed to store rate in cassandra: ${error.getMessage}")
        )
      )
    }
  }

  /**
   * Store multiple rates in batch
   */
  def storeRates(rates: List[Rate]): F[Unit] = {
    val startTime = System.currentTimeMillis()
    Concurrent[F].delay {
      rates.foreach { rate =>
        val bound = insertStatement.bind(
          Currency.show.show(rate.pair.from),
          Currency.show.show(rate.pair.to),
          java.lang.Long.valueOf(rate.timestamp.value.toInstant.toEpochMilli),
          rate.price.value.bigDecimal,
          rate.bid.value.bigDecimal,
          rate.ask.value.bigDecimal,
          rate.last_updated
        )
        session.execute(bound)
      }
    }.flatMap { _ =>
      val duration = System.currentTimeMillis() - startTime
      DbLogger.log[F](DbLogger.DbLogEntry(
        DbLogger.DbOperation.BATCH,
        "rates_history",
        duration,
        rowsAffected = Some(rates.length),
        success = true
      )).flatMap(_ =>
        Concurrent[F].delay(
          logger.info(s"stored ${rates.length} rates in cassandra (${duration}ms)")
        )
      )
    }.handleErrorWith { error =>
      val duration = System.currentTimeMillis() - startTime
      DbLogger.log[F](DbLogger.DbLogEntry(
        DbLogger.DbOperation.BATCH,
        "rates_history",
        duration,
        success = false,
        error = Some(error.getMessage)
      )).flatMap(_ =>
        Concurrent[F].delay(
          logger.error(s"failed to store rates in cassandra: ${error.getMessage}")
        )
      )
    }
  }
}

object CassandraRatesRepository {
  
  /**
   * Create Cassandra session and initialize keyspace/table
   */
  def createSession[F[_]: Sync](config: CassandraConfig): Resource[F, CqlSession] = {
    Resource.make(
      Sync[F].delay {
        val contactPoints = config.contactPoints.map(cp => 
          new InetSocketAddress(cp, config.port)
        ).asJava

        val session = CqlSession.builder()
          .addContactPoints(contactPoints)
          .withLocalDatacenter(config.datacenter)
          .build()

        // Initialize keyspace and table
        session.execute(
          s"""CREATE KEYSPACE IF NOT EXISTS ${config.keyspace} 
             |WITH replication = {'class':'SimpleStrategy', 'replication_factor':1}""".stripMargin
        )

        session.execute(
          s"""CREATE TABLE IF NOT EXISTS ${config.keyspace}.rates_history (
             |  pair_from text,
             |  pair_to text,
             |  timestamp bigint,
             |  price decimal,
             |  bid decimal,
             |  ask decimal,
             |  last_updated text,
             |  PRIMARY KEY ((pair_from, pair_to), timestamp)
             |) WITH CLUSTERING ORDER BY (timestamp DESC)""".stripMargin
        )

        // API call tracking table
        session.execute(
          s"""CREATE TABLE IF NOT EXISTS ${config.keyspace}.oneframe_api_calls (
             |  date text,
             |  timestamp bigint,
             |  endpoint text,
             |  pairs_count int,
             |  success boolean,
             |  error_message text,
             |  duration_ms bigint,
             |  PRIMARY KEY (date, timestamp)
             |) WITH CLUSTERING ORDER BY (timestamp DESC)""".stripMargin
        )

        session
      }
    )(session => Sync[F].delay(session.close()))
  }

  def create[F[_]: Concurrent](session: CqlSession): F[CassandraRatesRepository[F]] = {
    Concurrent[F].pure(new CassandraRatesRepository[F](session))
  }
}
