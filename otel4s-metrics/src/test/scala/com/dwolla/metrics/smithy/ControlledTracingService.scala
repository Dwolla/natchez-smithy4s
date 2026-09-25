package com.dwolla.metrics.smithy

import cats.effect.IO
import com.example.tracing.{TracingResponse, TracingService}

import scala.concurrent.duration.FiniteDuration

/**
 * A `TracingService` whose every operation takes `latency` (of virtual time, under `TestControl`)
 * and then completes with `result`, so tests can pin both the recorded duration and the outcome.
 */
class ControlledTracingService(latency: FiniteDuration, result: IO[TracingResponse]) extends TracingService[IO] {
  override def processRequest(id: String, value: Int, description: Option[String]): IO[TracingResponse] =
    IO.sleep(latency) >> result

  override def getStatus(): IO[TracingResponse] =
    IO.sleep(latency) >> result
}
