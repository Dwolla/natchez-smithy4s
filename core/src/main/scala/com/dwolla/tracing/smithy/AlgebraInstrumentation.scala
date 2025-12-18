package com.dwolla.tracing.smithy

import cats.*
import cats.syntax.all.*
import smithy4s.*
import smithy4s.kinds.*
import natchez.{Span, Trace, TraceableValue}

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
                                                (implicit S: Service[Alg]): Alg[Kind1[F]#toKind5] = {
    val algebraAsPolyFunction = S.toPolyFunction(alg)

    S.impl(new S.FunctorEndpointCompiler[F] {
      override def apply[I, E, O, SI, SO](fa: S.Endpoint[I, E, O, SI, SO]): I => F[O] = { (i: I) =>
        Trace[F].span(s"${S.id.name}.${fa.name}", spanOptions) {
          algebraAsPolyFunction.apply(fa.wrap(i))
        }
      }
    })
  }

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
    SimpleAlgebraInstrumentation(alg, Span.Options.Defaults)
}

object AlgebraInstrumentationWithInputs {
  /**
   * For the given algebra, whenever an operation is invoked, its parameters will be appended to
   * the current trace as an annotation.
   *
   * This should be added to an algebra instance before [[SimpleAlgebraInstrumentation]], since
   * that will wrap this one, adding the new span for the operation.
   *
   * @param alg The algebra instance
   * @param S   The instance of `Service` for the given algebra
   * @tparam Alg The higher-kinded algebra type.
   * @tparam F   The effect type constructor, which must have instances of `Applicative` and `Trace`.
   * @return A transformed algebra where all endpoints are wrapped with additional instrumentation logic.
   */
  def apply[Alg[_[_, _, _, _, _]], F[_] : Applicative : Trace](alg: Alg[Kind1[F]#toKind5])
                                                              (implicit S: Service[Alg]): Alg[Kind1[F]#toKind5] = {
    val algebraAsPolyFunction = S.toPolyFunction(alg)

    S.impl(new S.FunctorEndpointCompiler[F] {
      override def apply[I, E, O, SI, SO](fa: S.Endpoint[I, E, O, SI, SO]): I => F[O] = {
        implicit val traceableValueForInput: TraceableValue[I] = SchemaVisitorTraceableValue.fromSchema(fa.schema.input)
        val inputName = s"${S.id.name}.${fa.name}.${fa.input.shapeId.name}"

        (i: I) =>
          Trace[F].put(inputName -> i) *> algebraAsPolyFunction.apply(fa.wrap(i))
      }
    })
  }
}

object AlgebraInstrumentationWithOutputs {
  /**
   * For the given algebra, whenever an operation is invoked, its return value will be appended to
   * the current trace as an annotation.
   *
   * This should be added to an algebra instance before [[SimpleAlgebraInstrumentation]], since
   * that will wrap this one, adding the new span for the operation.
   *
   * @param alg The algebra instance
   * @param S   The instance of `Service` for the given algebra
   * @tparam Alg The type constructor of the algebra, parameterized over kinds.
   * @tparam F   The effect type, which must have instances of `Monad` and `Trace`.
   * @return A new algebra instance with instrumentation for tracing outputs and propagated effects.
   */
  def apply[Alg[_[_, _, _, _, _]], F[_] : Monad : Trace](alg: Alg[Kind1[F]#toKind5])
                                                        (implicit S: Service[Alg]): Alg[Kind1[F]#toKind5] = {
    val algebraAsPolyFunction = S.toPolyFunction(alg)

    S.impl(new S.FunctorEndpointCompiler[F] {
      override def apply[I, E, O, SI, SO](fa: S.Endpoint[I, E, O, SI, SO]): I => F[O] = {
        implicit val traceableValueForOutput: TraceableValue[O] = SchemaVisitorTraceableValue.fromSchema(fa.schema.output)
        val outputName = s"${S.id.name}.${fa.name}.returnValue"

        (i: I) =>
          algebraAsPolyFunction
            .apply(fa.wrap(i))
            .flatTap(o => Trace[F].put(outputName -> o))
      }
    })
  }
}
