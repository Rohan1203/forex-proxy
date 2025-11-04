package forex.services.coalescer

import cats.effect.Concurrent
import cats.effect.concurrent.{Deferred, Ref}
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.applicativeError._

/**
  * Merges concurrent requests for same key to avoid duplicate operations
  *
  * @param pendingRef
  */
class RequestCoalescer[F[_]: Concurrent, K, V](
    pendingRef: Ref[F, Map[K, Deferred[F, Either[Throwable, V]]]]
) {

  // Wait for in-flight request or execute new one
  def coalesce(key: K)(fetch: F[V]): F[V] = {
    pendingRef.get.flatMap { pending =>
      pending.get(key) match {
        case Some(deferred) =>
          // Request already in progress, wait for it
          deferred.get.flatMap {
            case Right(value) => Concurrent[F].pure(value)
            case Left(error) => Concurrent[F].raiseError(error)
          }

        case None =>
          // Start new request
          for {
            deferred <- Deferred[F, Either[Throwable, V]]
            _ <- pendingRef.update(_ + (key -> deferred))
            result <- fetch.attempt
            _ <- deferred.complete(result)
            _ <- pendingRef.update(_ - key)
            value <- result match {
              case Right(v) => Concurrent[F].pure(v)
              case Left(e) => Concurrent[F].raiseError(e)
            }
          } yield value
      }
    }
  }
}

object RequestCoalescer {
  // Create a new request coalescer
  def create[F[_]: Concurrent, K, V]: F[RequestCoalescer[F, K, V]] = {
    Ref.of[F, Map[K, Deferred[F, Either[Throwable, V]]]](Map.empty).map { ref =>
      new RequestCoalescer[F, K, V](ref)
    }
  }

  // Create a disabled coalescer that doesn't merge requests
  def disabled[F[_]: Concurrent, K, V]: F[RequestCoalescer[F, K, V]] = {
    Concurrent[F].pure(new RequestCoalescer[F, K, V](
      null // Won't be used since we override colesce
    ) {
      override def coalesce(key: K)(fetch: F[V]): F[V] = fetch
    })
  }
}
