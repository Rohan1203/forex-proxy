package forex.http
package rates

import cats.effect.{Clock, Sync}
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.applicativeError._
import forex.programs.RatesProgram
import forex.programs.rates.{ Protocol => RatesProgramProtocol }
import forex.logging.AccessLogger
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import org.http4s.server.Router
import org.http4s.headers.`User-Agent`
import org.typelevel.ci.CIString

class RatesHttpRoutes[F[_]: Sync: Clock](
    rates: RatesProgram[F]
) extends Http4sDsl[F] {

  import Converters._, QueryParams._, Protocol._

  // Extract client IP from headers or request
  private def getClientIp(req: org.http4s.Request[F]): Option[String] = {
    val xForwardedFor = req.headers.get(CIString("X-Forwarded-For")).map(_.head.value)
    val xRealIp = req.headers.get(CIString("X-Real-IP")).map(_.head.value)
    val remoteAddr = req.remoteAddr.map(_.toString)
    
    println(s"[DEBUG] X-Forwarded-For: $xForwardedFor, X-Real-IP: $xRealIp, remoteAddr: $remoteAddr")
    
    xForwardedFor
      .map(_.split(",").head.trim) // Get first IP from X-Forwarded-For
      .orElse(xRealIp)
      .orElse(remoteAddr)
  }

  // Get client ID from header or use IP as fallback
  private def getClientId(req: org.http4s.Request[F]): Option[String] = {
    val customClientId = req.headers.get(CIString("X-Client-ID")).map(_.head.value)
    
    customClientId match {
      case Some(clientId) if clientId.nonEmpty =>
        println(s"[DEBUG] Using custom client ID from header: $clientId")
        Some(clientId)
      case _ =>
        val clientIp = getClientIp(req)
        println(s"[DEBUG] No custom client ID, using IP: $clientIp")
        clientIp
    }
  }

  private[http] val prefixPath = "/rates"

  // Define HTTP routes for rates API
  private val httpRoutes: HttpRoutes[F] = HttpRoutes.of[F] {
    // Single pair endpoint
    case req @ GET -> Root :? FromQueryParam(from) +& ToQueryParam(to) =>
      val clientIp = getClientIp(req)
      val clientId = getClientId(req)
      AccessLogger.logRequest(
        "GET",
        "/rates",
        s"from=$from&to=$to",
        clientIp,
        req.headers.get[`User-Agent`].map(_.product.value)
      ) {
        rates.get(RatesProgramProtocol.GetRatesRequest(from, to), clientId).flatMap { result =>
          Sync[F].fromEither(result).flatMap { rate =>
            Ok(rate.asGetApiResponse).map(response => (200, response))
          }
        }.handleErrorWith { error =>
          val errorResponse = Protocol.ErrorResponse(
            error = "Service Unavailable",
            message = Option(error.getMessage).getOrElse("Unable to fetch exchange rate at this time. Please try again later.")
          )
          InternalServerError(errorResponse).map(response => (500, response))
        }
      }

    // Catch-all for missing or invalid parameters
    case GET -> Root =>
      val errorResponse = Protocol.ErrorResponse(
        error = "Bad Request",
        message = "Invalid parameters. Please provide both 'from' and 'to' correct currency codes; Please find the supported currency here, https://www.xe.com/currency/"
      )
      BadRequest(errorResponse)
  }

  val routes: HttpRoutes[F] = Router(
    prefixPath -> httpRoutes
  )

}
