package com.dwolla.tracing.smithy.otel4s

import cats.effect.IO
import cats.syntax.all.*
import org.typelevel.otel4s.sdk.testkit.trace.TracesTestkit
import org.typelevel.otel4s.sdk.trace.data.SpanData
import org.typelevel.otel4s.trace.TracerProvider

/** Runs code against a real, in-memory otel4s-sdk `TracerProvider` and returns what it recorded. */
trait SpanRecording {
  protected def resultAndSpans[A](f: TracerProvider[IO] => IO[A]): IO[(A, List[SpanData])] =
    TracesTestkit.inMemory[IO]().use { testkit =>
      f(testkit.tracerProvider).flatMap(a => testkit.finishedSpans.tupleLeft(a))
    }
}
