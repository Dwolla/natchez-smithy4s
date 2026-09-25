package com.dwolla.metrics.smithy

import cats.effect.kernel.{MonadCancelThrow, Resource}
import cats.syntax.all.*
import org.typelevel.otel4s.Attribute
import org.typelevel.otel4s.metrics.Meter
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
   * `rpc.method = "<namespace>.<Service>/<Operation>"`. Failed calls also carry `error.type`: the
   * fully-qualified class name of the error raised, or `"canceled"` if the call was canceled.
   * Errors and cancellation propagate unchanged.
   *
   * @param alg  Original algebra implementation to be instrumented.
   * @param role Whether `alg` is a server implementation or a client.
   * @param S    The `Service` instance for the algebra.
   * @return The histogram is created from `Meter[F]` when the returned effect runs; it yields the
   *         instrumented algebra, which shares that one histogram across all its endpoints.
   */
  def apply[Alg[_[_, _, _, _, _]], F[_] : MonadCancelThrow : Meter](alg: Alg[Kind1[F]#toKind5],
                                                                    role: RpcRole)
                                                                   (implicit S: Service[Alg]): F[Alg[Kind1[F]#toKind5]] =
    RpcSemanticConventions.callDurationHistogram[F](role).map { callDuration =>
      val algebraAsPolyFunction = S.toPolyFunction(alg)

      S.impl(new S.FunctorEndpointCompiler[F] {
        override def apply[I, E, O, SI, SO](fa: S.Endpoint[I, E, O, SI, SO]): I => F[O] = {
          val rpcMethod = RpcSemanticConventions.rpcMethod(S.id, fa.name)
          val attributesFor: Resource.ExitCase => List[Attribute[_]] = exitCase =>
            List[Attribute[_]](RpcSemanticConventions.SmithyRpcSystem, rpcMethod) ++ RpcSemanticConventions.errorType(exitCase).toList

          (i: I) =>
            callDuration
              .recordDuration(SECONDS, attributesFor)
              .surround(algebraAsPolyFunction.apply(fa.wrap(i)))
        }
      })
    }
}
