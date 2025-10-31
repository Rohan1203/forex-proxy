package forex.http
package rates

import cats.effect.{Clock, Sync}
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.Traverse
import cats.instances.list._
import forex.programs.RatesProgram
import forex.programs.rates.{ Protocol => RatesProgramProtocol }
import forex.logging.AccessLogger
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router
import org.http4s.headers.`User-Agent`
import java.util.concurrent.TimeUnit

class RatesHttpRoutes[F[_]: Sync: Clock](rates: RatesProgram[F]) extends Http4sDsl[F] {

  import Converters._, QueryParams._, Protocol._

  private[http] val prefixPath = "/rates"

  private val httpRoutes: HttpRoutes[F] = HttpRoutes.of[F] {
    // Batch endpoint: /rates?pair=USD-EUR&pair=GBP-USD&pair=JPY-EUR
    case req @ GET -> Root :? PairQueryParamMatcher(pairs) if pairs.nonEmpty =>
      Clock[F].monotonic(TimeUnit.MILLISECONDS).flatMap { start =>
        Traverse[List].sequence[F, forex.domain.Rate](
          pairs.map { pair =>
            rates.get(RatesProgramProtocol.GetRatesRequest(pair._1, pair._2)).flatMap(Sync[F].fromEither)
          }
        ).flatMap { ratesList =>
          val batchResponse = Protocol.GetBatchApiResponse(ratesList.map(_.asGetApiResponse))
          Ok(batchResponse).flatMap { response =>
            Clock[F].monotonic(TimeUnit.MILLISECONDS).flatMap { end =>
              AccessLogger.log(
                AccessLogger.AccessLogEntry(
                  method = "GET",
                  path = "/rates",
                  queryParams = s"batch=${pairs.length} pairs",
                  statusCode = 200,
                  responseTimeMs = end - start,
                  clientIp = req.remoteAddr.map(_.toString),
                  userAgent = req.headers.get[`User-Agent`].map(_.product.value)
                )
              ).as(response)
            }
          }
        }
      }

    // Single pair endpoint
    case req @ GET -> Root :? FromQueryParam(from) +& ToQueryParam(to) =>
      Clock[F].monotonic(TimeUnit.MILLISECONDS).flatMap { start =>
        rates.get(RatesProgramProtocol.GetRatesRequest(from, to)).flatMap { result =>
          Sync[F].fromEither(result).flatMap { rate =>
            Ok(rate.asGetApiResponse).flatMap { response =>
              Clock[F].monotonic(TimeUnit.MILLISECONDS).flatMap { end =>
                AccessLogger.log(
                  AccessLogger.AccessLogEntry(
                    method = "GET",
                    path = "/rates",
                    queryParams = s"from=$from&to=$to",
                    statusCode = 200,
                    responseTimeMs = end - start,
                    clientIp = req.remoteAddr.map(_.toString),
                    userAgent = req.headers.get[`User-Agent`].map(_.product.value)
                  )
                ).as(response)
              }
            }
          }
        }
      }
  }

  val routes: HttpRoutes[F] = Router(
    prefixPath -> httpRoutes
  )

}
