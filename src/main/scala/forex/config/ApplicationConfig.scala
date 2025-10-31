package forex.config

import scala.concurrent.duration.FiniteDuration

case class ApplicationConfig(
    version: String,
    name: String,
    http: HttpConfig,
    oneframe: OneFrameConfig
)

case class HttpConfig(
    host: String,
    port: Int,
    timeout: FiniteDuration
)

case class OneFrameConfig(
    uri: String,
    token: String,
    timeout: FiniteDuration
)
