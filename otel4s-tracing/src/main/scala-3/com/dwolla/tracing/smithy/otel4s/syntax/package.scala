package com.dwolla.tracing.smithy.otel4s
package syntax

import cats.syntax.all.*
import cats.{FlatMap, Functor, Monad}
import org.typelevel.otel4s.trace.{SpanKind, TracerProvider}
import scala.annotation.targetName
import smithy4s.Service
import smithy4s.kinds.Kind1

extension [Alg[_[_, _, _, _, _]], F[_]](alg: Alg[Kind1[F]#toKind5]) {
  /** Every endpoint call runs in a new `Internal` child span. See [[SimpleAlgebraInstrumentation]]. */
  def withSimpleInstrumentation()
                               (using Functor[F], TracerProvider[F], Service[Alg]): F[Alg[Kind1[F]#toKind5]] =
    SimpleAlgebraInstrumentation(alg)

  /** Every endpoint call runs in a new child span of `spanKind`. See [[SimpleAlgebraInstrumentation]]. */
  def withSimpleInstrumentation(spanKind: SpanKind)
                               (using Functor[F], TracerProvider[F], Service[Alg]): F[Alg[Kind1[F]#toKind5]] =
    SimpleAlgebraInstrumentation(alg, spanKind)

  /** Records each call's input on the current span. Apply before `withSimpleInstrumentation`. See [[AlgebraInstrumentationWithInputs]]. */
  def withTracedInputs()
                      (using Monad[F], TracerProvider[F], Service[Alg]): F[Alg[Kind1[F]#toKind5]] =
    AlgebraInstrumentationWithInputs(alg)

  /** Records each successful call's output on the current span. Apply before `withSimpleInstrumentation`. See [[AlgebraInstrumentationWithOutputs]]. */
  def withTracedOutputs()
                       (using Monad[F], TracerProvider[F], Service[Alg]): F[Alg[Kind1[F]#toKind5]] =
    AlgebraInstrumentationWithOutputs(alg)
}

/**
 * The same syntax on an algebra inside `F`, so wrappers chain without `flatMap`.
 *
 * Each method has an explicit binary name, because it and its counterpart on `Alg` can erase to the same signature.
 */
extension [Alg[_[_, _, _, _, _]], F[_]](falg: F[Alg[Kind1[F]#toKind5]]) {
  @targetName("withSimpleInstrumentationOnEffectfulAlgebra")
  def withSimpleInstrumentation()
                               (using FlatMap[F], TracerProvider[F], Service[Alg]): F[Alg[Kind1[F]#toKind5]] =
    falg.flatMap(SimpleAlgebraInstrumentation(_))

  @targetName("withSimpleInstrumentationOfKindOnEffectfulAlgebra")
  def withSimpleInstrumentation(spanKind: SpanKind)
                               (using FlatMap[F], TracerProvider[F], Service[Alg]): F[Alg[Kind1[F]#toKind5]] =
    falg.flatMap(SimpleAlgebraInstrumentation(_, spanKind))

  @targetName("withTracedInputsOnEffectfulAlgebra")
  def withTracedInputs()
                      (using Monad[F], TracerProvider[F], Service[Alg]): F[Alg[Kind1[F]#toKind5]] =
    falg.flatMap(AlgebraInstrumentationWithInputs(_))

  @targetName("withTracedOutputsOnEffectfulAlgebra")
  def withTracedOutputs()
                       (using Monad[F], TracerProvider[F], Service[Alg]): F[Alg[Kind1[F]#toKind5]] =
    falg.flatMap(AlgebraInstrumentationWithOutputs(_))
}
