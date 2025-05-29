package com.dwolla.tracing.smithy

import cats.*
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

    /**
     * Wraps the provided algebra with instrumentation logic that traces its inputs. This allows
     * parameters of the invoked operations to be appended to the current trace as annotations,
     * enhancing observability and debugging.
     *
     * This should be added to an algebra instance before [[SimpleAlgebraInstrumentation]], since
     * that will wrap this one, adding the new span for the operation.
     *
     * @param F           An implicit `Applicative[F]` used to combine multiple effects in `F[_]`
     * @param S           An implicit `Service[Alg]` instance that provides functionality to instrument
     *                    the algebra.
     * @param T           An implicit `Trace[F]` instance that provides tracing capabilities for the
     *                    effect type `F`.
     * @return A transformed algebra instance where all operations include input tracing as part of the instrumentation.
     */
    def withTracedInputs()
                        (implicit
                         F: Applicative[F],
                         S: Service[Alg],
                         T: Trace[F],
                        ): Alg[Kind1[F]#toKind5] =
      AlgebraInstrumentationWithInputs(alg)

    /**
     * Wraps the given algebra with tracing instrumentation for outputs.
     * Whenever an operation is invoked on the wrapped algebra, the return value
     * will be appended to the current trace as an annotation.
     *
     * This should be added to an algebra instance before [[SimpleAlgebraInstrumentation]], since
     * that will wrap this one, adding the new span for the operation.
     *
     * @param F           An implicit `Monad[F]` used to combine multiple effects in `F[_]`
     * @param S           An implicit `Service[Alg]` instance that provides functionality to instrument
     *                    the algebra.
     * @param T           An implicit `Trace[F]` instance that provides tracing capabilities for the
     *                    effect type `F`.
     */
    def withTracedOutputs()
                         (implicit
                          F: Monad[F],
                          S: Service[Alg],
                          T: Trace[F],
                         ): Alg[Kind1[F]#toKind5] =
      AlgebraInstrumentationWithOutputs(alg)
  }
}
