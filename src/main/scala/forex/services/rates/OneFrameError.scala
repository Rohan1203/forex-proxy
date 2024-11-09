package forex.services.rates

object OneFrameError {

  sealed trait Error extends Exception
  object Error {
    final case class OneFrameLookupFailed(msg: String) extends Error {
      override def getMessage: String = msg
    }
  }
}
class OneFrameTimeoutException(message: String) extends Exception(message)