package forex.http.rates

import forex.domain.Currency
import org.http4s.QueryParamDecoder
import org.http4s.dsl.impl.QueryParamDecoderMatcher
import scala.util.Try

object QueryParams {

  private[http] implicit val currencyQueryParam: QueryParamDecoder[Currency] =
    QueryParamDecoder[String].emap { str =>
      Try(Currency.fromString(str)).toEither.left.map { _ =>
        org.http4s.ParseFailure(s"Invalid currency code: $str", s"Currency code must be one of the supported ISO 4217 codes")
      }
    }

  object FromQueryParam extends QueryParamDecoderMatcher[Currency]("from")
  object ToQueryParam extends QueryParamDecoderMatcher[Currency]("to")
}
