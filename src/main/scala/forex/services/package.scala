package forex

import cats.effect.Sync
import forex.config.OneFrameConfig
import org.http4s.client.Client

package object services {
  type RatesService[F[_]] = rates.Algebra[F]
  
  object RatesServices {
    def dummy[F[_]: Sync]: RatesService[F] = rates.Interpreters.dummy[F]
    def live[F[_]: Sync](client: Client[F], config: OneFrameConfig): RatesService[F] = 
      rates.Interpreters.live[F](client, config)
  }
}
