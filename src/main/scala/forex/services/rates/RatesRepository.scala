package forex.services.rates

import forex.domain.Rate
import errors._

trait RatesRepository[F[_]] {
  def get(pair: Rate.Pair, clientId: Option[String] = None): F[Error Either Rate]
}
