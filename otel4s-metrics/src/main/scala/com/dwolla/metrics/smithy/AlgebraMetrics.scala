package com.dwolla.metrics.smithy

import cats.effect.kernel.{MonadCancelThrow, Resource}
import cats.syntax.all.*
import org.typelevel.otel4s.Attribute
import org.typelevel.otel4s.metrics.MeterProvider
import smithy4s.*
import smithy4s.kinds.*

import scala.concurrent.duration.SECONDS

object AlgebraMetrics {
  /**
   * Wraps an existing algebra implementation (`alg`) so that every call to any of its endpoints
   * records its duration, in seconds, to the OpenTelemetry `rpc.server.call.duration` or
   * `rpc.client.call.duration` histogram (chosen by `role`).
   *
   * Each measurement carries `rpc.system.name = "smithy"` and
   * `rpc.method = "<namespace>.<Service>/<Operation>"`. Failed calls also carry `error.type`:
   *  - `"canceled"` if the call was canceled;
   *  - the Smithy shape ID (e.g. `"com.example#NotFound"`) of an error the operation declares;
   *  - otherwise, the fully-qualified class name of the error raised, except that a Scala 3 `enum`'s
   *    simple cases are named like `"com.example.FooError.NotFound"`.
   * A cats-mtl raise that escapes the call is reported as the raised error, not cats-mtl's wrapper.
   * Errors and cancellation propagate unchanged.
   *
   * The histogram is created from a `Meter` this library obtains from `MeterProvider[F]`, named
   * for its instrumentation scope, `com.dwolla.metrics.smithy`, and versioned with the library.
   * natchez-tagless's `otel4s-tagless-metrics` records the same metric with an identical descriptor
   * under its own scope, so the two never conflict.
   *
   * To extend this API, add overloads rather than default arguments: a method with defaults can't
   * gain a parameter, or a same-named sibling with defaults, without breaking binary compatibility.
   *
   * @param alg  Original algebra implementation to be instrumented.
   * @param role Whether `alg` is a server implementation or a client.
   * @param S    The `Service` instance for the algebra.
   * @return The histogram is created when the returned effect runs; it yields the instrumented
   *         algebra, which shares that one histogram across all its endpoints.
   */
  def apply[Alg[_[_, _, _, _, _]], F[_] : MonadCancelThrow : MeterProvider](alg: Alg[Kind1[F]#toKind5],
                                                                    role: RpcRole)
                                                                   (implicit S: Service[Alg]): F[Alg[Kind1[F]#toKind5]] =
    RpcSemanticConventions.callDurationHistogram[F](role).map { callDuration =>
      val algebraAsPolyFunction = S.toPolyFunction(alg)

      S.impl(new S.FunctorEndpointCompiler[F] {
        override def apply[I, E, O, SI, SO](fa: S.Endpoint[I, E, O, SI, SO]): I => F[O] = {
          val rpcMethod = RpcSemanticConventions.rpcMethod(S.id, fa.name)
          val attributesFor: Resource.ExitCase => List[Attribute[_]] = exitCase =>
            List[Attribute[_]](RpcSemanticConventions.SmithyRpcSystem, rpcMethod) ++ RpcSemanticConventions.errorType(fa.error)(exitCase).toList

          (i: I) =>
            callDuration
              .recordDuration(SECONDS, attributesFor)
              .surround(algebraAsPolyFunction.apply(fa.wrap(i)))
        }
      })
    }
}
