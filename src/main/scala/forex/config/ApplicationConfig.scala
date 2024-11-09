package forex.config

import scala.concurrent.duration.FiniteDuration

case class ApplicationConfig(
    http: HttpConfig,
    oneframe: OneFrameConfig,
    cache: Cache,
)

case class HttpConfig(
    host: String,
    port: Int,
    timeout: FiniteDuration
)

case class OneFrameConfig(
     uri: String,
     token: String,
     timeout: FiniteDuration,
     retry: Int,
     delay: FiniteDuration
)

case class Cache(
     uri: String,
     expiry: Int,
     cleanup: Int
)

