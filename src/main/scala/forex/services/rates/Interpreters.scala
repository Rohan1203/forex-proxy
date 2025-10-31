package forex.services.rates

import cats.effect.Sync
import forex.config.OneFrameConfig
import org.http4s.client.Client
import interpreters._

object Interpreters {
  def dummy[F[_]: Sync]: Algebra[F] = new OneFrameDummy[F]()
  
  def live[F[_]: Sync](client: Client[F], config: OneFrameConfig): Algebra[F] =
    new OneFrameClient[F](client, config)
}
