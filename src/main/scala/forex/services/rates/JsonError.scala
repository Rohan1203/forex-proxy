package forex.services.rates

import io.circe.Encoder

case class JsonError(message: String, code: Option[Int] = None)

object JsonError {
  implicit val encoder: Encoder[JsonError] = Encoder.forProduct1("message") { error =>
    error.message
  }
}