package forex.services.rates.interpreters

import cats.effect.{Concurrent, Timer}
import cats.syntax.applicativeError._
import cats.syntax.either._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.show._
import cats.syntax.traverse._
import cats.instances.list._
import dev.profunktor.redis4cats.RedisCommands
import forex.config.{OneFrameConfig, RedisConfig}
import forex.domain.{Currency, Price, Rate, Timestamp}
import forex.logging.CacheLogger
import forex.services.apitracker.OneFrameApiTracker
import forex.services.cassandra.CassandraRatesRepository
import forex.services.circuitbreaker.CircuitBreaker
import forex.services.coalescer.RequestCoalescer
import forex.services.rates.RatesRepository
import forex.services.rates.errors._
import io.circe.{Decoder, HCursor}
import io.circe.syntax._
import io.circe.parser._
import org.http4s.{Header, Headers, Method, Request, Uri}
import org.http4s.circe.CirceEntityDecoder._
import org.http4s.client.Client
import org.typelevel.ci.CIString
import org.slf4j.LoggerFactory
import scala.concurrent.duration._

// Response from OneFrame API
case class OneFrameResponse(
    from: String,
    to: String,
    bid: BigDecimal,
    ask: BigDecimal,
    price: BigDecimal,
    time_stamp: String
)

object OneFrameResponse {
  implicit val decoder: Decoder[OneFrameResponse] = (c: HCursor) =>
    for {
      from <- c.downField("from").as[String]
      to <- c.downField("to").as[String]
      bid <- c.downField("bid").as[BigDecimal]
      ask <- c.downField("ask").as[BigDecimal]
      price <- c.downField("price").as[BigDecimal]
      timeStamp <- c.downField("time_stamp").as[String]
    } yield OneFrameResponse(from, to, bid, ask, price, timeStamp)
}

