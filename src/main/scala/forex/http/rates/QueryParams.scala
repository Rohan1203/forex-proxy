package forex.http.rates

import forex.domain.Currency
import org.http4s.QueryParamDecoder
import org.http4s.dsl.impl.QueryParamDecoderMatcher
import scala.util.Try

object QueryParams {

  private[http] implicit val currencyQueryParam: QueryParamDecoder[Currency] =
    QueryParamDecoder[String].map(Currency.fromString)

  object FromQueryParam extends QueryParamDecoderMatcher[Currency]("from")
  object ToQueryParam extends QueryParamDecoderMatcher[Currency]("to")

  // Matcher for batch requests: ?pair=USD-EUR&pair=GBP-USD
  object PairQueryParamMatcher {
    def unapply(params: Map[String, collection.Seq[String]]): Option[List[(Currency, Currency)]] = {
      params.get("pair").map { pairs =>
        pairs.toList.flatMap { pairStr =>
          pairStr.split("-") match {
            case Array(from, to) =>
              Try {
                (Currency.fromString(from), Currency.fromString(to))
              }.toOption
            case _ => None
          }
        }
      }
    }
  }

}
