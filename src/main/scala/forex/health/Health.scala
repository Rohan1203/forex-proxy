package forex.health

import io.circe.{Encoder, Json}

import java.time.OffsetDateTime

case class Health(status: Server, timestamp: CurrentTimestamp, version: Version)

case class Server(value: String) extends AnyVal
case class CurrentTimestamp(value: OffsetDateTime) extends AnyVal
case class Version(value: String) extends AnyVal

// encoder for Health to flatten the JSON structure
object Health {
  implicit val encoder: Encoder[Health] = new Encoder[Health] {
    final def apply(health: Health): Json = Json.obj(
      ("status", Json.fromString(health.status.value)),
      ("timestamp", Json.fromString(health.timestamp.value.toString)),
      ("version", Json.fromString(health.version.value))
    )
  }
}

// Custom encoders for other case classes if necessary (optional)
//object Server {
//  implicit val encoder: Encoder[Server] = Encoder.forProduct1("value")(_.value)
//}
//
//object CurrentTimestamp {
//  implicit val encoder: Encoder[CurrentTimestamp] = Encoder.forProduct1("value")(_.value.toString)
//}
//
//object Version {
//  implicit val encoder: Encoder[Version] = Encoder.forProduct1("value")(_.value)
//}
