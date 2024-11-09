package forex.http.rates

import cats.effect.Sync
import cats.implicits._
import forex.domain.Currency
import forex.http.jsonEncoder
import forex.http.rates.Converters.GetApiResponseOps
import forex.http.rates.QueryParams.{FromQueryParam, ToQueryParam}
import forex.programs.RatesProgram
import forex.programs.rates.Protocol
import forex.services.rates.JsonError
import io.circe.syntax.EncoderOps
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.slf4j.LoggerFactory

/**
 *
 * RatesHttpRoutes Manages /rates endpoint
 * Filters query params
 * responsible for sending response [ok, bad]
 *
 */
class RatesHttpRoutes[F[_]: Sync](rates: RatesProgram[F]) extends Http4sDsl[F] {
  private val logger = LoggerFactory.getLogger(getClass)
  private[http] val prefixPath = "/rates"

  private val httpRoutes: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root :? FromQueryParam(from) +& ToQueryParam(to) =>
      logger.debug(s"/rates with query params from=$from to=$to")

      try {
        val fromCurrency = Currency.fromString(from)
        val toCurrency = Currency.fromString(to)

        // Fetch rates using the successfully parsed currencies
        rates.get(Protocol.GetRatesRequest(fromCurrency, toCurrency)).flatMap {
          case Right(rate) =>
            // If successful, return the rate as JSON
            Ok(rate.asGetApiResponse)

          case Left(error) =>
            logger.error(s"Rate lookup failed: ${error.getMessage}")
            // Create a JsonError instance with the error message
            val jsonError = JsonError(error.getMessage, Some(400))
            // Return a BadRequest with the JSON error response
            BadRequest(jsonError.asJson)
        }

      } catch {
        case e: IllegalArgumentException =>
          logger.error(s"Invalid currency error: ${e.getMessage}")
          val jsonError = JsonError(e.getMessage, Some(400)) // 400 Bad Request
          BadRequest(jsonError.asJson)
        case e: Exception =>
          logger.error(s"Unexpected error: ${e.getMessage}")
          val jsonError = JsonError("An unexpected error occurred", Some(500)) // 500 Internal Server Error
          BadRequest(jsonError.asJson)
      }
  }

  logger.info("/rates:ok")

  // register route
  val routes: HttpRoutes[F] = httpRoutes
}