package com.dwolla.tracing.smithy.otel4s

import cats.Monad
import cats.syntax.all.*
import org.typelevel.otel4s.trace.Tracer
import org.typelevel.otel4s.{AnyValue, Attribute}

/**
 * Adds one attribute to the *current* span. `value` is by-name and evaluated only if that span is recording, so a
 * noop tracer, an unsampled span, or the absence of any span costs nothing and can't fail the call. An empty value
 * (for example a `Unit` input) records nothing, as in otel4s-tagless.
 */
private[otel4s] object CurrentSpanRecording {
  def record[F[_] : Monad](tracer: Tracer[F], key: String, value: => AnyValue): F[Unit] =
    tracer.currentSpanOrNoop.flatMap { span =>
      span.isRecording.ifM(
        {
          val encoded: AnyValue = value
          // `.backend` deliberately: Span#addAttributes is a macro on Scala 2 and inline on Scala 3
          if (encoded == AnyValue.empty) ().pure[F] else span.backend.addAttributes(List(Attribute[AnyValue](key, encoded)))
        },
        ().pure[F],
      )
    }
}
