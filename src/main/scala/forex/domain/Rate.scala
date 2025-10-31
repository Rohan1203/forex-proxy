package forex.domain

case class Rate(
    pair: Rate.Pair,
    bid: Price,
    ask: Price,
    price: Price,
    timestamp: Timestamp,
    last_updated: String
)

object Rate {
  final case class Pair(
      from: Currency,
      to: Currency
  )
}
