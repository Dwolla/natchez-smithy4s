package com.dwolla.tracing.smithy.otel4s

import cats.*
import cats.syntax.all.*
import org.typelevel.otel4s.trace.{SpanKind, TracerProvider}
import smithy4s.*
import smithy4s.kinds.*

object SimpleAlgebraInstrumentation {
  /**
   * Wraps an algebra so that every call to one of its endpoints runs in a new child span named
   * `<Service>.<Operation>`, carrying `code.function.name` with the same value. A failed call records status ERROR,
   * `error.type` (the Smithy shape ID of an error the operation declares, otherwise the error's class name), and an
   * exception event. A canceled call records status ERROR "canceled" and `error.type = canceled`.
   *
   * The tracer is obtained from `TracerProvider[F]` once, when the returned effect runs, under the instrumentation
   * scope `com.dwolla.tracing.smithy.otel4s` (versioned).
   *
   * Apply this outermost when combining it with `AlgebraInstrumentationWithInputs` or
   * `AlgebraInstrumentationWithOutputs`, so their attributes land on this span.
   *
   * To extend this API, add overloads rather than default arguments: a method with defaults can't gain a parameter,
   * or a same-named sibling with defaults, without breaking binary compatibility.
   *
   * @param spanKind the kind of span to open. `Internal` (the other overload) suits an algebra whose transport is
   *                 already traced, such as an HTTP server or client with tracing middleware.
   */
  def apply[Alg[_[_, _, _, _, _]], F[_] : Functor : TracerProvider](alg: Alg[Kind1[F]#toKind5],
                                                                    spanKind: SpanKind)
                                                                   (implicit S: Service[Alg]): F[Alg[Kind1[F]#toKind5]] =
    LibraryTracer[F].map { tracer =>
      val algebraAsPolyFunction = S.toPolyFunction(alg)

      S.impl(new S.FunctorEndpointCompiler[F] {
        override def apply[I, E, O, SI, SO](fa: S.Endpoint[I, E, O, SI, SO]): I => F[O] = {
          val name = TracingAttributes.spanName(S.id, fa.name)
          val finalization = EndpointSpanFinalization.strategy(fa.error)

          (i: I) =>
            tracer
              .spanBuilder(name)
              .modifyState(_.withSpanKind(spanKind).withFinalizationStrategy(finalization).addAttribute(TracingAttributes.codeFunctionName(name)))
              .build
              .surround(algebraAsPolyFunction.apply(fa.wrap(i)))
        }
      })
    }

  /** As the other overload, with `SpanKind.Internal`. */
  def apply[Alg[_[_, _, _, _, _]], F[_] : Functor : TracerProvider](alg: Alg[Kind1[F]#toKind5])
                                                                   (implicit S: Service[Alg]): F[Alg[Kind1[F]#toKind5]] =
    SimpleAlgebraInstrumentation(alg, SpanKind.Internal)
}
