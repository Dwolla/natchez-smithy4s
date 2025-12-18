package com.dwolla.tracing.smithy
package syntax

import cats.*
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
    SimpleAlgebraInstrumentation(alg)

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

  /**
   * Wraps the provided algebra with instrumentation logic that traces its inputs. This allows
   * parameters of the invoked operations to be appended to the current trace as annotations,
   * enhancing observability and debugging.
   *
   * This should be added to an algebra instance before [[SimpleAlgebraInstrumentation]], since
   * that will wrap this one, adding the new span for the operation.
   *
   * @param `Applicative[F]` an instance of `Applicative[F]`
   * @param `Service[Alg]` An implicit `Service[Alg]` instance used for deriving the necessary
   *                       functionality to instrument the algebra.
   * @return A transformed algebra instance where all operations include input tracing as part of the instrumentation.
   */
  def withTracedInputs()
                      (using Applicative[F], Service[Alg]): Alg[Kind1[F]#toKind5] =
    AlgebraInstrumentationWithInputs(alg)

  /**
   * Wraps the given algebra with tracing instrumentation for outputs.
   * Whenever an operation is invoked on the wrapped algebra, the return value
   * will be appended to the current trace as an annotation.
   *
   * This should be added to an algebra instance before [[SimpleAlgebraInstrumentation]], since
   * that will wrap this one, adding the new span for the operation.
   *
   * @param `Monad[F]` An instance of `Monad[F]`
   * @param `Service[Alg]` An implicit `Service[Alg]` instance used for deriving the necessary
   *                       functionality to instrument the algebra.
   */
  def withTracedOutputs()
                      (using Monad[F], Service[Alg]): Alg[Kind1[F]#toKind5] =
    AlgebraInstrumentationWithOutputs(alg)
}
