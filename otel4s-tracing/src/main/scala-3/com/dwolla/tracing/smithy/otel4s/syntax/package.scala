package com.dwolla.tracing.smithy.otel4s
package syntax

import cats.syntax.all.*
import cats.{FlatMap, Functor}
import org.typelevel.otel4s.trace.{SpanKind, TracerProvider}
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
}

/** The same syntax on an algebra inside `F`, so wrappers chain without `flatMap`. */
extension [Alg[_[_, _, _, _, _]], F[_]](falg: F[Alg[Kind1[F]#toKind5]]) {
  def withSimpleInstrumentation()
                               (using FlatMap[F], TracerProvider[F], Service[Alg]): F[Alg[Kind1[F]#toKind5]] =
    falg.flatMap(SimpleAlgebraInstrumentation(_))

  def withSimpleInstrumentation(spanKind: SpanKind)
                               (using FlatMap[F], TracerProvider[F], Service[Alg]): F[Alg[Kind1[F]#toKind5]] =
    falg.flatMap(SimpleAlgebraInstrumentation(_, spanKind))
}
