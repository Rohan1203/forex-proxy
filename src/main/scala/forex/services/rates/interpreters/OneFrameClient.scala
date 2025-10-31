package forex.services.rates.interpreters

import cats.effect.Sync
import cats.syntax.applicativeError._
import cats.syntax.either._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.show._
import forex.config.OneFrameConfig
import forex.domain.{Price, Rate, Timestamp}
import forex.services.rates.Algebra
import forex.services.rates.errors._
import io.circe.{Decoder, HCursor}
import org.http4s.{Header, Headers, Method, Request, Uri}
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.client.Client
import org.typelevel.ci.CIString

case class OneFrameResponse(
    from: String,
    to: String,
    bid: BigDecimal,
    ask: BigDecimal,
    price: BigDecimal,
    time_stamp: String
)

object OneFrameResponse {
  implicit val decoder: Decoder[OneFrameResponse] = (c: HCursor) =>
    for {
      from <- c.downField("from").as[String]
      to <- c.downField("to").as[String]
      bid <- c.downField("bid").as[BigDecimal]
      ask <- c.downField("ask").as[BigDecimal]
      price <- c.downField("price").as[BigDecimal]
      timeStamp <- c.downField("time_stamp").as[String]
    } yield OneFrameResponse(from, to, bid, ask, price, timeStamp)
}

class OneFrameClient[F[_]: Sync](
    client: Client[F],
    config: OneFrameConfig
) extends Algebra[F] {

  override def get(pair: Rate.Pair): F[Error Either Rate] =
    fetchRate(pair)
      .map(rate => rate.asRight[Error])
      .handleErrorWith { error =>
        Sync[F].pure(Error.OneFrameLookupFailed(error.getMessage).asLeft[Rate])
      }

  private def fetchRate(pair: Rate.Pair): F[Rate] = {
    val pairParam = s"${pair.from.show}${pair.to.show}"
    
    Uri.fromString(s"${config.uri}/rates?pair=$pairParam") match {
      case Left(parseError) =>
        Sync[F].raiseError(new Exception(s"Invalid URI: ${parseError.message}"))
      
      case Right(uri) =>
        val request = Request[F](
          method = Method.GET,
          uri = uri,
          headers = Headers(Header.Raw(CIString("token"), config.token))
        )

        client.expect[List[OneFrameResponse]](request).flatMap {
          case response :: _ =>
            Sync[F].delay {
              Rate(
                pair = pair,
                bid = Price(response.bid),
                ask = Price(response.ask),
                price = Price(response.price),
                timestamp = Timestamp.now,
                last_updated = response.time_stamp
              )
            }
          case Nil =>
            Sync[F].raiseError(new Exception(s"No rate found for ${pair.from.show}${pair.to.show}"))
        }
    }
  }
}
