package forex.services.rates

import cats.effect.Sync
import interpreters._
import scala.concurrent.ExecutionContext

object Interpreters {
  // Use the global ExecutionContext or define your own
  implicit val ec: ExecutionContext = ExecutionContext.global

  // Only keep the Sync context bound
  def OneFrameInterpreter[F[_]: Sync]: Algebra[F] = new OneFrameService[F]()
}
