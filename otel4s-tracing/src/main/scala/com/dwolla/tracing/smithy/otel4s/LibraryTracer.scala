package com.dwolla.tracing.smithy.otel4s

import org.typelevel.otel4s.trace.{Tracer, TracerProvider}

/**
 * This library's own `Tracer`, obtained from the application's `TracerProvider` under the library's versioned
 * instrumentation scope. Tracers from one provider share its context, so a span opened by the application's own
 * tracer (or an HTTP middleware) still parents the spans this one opens.
 */
private[otel4s] object LibraryTracer {
  val ScopeName: String = "com.dwolla.tracing.smithy.otel4s"

  def apply[F[_]: TracerProvider]: F[Tracer[F]] =
    TracerProvider[F].tracer(ScopeName).withVersion(BuildInfo.version).get
}
