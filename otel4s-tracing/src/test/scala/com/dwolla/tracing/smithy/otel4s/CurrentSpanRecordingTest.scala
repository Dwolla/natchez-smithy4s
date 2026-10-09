package com.dwolla.tracing.smithy.otel4s

import cats.effect.IO
import munit.CatsEffectSuite
import org.typelevel.otel4s.trace.Tracer
import org.typelevel.otel4s.{AnyValue, Attribute, Attributes}

/** Recording on the current span costs nothing, and can't fail the call, unless that span is actually recording. */
class CurrentSpanRecordingTest extends CatsEffectSuite with SpanRecording {
  private def neverEncoded: AnyValue = throw new AssertionError("encoded a value for a span that isn't recording")

  test("under a noop tracer, the value is never encoded") {
    CurrentSpanRecording.record(Tracer.noop[IO], "key", neverEncoded)
  }

  test("with no current span, the value is never encoded") {
    resultAndSpans { tracerProvider =>
      tracerProvider.get("test").flatMap(CurrentSpanRecording.record(_, "key", neverEncoded))
    }.map { case (_, spans) => assertEquals(spans, Nil) }
  }

  test("on a recording span, the value is added under the key") {
    resultAndSpans { tracerProvider =>
      tracerProvider.get("test").flatMap { tracer =>
        tracer.span("parent").surround(CurrentSpanRecording.record(tracer, "key", AnyValue.string("v")))
      }
    }.map { case (_, spans) =>
      assertEquals(spans.map(_.attributes.elements), List(Attributes(Attribute[AnyValue]("key", AnyValue.string("v")))))
    }
  }

  test("an empty value records no attribute") {
    resultAndSpans { tracerProvider =>
      tracerProvider.get("test").flatMap { tracer =>
        tracer.span("parent").surround(CurrentSpanRecording.record(tracer, "key", AnyValue.empty))
      }
    }.map { case (_, spans) => assertEquals(spans.map(_.attributes.elements), List(Attributes.empty)) }
  }
}
