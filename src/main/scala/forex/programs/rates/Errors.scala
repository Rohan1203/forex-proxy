package forex.programs.rates

import forex.services.rates.OneFrameError.{ Error => RatesServiceError }

object Errors {

  sealed trait Error extends Exception
  object Error {
    final case class RateLookupFailed(msg: String) extends Error {
      override def getMessage: String = msg
    }
  }

  def toProgramError(error: RatesServiceError): Error = error match {
    case RatesServiceError.OneFrameLookupFailed(msg) => Error.RateLookupFailed(msg)
  }
}
