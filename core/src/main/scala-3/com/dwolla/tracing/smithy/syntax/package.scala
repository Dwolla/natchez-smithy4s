package com.dwolla.tracing.smithy
package syntax

import natchez.{Span, Trace}
import smithy4s.Service
import smithy4s.kinds.Kind1

extension [Alg[_[_, _, _, _, _]], F[_] : Trace](alg: Alg[Kind1[F]#toKind5]) {
  /**
   * Wraps an existing algebra implementation with default instrumentation to trace its operations.
   * This version uses default span options with a span kind of `Server`.
   *
   * @param `Service[Alg]` An implicit `Service[Alg]` instance used for deriving the necessary
   *                       functionality to instrument the algebra.
   * @return A new instance of the algebra with each of its endpoints wrapped in tracing logic
   *         using default span options.
   */
  def withSimpleInstrumentation()
                               (using Service[Alg]): Alg[Kind1[F]#toKind5] =
    withSimpleInstrumentation(Span.Options.Defaults.withSpanKind(Span.SpanKind.Server))

  /**
   * Wraps an existing algebra implementation with simple instrumentation to trace its operations.
   *
   * @param spanOptions Configuration options for creating and managing spans during tracing.
   * @param `Service[Alg]` An implicit `Service[Alg]` instance used for deriving the necessary
   *                       functionality to instrument the algebra.
   * @return A new instance of the algebra with each of its endpoints wrapped in tracing logic.
   */
  def withSimpleInstrumentation(spanOptions: Span.Options)
                               (using Service[Alg]): Alg[Kind1[F]#toKind5] =
    SimpleAlgebraInstrumentation(alg, spanOptions)
}
