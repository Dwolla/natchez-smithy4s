package com.dwolla.tracing.smithy.otel4s

import cats.syntax.all._
import cats.{FlatMap, Functor, Monad}
import org.typelevel.otel4s.trace.{SpanKind, TracerProvider}
import smithy4s._
import smithy4s.kinds._

package object syntax {
  implicit class Otel4sTraceAlgebraOps[Alg[_[_, _, _, _, _]], F[_]](val alg: Alg[Kind1[F]#toKind5]) extends AnyVal {
    /** Every endpoint call runs in a new `Internal` child span. See [[SimpleAlgebraInstrumentation]]. */
    def withSimpleInstrumentation()
                                 (implicit F: Functor[F], T: TracerProvider[F], S: Service[Alg]): F[Alg[Kind1[F]#toKind5]] =
      SimpleAlgebraInstrumentation(alg)

    /** Every endpoint call runs in a new child span of `spanKind`. See [[SimpleAlgebraInstrumentation]]. */
    def withSimpleInstrumentation(spanKind: SpanKind)
                                 (implicit F: Functor[F], T: TracerProvider[F], S: Service[Alg]): F[Alg[Kind1[F]#toKind5]] =
      SimpleAlgebraInstrumentation(alg, spanKind)

    /** Records each call's input on the current span. Apply before `withSimpleInstrumentation`. See [[AlgebraInstrumentationWithInputs]]. */
    def withTracedInputs()
                        (implicit F: Monad[F], T: TracerProvider[F], S: Service[Alg]): F[Alg[Kind1[F]#toKind5]] =
      AlgebraInstrumentationWithInputs(alg)

    /** Records each successful call's output on the current span. Apply before `withSimpleInstrumentation`. See [[AlgebraInstrumentationWithOutputs]]. */
    def withTracedOutputs()
                         (implicit F: Monad[F], T: TracerProvider[F], S: Service[Alg]): F[Alg[Kind1[F]#toKind5]] =
      AlgebraInstrumentationWithOutputs(alg)
  }

  /** The same syntax on an algebra inside `F`, so wrappers chain without `flatMap`. */
  implicit class Otel4sTraceEffectfulAlgebraOps[Alg[_[_, _, _, _, _]], F[_]](val falg: F[Alg[Kind1[F]#toKind5]]) extends AnyVal {
    def withSimpleInstrumentation()
                                 (implicit F: FlatMap[F], T: TracerProvider[F], S: Service[Alg]): F[Alg[Kind1[F]#toKind5]] =
      falg.flatMap(SimpleAlgebraInstrumentation(_))

    def withSimpleInstrumentation(spanKind: SpanKind)
                                 (implicit F: FlatMap[F], T: TracerProvider[F], S: Service[Alg]): F[Alg[Kind1[F]#toKind5]] =
      falg.flatMap(SimpleAlgebraInstrumentation(_, spanKind))

    def withTracedInputs()
                        (implicit F: Monad[F], T: TracerProvider[F], S: Service[Alg]): F[Alg[Kind1[F]#toKind5]] =
      falg.flatMap(AlgebraInstrumentationWithInputs(_))

    def withTracedOutputs()
                         (implicit F: Monad[F], T: TracerProvider[F], S: Service[Alg]): F[Alg[Kind1[F]#toKind5]] =
      falg.flatMap(AlgebraInstrumentationWithOutputs(_))
  }
}
