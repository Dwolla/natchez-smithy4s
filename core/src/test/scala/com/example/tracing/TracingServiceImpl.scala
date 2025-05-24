package com.example.tracing

import cats._
import cats.syntax.all._

object TracingServiceImpl {
  def apply[F[_]: ApplicativeThrow](maybeThrowable: Option[Throwable]): TracingService[F] = new TracingServiceImpl[F](maybeThrowable)
}

/**
 * Implementation of the TracingService algebra.
 * This is a simple implementation that we can use in our tests.
 */
class TracingServiceImpl[F[_]: ApplicativeThrow](maybeThrowable: Option[Throwable]) extends TracingService[F] {

  /**
   * Process a request and return a response.
   * This implementation simply transforms the input into an output.
   */
  override def processRequest(id: String,
                              value: Int,
                              description: Option[String] = None): F[TracingResponse] =
    maybeThrowable.fold {
      TracingResponse(
        id = s"response-$id",
        result = value * 2,
        status = description.map(d => s"Processed: $d")
      ).pure[F]
    }(_.raiseError)

  /**
   * Get the status of the service.
   * This implementation returns a fixed response.
   */
  override def getStatus(): F[TracingResponse] =
    maybeThrowable.fold {
      TracingResponse(
        id = "status-response",
        result = 200,
        status = "Service is running".some
      ).pure[F]
    }(_.raiseError)
}
