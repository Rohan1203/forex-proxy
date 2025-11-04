package forex.config

import scala.concurrent.duration.FiniteDuration

case class ApplicationConfig(
    version: String,
    name: String,
    http: HttpConfig,
    oneframe: OneFrameConfig,
    batch: BatchConfig,
    redis: RedisConfig,
    circuitBreaker: CircuitBreakerConfig,
    rateLimiter: RateLimiterConfig,
    coalescer: CoalescerConfig,
    cassandra: CassandraConfig
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
    retry: RetryConfig,
    healthCheckInterval: FiniteDuration
)

case class RetryConfig(
    maxAttempts: Int,
    delay: FiniteDuration
)

case class BatchConfig(
    enabled: Boolean,
    interval: FiniteDuration
)

case class RedisConfig(
    host: String,
    port: Int,
    database: Int,
    ttl: FiniteDuration
)

case class CircuitBreakerConfig(
    enabled: Boolean,
    maxFailures: Int,
    resetTimeout: FiniteDuration
)

case class RateLimiterConfig(
    enabled: Boolean,
    maxRequests: Int,
    window: FiniteDuration,
    whitelist: List[String] = List.empty
)

case class CoalescerConfig(
    enabled: Boolean
)

case class CassandraConfig(
    contactPoints: List[String],
    port: Int,
    keyspace: String,
    datacenter: String
)