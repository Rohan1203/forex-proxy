package forex.domain

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto._
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter

package object codecs {
  
  implicit val timestampEncoder: Encoder[Timestamp] = Encoder.encodeString.contramap[Timestamp](
    _.value.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
  )
  
  implicit val timestampDecoder: Decoder[Timestamp] = Decoder.decodeString.emap { str =>
    try {
      Right(Timestamp(OffsetDateTime.parse(str, DateTimeFormatter.ISO_OFFSET_DATE_TIME)))
    } catch {
      case e: Exception => Left(s"Invalid timestamp: ${e.getMessage}")
    }
  }
  
  implicit val priceEncoder: Encoder[Price] = Encoder.encodeBigDecimal.contramap[Price](_.value)
  implicit val priceDecoder: Decoder[Price] = Decoder.decodeBigDecimal.map(Price(_))
  
  implicit val currencyEncoder: Encoder[Currency] = Encoder.encodeString.contramap[Currency](_.toString)
  implicit val currencyDecoder: Decoder[Currency] = Decoder.decodeString.emap { str =>
    try {
      Right(Currency.fromString(str))
    } catch {
      case _: MatchError => Left(s"Invalid currency: $str")
    }
  }
  
  implicit val ratePairEncoder: Encoder[Rate.Pair] = deriveEncoder[Rate.Pair]
  implicit val ratePairDecoder: Decoder[Rate.Pair] = deriveDecoder[Rate.Pair]
  
  implicit val rateEncoder: Encoder[Rate] = deriveEncoder[Rate]
  implicit val rateDecoder: Decoder[Rate] = deriveDecoder[Rate]
}
