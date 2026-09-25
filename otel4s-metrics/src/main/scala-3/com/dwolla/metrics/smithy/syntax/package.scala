package com.dwolla.metrics.smithy
package syntax

import cats.effect.kernel.MonadCancelThrow
import org.typelevel.otel4s.metrics.Meter
import smithy4s.Service
import smithy4s.kinds.Kind1

extension [Alg[_[_, _, _, _, _]], F[_]](alg: Alg[Kind1[F]#toKind5]) {
  /**
   * Wraps `alg` so that every endpoint call records its duration to the OpenTelemetry RPC
   * call-duration histogram for `role`. See [[AlgebraMetrics]] for the recorded attributes.
   *
   * @param role Whether `alg` is a server implementation or a client.
   * @return An effect that creates the histogram and yields the instrumented algebra.
   */
  def withMetrics(role: RpcRole)
                 (using MonadCancelThrow[F], Meter[F], Service[Alg]): F[Alg[Kind1[F]#toKind5]] =
    AlgebraMetrics(alg, role)
}
