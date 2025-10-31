package forex.http
package rates

import forex.domain.Currency.show
import forex.domain.Rate.Pair
import forex.domain._
import io.circe._
import io.circe.generic.extras.Configuration
import io.circe.generic.extras.semiauto.deriveConfiguredEncoder

object Protocol {

  implicit val configuration: Configuration = Configuration.default.withSnakeCaseMemberNames

  final case class GetApiRequest(
      from: Currency,
      to: Currency
  )

  final case class RatesInfo(
      bid: Price,
      ask: Price,
      price: Price
  )

  final case class GetApiResponse(
      from: Currency,
      to: Currency,
      rates: RatesInfo,
      timestamp: Timestamp,
      last_updated: String
  )

  final case class GetBatchApiResponse(
      rates: List[GetApiResponse]
  )

  implicit val currencyEncoder: Encoder[Currency] =
    Encoder.instance[Currency] { show.show _ andThen Json.fromString }

  implicit val pairEncoder: Encoder[Pair] =
    deriveConfiguredEncoder[Pair]

  implicit val rateEncoder: Encoder[Rate] =
    deriveConfiguredEncoder[Rate]

  implicit val ratesInfoEncoder: Encoder[RatesInfo] =
    deriveConfiguredEncoder[RatesInfo]

  implicit val responseEncoder: Encoder[GetApiResponse] =
    deriveConfiguredEncoder[GetApiResponse]

  implicit val batchResponseEncoder: Encoder[GetBatchApiResponse] =
    deriveConfiguredEncoder[GetBatchApiResponse]

}
