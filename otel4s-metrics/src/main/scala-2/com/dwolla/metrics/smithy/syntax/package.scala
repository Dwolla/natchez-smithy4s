package com.dwolla.metrics.smithy

import cats.effect.kernel.MonadCancelThrow
import org.typelevel.otel4s.metrics.Meter
import smithy4s._
import smithy4s.kinds._

package object syntax {
  implicit class MetricsAlgebraOps[Alg[_[_, _, _, _, _]], F[_]](val alg: Alg[Kind1[F]#toKind5]) extends AnyVal {
    /**
     * Wraps `alg` so that every endpoint call records its duration to the OpenTelemetry RPC
     * call-duration histogram for `role`. See [[AlgebraMetrics]] for the recorded attributes.
     *
     * @param role Whether `alg` is a server implementation or a client.
     * @param F    Used to bracket the call so its outcome is recorded even if it fails or is canceled.
     * @param M    The `Meter` the histogram is created from.
     * @param S    The `Service` instance for the algebra.
     * @return An effect that creates the histogram and yields the instrumented algebra.
     */
    def withMetrics(role: RpcRole)
                   (implicit F: MonadCancelThrow[F],
                    M: Meter[F],
                    S: Service[Alg]): F[Alg[Kind1[F]#toKind5]] =
      AlgebraMetrics(alg, role)
  }
}