// Fetches exchange rates from OneFrame API with caching and resilience
class OneFrameClient[F[_]: Concurrent: Timer](
    httpClient: Client[F],
    oneFrameConfig: OneFrameConfig,
    redisConfig: RedisConfig,
    redis: RedisCommands[F, String, String],
    circuitBreaker: CircuitBreaker[F],
    coalescer: RequestCoalescer[F, String, Rate],
    cassandraRepo: Option[CassandraRatesRepository[F]] = None,
    apiTracker: Option[OneFrameApiTracker[F]] = None,
    rateLimiter: Option[forex.services.ratelimiter.RateLimiter[F]] = None
) extends RatesRepository[F] {

  private val logger = LoggerFactory.getLogger(getClass)
  // Fetch all rates from API and store them in cache
  def fetchAndCache: F[Unit] = {
    val startNs = System.nanoTime()
    logger.info("fetching rates from oneframe api")
    
    fetchAllRates.flatMap { rates =>
      logger.info(s"caching ${rates.length} rates in redis (ttl: ${redisConfig.ttl.toMinutes}m)")
      // write to redis with ttl
      rates.traverse { rate =>
        val key = s"forex:rate:${pairKey(rate.pair)}"
        val value = rateToJson(rate)
        redis.setEx(key, value, redisConfig.ttl)
      }.flatMap { _ =>
        val durationMs = (System.nanoTime() - startNs) / 1000000L
        // Start Cassandra write asynchronously without blocking
        (cassandraRepo match {
          case Some(repo) =>
            Concurrent[F].start(
              repo.storeRates(rates).attempt.flatMap {
                case Right(_) => Concurrent[F].delay(logger.debug(s"cassandra batch write completed: ${rates.length} rates"))
                case Left(err) => Concurrent[F].delay(logger.error(s"cassandra batch write failed: ${err.getMessage}"))
              }
            ).void
          case None => Concurrent[F].unit
        }).flatMap { _ =>
          CacheLogger.logPut[F]("redis", "batch", Some(redisConfig.ttl.toMillis), Map("count" -> rates.length.toString, "duration_ms" -> durationMs.toString)).flatMap { _ =>
            Concurrent[F].delay(
              logger.info(s"cache updated: ${rates.length} rates stored in ${durationMs}ms")
            )
          }
        }
      }
    }
  }

  private def fetchAllRates: F[List[Rate]] = {
    val quotaCheck: F[Unit] = apiTracker match {
      case Some(tracker) => 
        tracker.hasQuotaAvailable.flatMap { available =>
          if (!available) {
            tracker.getQuotaStatus.flatMap { status =>
              Concurrent[F].raiseError[Unit](new Exception(
                s"OneFrame API quota exceeded: ${status.used}/${status.limit} calls used today"
              ))
            }
          } else Concurrent[F].unit
        }
      case None => Concurrent[F].unit
    }
    
    quotaCheck.flatMap { _ =>
      val startNs = System.nanoTime()
      
      val topCurrencies: List[Currency] = List(
        Currency.USD, Currency.EUR, Currency.GBP, Currency.JPY,
        Currency.CHF, Currency.CAD, Currency.AUD, Currency.NZD,
        Currency.CNY, Currency.HKD, Currency.SGD, Currency.KRW,
        Currency.INR, Currency.THB, Currency.IDR, Currency.MYR,
        Currency.AED, Currency.PHP, 
        // Currency.TWD, Currency.PKR, Currency.BDT,
        // Currency.SEK, Currency.NOK, Currency.DKK, Currency.PLN,
        // Currency.CZK, Currency.HUF, Currency.RON, Currency.RUB,
        // Currency.TRY, Currency.UAH, Currency.MXN, Currency.BRL, 
        // Currency.ARS, Currency.CLP, Currency.COP, Currency.PEN, 
        // Currency.AED, Currency.SAR, Currency.ILS, Currency.ZAR,
        // Currency.EGP, Currency.NGN
      )
      
      // prepare query string
      val pairStrings = for {
        from <- topCurrencies
        to <- topCurrencies
        if from != to
      } yield s"${from.show}${to.show}"
      
      val queryParams = pairStrings.map(p => s"pair=$p").mkString("&")
      Uri.fromString(s"${oneFrameConfig.uri}/rates?$queryParams") match {
        case Left(parseError) =>
          Concurrent[F].raiseError(new Exception(s"Invalid URI: ${parseError.message}"))
        
        case Right(uri) =>
          val request = Request[F](
            method = Method.GET,
            uri = uri,
            headers = Headers(Header.Raw(CIString("token"), oneFrameConfig.token))
          )
          circuitBreaker.protect(
            retryWithBackoff(
              httpClient.expect[List[OneFrameResponse]](request).flatMap { responses =>
              responses.traverse[F, Rate] { response =>
                Currency.fromStringOpt(response.from) match {
                  case Some(from) =>
                    Currency.fromStringOpt(response.to) match {
                      case Some(to) =>
                        Concurrent[F].delay {
                          Rate(
                            pair = Rate.Pair(from, to),
                            bid = Price(response.bid),
                            ask = Price(response.ask),
                            price = Price(response.price),
                            timestamp = Timestamp.now,
                            last_updated = response.time_stamp
                          )
                        }
                      case None =>
                        Concurrent[F].raiseError[Rate](new Exception(s"Invalid currency: ${response.to}"))
                    }
                  case None =>
                    Concurrent[F].raiseError[Rate](new Exception(s"Invalid currency: ${response.from}"))
                }
              }
              },
              oneFrameConfig.retry.maxAttempts,
              oneFrameConfig.retry.delay
            )
          ).flatMap { rates =>
            val durationMs = (System.nanoTime() - startNs) / 1000000L
            apiTracker match {
              case Some(tracker) => 
                tracker.trackCall("/rates", pairStrings.length, success = true, durationMs = durationMs).map(_ => rates)
              case None => Concurrent[F].pure(rates)
            }
          }.handleErrorWith { error =>
            val durationMs = (System.nanoTime() - startNs) / 1000000L
            apiTracker match {
              case Some(tracker) =>
                tracker.trackCall("/rates", pairStrings.length, success = false, Some(error.getMessage), durationMs)
                  .flatMap(_ => Concurrent[F].raiseError(error))
              case None => Concurrent[F].raiseError(error)
            }
          }
      }
    }
  }

  // Get exchange rate for a currency pair from cache or API
  override def get(pair: Rate.Pair, clientId: Option[String] = None): F[Error Either Rate] = {
    val key = s"forex:rate:${pairKey(pair)}"
    val pairStr = pairKey(pair)
    val startTime = System.currentTimeMillis()
    
    redis.get(key).flatMap {
      case Some(json) =>
        jsonToRate(json) match {
          case Right(rate) =>
            val duration = System.currentTimeMillis() - startTime
            CacheLogger.logHit[F]("redis", pairStr, Some(duration)).map { _ =>
              logger.debug(s"cache hit: $pairStr (${duration}ms)")
              rate.asRight[Error]
            }
          case Left(_) =>
            Concurrent[F].delay(logger.warn(s"cache parse error for $pairStr, fetching from oneframe")).flatMap { _ =>
              coalescer.coalesce(pairStr)(fetchSingleRate(pair, clientId)).flatMap { rate =>
                val value = rateToJson(rate)
            redis.setEx(key, value, redisConfig.ttl).flatMap { _ =>
                  val cassandraWrite = cassandraRepo match {
                    case Some(repo) =>
                      Concurrent[F].start(
                        repo.storeRate(rate).attempt.flatMap {
                          case Right(_) => Concurrent[F].unit
                          case Left(err) => Concurrent[F].delay(logger.error(s"cassandra write failed for $pairStr: ${err.getMessage}"))
                        }
                      ).void
                    case None => Concurrent[F].unit
                  }
                  
                  cassandraWrite.flatMap(_ =>
                    CacheLogger.logPut[F]("redis", pairStr, Some(redisConfig.ttl.toMillis), Map("source" -> "parse_error")).map { _ =>
                      logger.info(s"cache restored: $pairStr (after parse error)")
                      rate.asRight[Error]
                    }
                  )
                }
              }.handleErrorWith { error =>
                Concurrent[F].delay(
                  logger.error(s"failed to fetch $pairStr after parse error: ${error.getMessage}")
                ).map(_ => Error.OneFrameLookupFailed(error.getMessage).asLeft[Rate])
              }
            }
        }
      case None =>
        CacheLogger.logMiss[F]("redis", pairStr).flatMap { _ =>
          Concurrent[F].delay(
            logger.debug(s"cache miss: $pairStr, fetching from oneframe (coalesced)")
          ).flatMap(_ => 
            // Use coalescer to prevent multiple concurrent requests for same pair
            coalescer.coalesce(pairStr)(fetchSingleRate(pair, clientId)).flatMap { rate =>
              val key = s"forex:rate:$pairStr"
              val value = rateToJson(rate)
              redis.setEx(key, value, redisConfig.ttl).flatMap { _ =>
                val cassandraWrite = cassandraRepo match {
                  case Some(repo) =>
                    Concurrent[F].start(
                      repo.storeRate(rate).attempt.flatMap {
                        case Right(_) => Concurrent[F].unit
                        case Left(err) => Concurrent[F].delay(logger.error(s"cassandra write failed for $pairStr: ${err.getMessage}"))
                      }
                    ).void
                  case None => Concurrent[F].unit
                }
                
                cassandraWrite.flatMap(_ =>
                  CacheLogger.logPut[F]("redis", pairStr, Some(redisConfig.ttl.toMillis), Map("source" -> "coalesced")).map { _ =>
                    logger.info(s"cache stored: $pairStr (coalesced fetch)")
                    rate.asRight[Error]
                  }
                )
              }
            }.handleErrorWith { error =>
              Concurrent[F].delay(
                logger.warn(s"failed to fetch $pairStr: ${error.getMessage}, batch scheduler will retry")
              ).map(_ => Error.OneFrameLookupFailed(s"Service temporarily unavailable. ${error.getMessage}").asLeft[Rate])
            }
          )
        }
    }
  }

  // Fetch a single currency pair rate from OneFrame API
  private def fetchSingleRate(pair: Rate.Pair, clientId: Option[String]): F[Rate] = {
    val pairParam = s"${pair.from.show}${pair.to.show}"
    val startNs = System.nanoTime()
    val rateLimitCheck: F[Unit] = rateLimiter match {
      case Some(limiter) =>
        val rateClientId = clientId.getOrElse("unknown")
        limiter.isAllowed(rateClientId).flatMap { allowed =>
          if (!allowed) {
            Concurrent[F].raiseError[Unit](new Exception(
              s"Rate limit exceeded for client: $rateClientId. Please try again later."
            ))
          } else Concurrent[F].unit
        }
      case None => Concurrent[F].unit
    }
    
    val quotaCheck: F[Unit] = apiTracker match {
      case Some(tracker) => 
        tracker.hasQuotaAvailable.flatMap { available =>
          if (!available) {
            tracker.getQuotaStatus.flatMap { status =>
              Concurrent[F].raiseError[Unit](new Exception(
                s"OneFrame API quota exceeded: ${status.used}/${status.limit} calls used today"
              ))
            }
          } else Concurrent[F].unit
        }
      case None => Concurrent[F].unit
    }
    
    rateLimitCheck.flatMap { _ =>
      quotaCheck.flatMap { _ =>
      Uri.fromString(s"${oneFrameConfig.uri}/rates?pair=$pairParam") match {
        case Left(parseError) =>
          Concurrent[F].raiseError(new Exception(s"Invalid URI: ${parseError.message}"))
        
        case Right(uri) =>
          val request = Request[F](
            method = Method.GET,
            uri = uri,
            headers = Headers(Header.Raw(CIString("token"), oneFrameConfig.token))
          )

          circuitBreaker.protect(
            retryWithBackoff(
              httpClient.expect[List[OneFrameResponse]](request).flatMap {
                case response :: _ =>
                Concurrent[F].delay {
                  Rate(
                    pair = pair,
                    bid = Price(response.bid),
                    ask = Price(response.ask),
                    price = Price(response.price),
                    timestamp = Timestamp.now,
                    last_updated = response.time_stamp
                  )
                }
                case Nil =>
                  Concurrent[F].raiseError[Rate](new Exception(s"No rate found for $pairParam"))
              },
              oneFrameConfig.retry.maxAttempts,
              oneFrameConfig.retry.delay
            )
          ).flatMap { rate =>
            val durationMs = (System.nanoTime() - startNs) / 1000000L
            apiTracker match {
              case Some(tracker) => 
                tracker.trackCall(s"/rates?pair=$pairParam", 1, success = true, durationMs = durationMs).map(_ => rate)
              case None => Concurrent[F].pure(rate)
            }
          }.handleErrorWith { error =>
            val durationMs = (System.nanoTime() - startNs) / 1000000L
            apiTracker match {
              case Some(tracker) =>
                tracker.trackCall(s"/rates?pair=$pairParam", 1, success = false, Some(error.getMessage), durationMs)
                  .flatMap(_ => Concurrent[F].raiseError(error))
              case None => Concurrent[F].raiseError(error)
            }
          }
      }
      }
    }
  }

  // Create cache key for a currency pair
  private def pairKey(pair: Rate.Pair): String = s"${pair.from.show}${pair.to.show}"

  // Convert rate to JSON string
  private def rateToJson(rate: Rate): String = {
    import forex.domain.codecs._
    rate.asJson.noSpaces
  }

  // Parse JSON string into rate object
  private def jsonToRate(json: String): Either[Throwable, Rate] = {
    import forex.domain.codecs._
    decode[Rate](json).left.map(e => new Exception(e.getMessage))
  }

  // Retry failed API calls with delay between attempts
  private def retryWithBackoff[A](fa: F[A], maxAttempts: Int, delay: FiniteDuration, attempt: Int = 1): F[A] =
    fa.handleErrorWith { error =>
      if (attempt < maxAttempts) {
        Concurrent[F].delay(
          logger.warn(s"oneframe request failed (attempt $attempt/$maxAttempts), retrying in ${delay.toMillis}ms, error: ${Option(error.getMessage).getOrElse(error.getClass.getSimpleName)}")
        ).flatMap(_ =>
          Timer[F].sleep(delay).flatMap(_ => retryWithBackoff(fa, maxAttempts, delay, attempt + 1))
        )
      } else {
        Concurrent[F].delay(
          logger.error(s"oneframe request failed after $maxAttempts attempts, error: ${Option(error.getMessage).getOrElse(error.getClass.getSimpleName)}")
        ).flatMap(_ => Concurrent[F].raiseError(error))
      }
    }
}
