package forex.programs.rates

import cats.effect.Sync
import cats.data.EitherT
import cats.syntax.flatMap._
import cats.syntax.functor._
import errors._
import forex.domain._
import forex.services.RatesService
import org.slf4j.LoggerFactory

class Program[F[_]: Sync](
    ratesService: RatesService[F]
) extends Algebra[F] {

  private val logger = LoggerFactory.getLogger(getClass)

  override def get(request: Protocol.GetRatesRequest): F[Error Either Rate] =
    for {
      _ <- Sync[F].delay(logger.info(s"Processing rate request: ${request.from} -> ${request.to}"))
      result <- EitherT(ratesService.get(Rate.Pair(request.from, request.to)))
        .leftMap { error =>
          logger.warn(s"Service error for ${request.from} -> ${request.to}: $error")
          toProgramError(error)
        }
        .map { rate =>
          logger.debug(s"Successfully retrieved rate: $rate")
          rate
        }
        .value
    } yield result

}

object Program {

  def apply[F[_]: Sync](
      ratesService: RatesService[F]
  ): Algebra[F] = new Program[F](ratesService)

}
