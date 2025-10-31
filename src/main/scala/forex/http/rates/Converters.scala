package forex.http.rates

import forex.domain._

object Converters {
  import Protocol._

  private[rates] implicit class GetApiResponseOps(val rate: Rate) extends AnyVal {
    def asGetApiResponse: GetApiResponse =
      GetApiResponse(
        from = rate.pair.from,
        to = rate.pair.to,
        rates = RatesInfo(
          bid = rate.bid,
          ask = rate.ask,
          price = rate.price
        ),
        timestamp = rate.timestamp,
        last_updated = rate.last_updated
      )
  }

}
