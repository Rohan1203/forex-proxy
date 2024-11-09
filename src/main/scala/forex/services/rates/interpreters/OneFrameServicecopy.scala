// latest working
//package forex.services.rates.interpreters
//
//import forex.services.rates.Algebra
//import forex.domain.{OneFrame, Price, Rate, Timestamp}
//import forex.services.rates.OneFrameError._
//import cats.effect.Sync
//import akka.http.scaladsl.Http
//import akka.http.scaladsl.model._
//import akka.actor.ActorSystem
//import akka.stream.Materializer
//import scala.concurrent.{ExecutionContext, Future, Await}
//import pureconfig.ConfigSource
//import io.circe.parser.decode
//import io.circe.generic.auto._
//import forex.config.ApplicationConfig
//import org.slf4j.LoggerFactory
//import pureconfig.generic.auto.exportReader
//
///**
// *
// * OneFrameService interacts with OneFrame API, handles response, manage cache
// *
// * @param ec Implicit ExecutionContext for asynchronous operations.
// */
//class OneFrameService[F[_]: Sync](implicit ec: ExecutionContext) extends Algebra[F] {
//  private val logger = LoggerFactory.getLogger(getClass)
//  val config: ApplicationConfig = ConfigSource.default.at("app").loadOrThrow[ApplicationConfig]
//  implicit val system: ActorSystem = ActorSystem("OneFrameClient")
//  implicit val materializer: Materializer = Materializer(system)
//
//  // Initialize the cache store
//  private val cacheStore = new CacheStore()
//
//  /**
//   * Executes the HTTP request to the OneFrame API for the given currency pair.
//   *
//   * @param pair The currency pair for which to fetch the rate.
//   * @return Either containing the Rate object or an error.
//   */
//  private def execute(pair: Rate.Pair): Either[Error, Rate] = {
//    val baseUri = config.oneframe.uri
//    val currencyPairs = s"${pair.from}${pair.to}"
//    val uri = s"$baseUri?pair=$currencyPairs"
//    val request = HttpRequest(
//      method = HttpMethods.GET,
//      uri = uri,
//      headers = List(headers.RawHeader("token", config.oneframe.token))
//    )
//    logger.debug(s"oneframe uri: ${uri}")
//    try {
//      // making the request to source
//      val responseFuture = Http().singleRequest(request)
//
//      // Use Await.result to block until we get the response
//      val response = Await.result(responseFuture, config.oneframe.timeout) // Timeout set here
//
//      // Read the response body and decode
//      val entity = Await.result(response.entity.dataBytes.runFold("")(_ + _.utf8String), config.oneframe.timeout) // Timeout for response body
//
//      decode[List[OneFrame]](entity) match {
//        case Right(responses) =>
//          responses.headOption match {
//            case Some(rateResponse) =>
//              val rate = Rate(pair, Price(rateResponse.price), Timestamp.now)
//              // Save to cache asynchronously, but it does not block the main response flow
//              // The cache save operation is non-blocking (it returns a Future)
//              Future {
//                cacheStore.saveToCache(pair, rate)
//              }
//              Right(rate)
//            case None =>
//              logger.error("No rate data found")
//              Left(Error.OneFrameLookupFailed("No rate data found"))
//          }
//        case Left(error) =>
//          logger.error(s"Failed to decode response: ${error.getMessage}")
//          Left(Error.OneFrameLookupFailed("Invalid response format"))
//      }
//
//    } catch {
//      case ex: java.util.concurrent.TimeoutException =>
//        // timeout exception
//        logger.error(s"Timeout occurred: ${ex.getMessage}")
//        Left(Error.OneFrameLookupFailed("Internal Request timed out, Please try again later."))
//      case exception: Throwable =>
//        // connection refused exception or any other
//        logger.error(s"Request failed with exception: ${exception.getMessage}", exception)
//        Left(Error.OneFrameLookupFailed("Some internal error occurred, Please try again later."))
//    }
//  }
//
//  /**
//   * Method to retrieve the exchange rate for a given currency pair.
//   */
//  @Override
//  override def get(pair: Rate.Pair): F[Either[Error, Rate]] = {
//    // Try to get from cache first
//    Sync[F].delay {
//      cacheStore.getFromCache(pair) match {
//        case Some(cachedRate) =>
//          logger.debug(s"${pair} found in cache, returning to service")
//          // Cache hit, return the cached rate
//          Right(Rate(pair, cachedRate.price, cachedRate.timestamp))
//        case None =>
//          logger.debug(s"${pair} not found in cache, fetching from oneframe")
//          // Cache miss, fetch the rate from OneFrame
//          execute(pair)
//      }
//    }
//  }
//}


// latest working
//package forex.http.rates
//
//import cats.effect.Sync
//import cats.implicits._
//import forex.domain.Currency
//import forex.http.jsonEncoder
//import forex.http.rates.Converters.GetApiResponseOps
//import forex.http.rates.QueryParams.{FromQueryParam, ToQueryParam}
//import forex.programs.RatesProgram
//import forex.programs.rates.Protocol
//import forex.services.rates.JsonError
//import io.circe.syntax.EncoderOps
//import org.http4s.HttpRoutes
//import org.http4s.dsl.Http4sDsl
//import org.slf4j.LoggerFactory
//
///**
// *
// * RatesHttpRoutes Manages /rates endpoint
// * Filters query params
// * responsible for sending response [ok, bad]
// *
// */
//class RatesHttpRoutes[F[_]: Sync](rates: RatesProgram[F]) extends Http4sDsl[F] {
//  private val logger = LoggerFactory.getLogger(getClass)
//  private[http] val prefixPath = "/rates"
//
//  private val httpRoutes: HttpRoutes[F] = HttpRoutes.of[F] {
//    case GET -> Root :? FromQueryParam(from) +& ToQueryParam(to) =>
//      logger.debug(s"/rates with query params from=$from to=$to")
//
//      try {
//        val fromCurrency = Currency.fromString(from)
//        val toCurrency = Currency.fromString(to)
//
//        // Fetch rates using the successfully parsed currencies
//        rates.get(Protocol.GetRatesRequest(fromCurrency, toCurrency)).flatMap {
//          case Right(rate) =>
//            // If successful, return the rate as JSON
//            Ok(rate.asGetApiResponse)
//
//          case Left(error) =>
//            logger.error(s"Rate lookup failed: ${error.getMessage}")
//            // Create a JsonError instance with the error message
//            val jsonError = JsonError(error.getMessage, Some(400))
//            // Return a BadRequest with the JSON error response
//            BadRequest(jsonError.asJson)
//        }
//
//      } catch {
//        case e: IllegalArgumentException =>
//          logger.error(s"Invalid currency error: ${e.getMessage}")
//          val jsonError = JsonError(e.getMessage, Some(400)) // 400 Bad Request
//          BadRequest(jsonError.asJson)
//        case e: Exception =>
//          logger.error(s"Unexpected error: ${e.getMessage}")
//          val jsonError = JsonError("An unexpected error occurred", Some(500)) // 500 Internal Server Error
//          BadRequest(jsonError.asJson)
//      }
//  }
//
//  logger.info("/rates:ok")
//
//  // register route
//  val routes: HttpRoutes[F] = httpRoutes
//}