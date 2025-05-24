package com.dwolla.tracing.smithy

import smithy4s._
import smithy4s.kinds._
import natchez.{Span, Trace}

package object syntax {
  implicit class TraceAlgebraOps[Alg[_[_, _, _, _, _]], F[_]](val alg: Alg[Kind1[F]#toKind5]) extends AnyVal {
    /**
     * Wraps `alg` with simple instrumentation using default span options that
     * configure the span as a server kind. The instrumentation facilitates tracing
     * by wrapping each endpoint invocation in a new span named after the endpoint.
     *
     * @param S provides service-level operations for the `Alg` algebra.
     * @param T provides tracing capabilities for the effect type `F`.
     * @return Returns an instrumented version of the algebra `Alg` where endpoint
     *         invocations are traced with the default server span options.
     */
    def withSimpleInstrumentation()
                                 (implicit S: Service[Alg],
                                  T: Trace[F]): Alg[Kind1[F]#toKind5] =
      withSimpleInstrumentation(Span.Options.Defaults.withSpanKind(Span.SpanKind.Server))

    /**
     * Wraps the given algebra `alg: Alg[F]` with instrumentation that uses the provided `spanOptions`
     * for tracing operations. The instrumentation creates a new span for each endpoint invocation
     * based on the specified span options.
     *
     * @param spanOptions Configuration options for creating and managing spans during tracing.
     * @param S           An implicit `Service[Alg]` instance that provides functionality to instrument
     *                    the algebra.
     * @param T           An implicit `Trace[F]` instance that provides tracing capabilities for the
     *                    effect type `F`.
     * @return An instrumented version of the algebra `Alg[F]` where endpoint invocations are traced
     *         using the specified span options.
     */
    def withSimpleInstrumentation(spanOptions: Span.Options)
                                 (implicit S: Service[Alg],
                                  T: Trace[F]): Alg[Kind1[F]#toKind5] =
      SimpleAlgebraInstrumentation(alg, spanOptions)
  }
}
