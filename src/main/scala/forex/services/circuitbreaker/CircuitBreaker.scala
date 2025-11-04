package forex.services.circuitbreaker

import cats.effect.{Concurrent, Timer}
import cats.effect.concurrent.Ref
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.applicativeError._
import org.slf4j.LoggerFactory
import scala.concurrent.duration._

sealed trait CircuitBreakerState
object CircuitBreakerState {
  case object Closed extends CircuitBreakerState
  case object Open extends CircuitBreakerState
  case object HalfOpen extends CircuitBreakerState
}

/**
  * Prevents from repeatedly calling failing downstream service, protecting the system from cascading failures.
  *
  * @param maxFailures
  * @param resetTimeout
  * @param halfOpenMaxCalls
  */
case class CircuitBreakerConfig(
    maxFailures: Int = 5,
    resetTimeout: FiniteDuration = 1.minute,
    halfOpenMaxCalls: Int = 3
)

// Prevents cascading failures by blocking requests when service is down
class CircuitBreaker[F[_]: Concurrent: Timer](
    config: CircuitBreakerConfig,
    stateRef: Ref[F, CircuitBreakerState],
    failureCountRef: Ref[F, Int],
    halfOpenCallsRef: Ref[F, Int]
) {

  private val logger = LoggerFactory.getLogger(getClass)

  // Protect operation with circuit breaker
  def protect[A](fa: F[A]): F[A] = {
    stateRef.get.flatMap {
      case CircuitBreakerState.Open =>
        Concurrent[F].raiseError(new Exception("circuit breaker is open - too many failures"))

      case CircuitBreakerState.HalfOpen =>
        halfOpenCallsRef.get.flatMap { calls =>
          if (calls >= config.halfOpenMaxCalls) {
            Concurrent[F].raiseError(new Exception("circuit breaker half_open - max test calls reached"))
          } else {
            halfOpenCallsRef.update(_ + 1).flatMap { _ =>
              fa.flatMap { result =>
                onSuccess.map(_ => result)
              }.handleErrorWith { error =>
                onFailure.flatMap(_ => Concurrent[F].raiseError(error))
              }
            }
          }
        }

      case CircuitBreakerState.Closed =>
        fa.flatMap { result =>
          onSuccess.map(_ => result)
        }.handleErrorWith { error =>
          onFailure.flatMap(_ => Concurrent[F].raiseError(error))
        }
    }
  }

  // Handle successful operation
  private def onSuccess: F[Unit] = {
    stateRef.get.flatMap {
      case CircuitBreakerState.HalfOpen =>
        stateRef.set(CircuitBreakerState.Closed).flatMap { _ =>
          failureCountRef.set(0).flatMap { _ =>
            halfOpenCallsRef.set(0).flatMap { _ =>
              Concurrent[F].delay(logger.info("circuit breaker: half_open → closed (recovery successful)"))
            }
          }
        }
      case CircuitBreakerState.Closed =>
        failureCountRef.set(0).flatMap { _ =>
          Concurrent[F].delay(logger.debug("circuit breaker: request succeeded"))
        }
      case _ =>
        Concurrent[F].unit
    }
  }

  // Handle failed operation
  private def onFailure: F[Unit] = {
    stateRef.get.flatMap {
      case CircuitBreakerState.Closed =>
        failureCountRef.updateAndGet(_ + 1).flatMap { count =>
          if (count >= config.maxFailures) {
            stateRef.set(CircuitBreakerState.Open).flatMap { _ =>
              Concurrent[F].delay(logger.error(s"circuit breaker: closed → open (failures: $count/${config.maxFailures})")).flatMap { _ =>
                scheduleReset
              }
            }
          } else {
            Concurrent[F].delay(logger.warn(s"circuit breaker: failure recorded ($count/${config.maxFailures})"))
          }
        }

      case CircuitBreakerState.HalfOpen =>
        stateRef.set(CircuitBreakerState.Open).flatMap { _ =>
          halfOpenCallsRef.set(0).flatMap { _ =>
            Concurrent[F].delay(logger.warn("circuit breaker: half_open → open (test failed)")).flatMap { _ =>
              scheduleReset
            }
          }
        }

      case CircuitBreakerState.Open =>
        Concurrent[F].unit
    }
  }

  // Schedule circuit breaker reset
  private def scheduleReset: F[Unit] = {
    Concurrent[F].start(
      Timer[F].sleep(config.resetTimeout).flatMap { _ =>
        stateRef.set(CircuitBreakerState.HalfOpen).flatMap { _ =>
          halfOpenCallsRef.set(0).flatMap { _ =>
            Concurrent[F].delay(
              logger.info(s"circuit breaker: open → half_open (attempting recovery after ${config.resetTimeout.toSeconds}s)")
            )
          }
        }
      }
    ).void
  }
}

object CircuitBreaker {
  // Create a new circuit breaker instance
  def create[F[_]: Concurrent: Timer](
      config: CircuitBreakerConfig
  ): F[CircuitBreaker[F]] = {
    for {
      stateRef <- Ref.of[F, CircuitBreakerState](CircuitBreakerState.Closed)
      failureCountRef <- Ref.of[F, Int](0)
      halfOpenCallsRef <- Ref.of[F, Int](0)
    } yield new CircuitBreaker[F](config, stateRef, failureCountRef, halfOpenCallsRef)
  }

  // Create a disabled circuit breaker that always succeeds
  def disabled[F[_]: Concurrent: Timer]: F[CircuitBreaker[F]] = {
    val noopConfig = CircuitBreakerConfig(maxFailures = Int.MaxValue, resetTimeout = 1.hour)
    create[F](noopConfig)
  }
}
