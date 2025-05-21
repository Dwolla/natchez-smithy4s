package com.dwolla.tracing.smithy

import smithy4s._
import smithy4s.kinds._
import natchez.{Span, Trace}

object SimpleAlgebraInstrumentation {
  /**
   * Wraps an existing algebra implementation (`alg`) with instrumentation to trace its operations.
   *
   * @param alg         Original algebra implementation to be instrumented.
   * @param spanOptions Configuration options for creating and managing spans during tracing.
   * @param S           An implicit `Service` instance used for deriving the necessary functionality
   *                    to instrument the algebra.
   * @return A new instance of the algebra, with each of its endpoints wrapped in tracing logic.
   */
  def apply[Alg[_[_, _, _, _, _]], F[_] : Trace](alg: Alg[Kind1[F]#toKind5],
                                                 spanOptions: Span.Options)
                                                (implicit S: Service[Alg]): Alg[Kind1[F]#toKind5] =
    S.impl(new S.FunctorEndpointCompiler[F] {
      override def apply[I, E, O, SI, SO](fa: S.Endpoint[I, E, O, SI, SO]): I => F[O] = { (i: I) =>
        Trace[F].span(s"${S.id.name}.${fa.name}", spanOptions) {
          S.toPolyFunction(alg).apply(fa.wrap(i))
        }
      }
    })

  /**
   * Wraps an existing algebra implementation (`alg`) with default instrumentation to trace its operations.
   *
   * @param alg Original algebra implementation to be instrumented.
   * @param S   An implicit `Service` instance used for deriving the necessary functionality
   *            to instrument the algebra.
   * @return A new instance of the algebra, with each of its endpoints wrapped in tracing logic using
   *         default span options with a span kind of `Server`.
   */
  def apply[Alg[_[_, _, _, _, _]], F[_] : Trace](alg: Alg[Kind1[F]#toKind5])
                                                (implicit S: Service[Alg]): Alg[Kind1[F]#toKind5] =
    SimpleAlgebraInstrumentation(alg, Span.Options.Defaults.withSpanKind(Span.SpanKind.Server))
}
