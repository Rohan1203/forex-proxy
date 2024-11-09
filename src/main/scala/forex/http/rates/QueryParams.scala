package forex.http.rates

import forex.domain.Currency
import org.http4s.QueryParamDecoder
import org.http4s.dsl.impl.QueryParamDecoderMatcher

object QueryParams {
  private[http] implicit val currencyQueryParam: QueryParamDecoder[Currency] =
    QueryParamDecoder[String].map(Currency.fromString)

  object FromQueryParam extends QueryParamDecoderMatcher[String]("from")
  object ToQueryParam extends QueryParamDecoderMatcher[String]("to")
}
