package com.dwolla.tracing.smithy.otel4s

import cats.effect.IO
import com.example.tracing.{TracingResponse, TracingService}

/** A `TracingService` whose every operation completes with `result`: a value, an error, or cancellation. */
class FixedTracingService(result: IO[TracingResponse]) extends TracingService[IO] {
  override def processRequest(id: String, value: Int, description: Option[String]): IO[TracingResponse] = result
  override def getStatus(): IO[TracingResponse] = result
}
