package forex.services.rates.interpreters

import forex.services.rates.Algebra
import forex.domain.{OneFrame, Price, Rate, Timestamp}
import forex.services.rates.OneFrameError._
import cats.effect.Sync
import akka.http.scaladsl.model._
import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.stream.Materializer
import akka.pattern.CircuitBreaker
import scala.concurrent.{Await, ExecutionContext, Future}
import pureconfig.ConfigSource
import io.circe.parser.decode
import io.circe.generic.auto._
import forex.config.ApplicationConfig
import org.slf4j.LoggerFactory
import pureconfig.generic.auto.exportReader

/**
 *
 * OneFrameService interacts with OneFrame API, handles response, manage cache
 * @param ec Implicit ExecutionContext for asynchronous operations.
 */
class OneFrameService[F[_]: Sync](implicit ec: ExecutionContext) extends Algebra[F] {
  private val logger = LoggerFactory.getLogger(getClass)
  val config: ApplicationConfig = ConfigSource.default.at("app").loadOrThrow[ApplicationConfig]
  implicit val system: ActorSystem = ActorSystem("OneFrameClient")
  implicit val materializer: Materializer = Materializer(system)

  // Initialize the cache store
  private val cacheStore = new CacheStore()

  // CircuitBreaker with scheduler
  private val circuitBreaker = CircuitBreaker(
    system.scheduler,
    maxFailures = config.oneframe.retry,
    callTimeout = config.oneframe.timeout,
    resetTimeout = config.oneframe.delay
  ).onOpen {
    logger.info("Circuit breaker is opened!")
  }.onClose {
    logger.info("Circuit breaker is closed!")
  }.onHalfOpen {
    logger.info("Circuit breaker is half-open!")
  }

  /**
   * Executes the HTTP request to the OneFrame API for the given currency pair.
   *
   * @param pair The currency pair for which to fetch the rate.
   * @return Either containing the Rate object or an error.
   */
  private def execute(pair: Rate.Pair): Either[Error, Rate] = {
    val baseUri = config.oneframe.uri
    val currencyPairs = s"${pair.from}${pair.to}"
    val uri = s"$baseUri?pair=$currencyPairs"
    val request = HttpRequest(
      method = HttpMethods.GET,
      uri = uri,
      headers = List(headers.RawHeader("token", config.oneframe.token))
    )

    logger.debug(s"oneframe uri: ${uri}")

    try {
      // Wrap the HTTP request with the circuit breaker
      val responseFuture: Future[HttpResponse] = circuitBreaker.withCircuitBreaker {
        Http().singleRequest(request)
      }

      // Use Await.result to block until we get the response
      val response = Await.result(responseFuture, config.oneframe.timeout)

      // Read the response body and decode
      val entity = Await.result(response.entity.dataBytes.runFold("")(_ + _.utf8String), config.oneframe.timeout)

      decode[List[OneFrame]](entity) match {
        case Right(responses) =>
          responses.headOption match {
            case Some(rateResponse) =>
              val rate = Rate(pair, Price(rateResponse.price), Timestamp.now)
              // Save to cache asynchronously
              Future {
                cacheStore.saveToCache(pair, rate)
              }
              Right(rate)
            case None =>
              logger.error("No rate data found")
              Left(Error.OneFrameLookupFailed("No rate data found"))
          }
        case Left(error) =>
          logger.error(s"Failed to decode response: ${error.getMessage}")
          Left(Error.OneFrameLookupFailed("Invalid response format"))
      }

    } catch {
      case ex: java.util.concurrent.TimeoutException =>
        logger.error(s"Timeout occurred: ${ex.getMessage}")
        Left(Error.OneFrameLookupFailed("Internal Request timed out, Please try again later."))
      case exception: Throwable =>
        logger.error(s"Request failed with exception: ${exception.getMessage}", exception)
        Left(Error.OneFrameLookupFailed("Some internal error occurred, Please try again later."))
    }
  }

  /**
   *
   * Method to retrieve the exchange rate for a given currency pair.
   */
  @Override
  override def get(pair: Rate.Pair): F[Either[Error, Rate]] = {
    // Try to get from cache first
    Sync[F].delay {
      cacheStore.getFromCache(pair) match {
        case Some(cachedRate) =>
          logger.debug(s"${pair} found in cache, returning to service")
          // Cache hit, return the cached rate
          Right(Rate(pair, cachedRate.price, cachedRate.timestamp))
        case None =>
          logger.debug(s"${pair} not found in cache, fetching from oneframe")
          // Cache miss, fetch the rate from OneFrame
          execute(pair)
      }
    }
  }
}