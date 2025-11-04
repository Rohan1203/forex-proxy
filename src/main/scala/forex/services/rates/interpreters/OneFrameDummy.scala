package forex.services.rates.interpreters

import forex.services.rates.RatesRepository
import cats.effect.Sync
import cats.syntax.either._
import forex.domain.{ Price, Rate, Timestamp }
import forex.services.rates.errors._

class OneFrameDummy[F[_]: Sync] extends RatesRepository[F] {

  override def get(pair: Rate.Pair, clientId: Option[String] = None): F[Error Either Rate] =
    Sync[F].delay {
      val now = Timestamp.now
      val timeString = "2025-10-22T17:08:22.049076+09:00"
      val price = Price(BigDecimal(100))
      Rate(
        pair = pair,
        bid = price,
        ask = price,
        price = price,
        timestamp = now,
        last_updated = timeString
      ).asRight[Error]
    }

}
