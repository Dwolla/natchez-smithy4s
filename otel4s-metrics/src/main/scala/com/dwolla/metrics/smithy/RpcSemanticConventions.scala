package com.dwolla.metrics.smithy

import cats.effect.kernel.Resource
import cats.syntax.all.*
import org.typelevel.otel4s.{Attribute, AttributeKey}
import org.typelevel.otel4s.metrics.{BucketBoundaries, Histogram, Meter}
import smithy4s.ShapeId

/**
 * The subset of the OpenTelemetry RPC semantic conventions this library emits. These are
 * deliberately our own constants rather than a dependency on otel4s's experimental semconv
 * module, which makes no binary-compatibility guarantees; `RpcSemanticConventionsTest`
 * checks them against that module.
 */
private[smithy] object RpcSemanticConventions {
  val RpcSystemName: AttributeKey[String] = AttributeKey("rpc.system.name")
  val RpcMethod: AttributeKey[String] = AttributeKey("rpc.method")
  val ErrorType: AttributeKey[String] = AttributeKey("error.type")

  val SmithyRpcSystem: Attribute[String] = Attribute(RpcSystemName, "smithy")
  val CanceledErrorType: String = "canceled"

  val CallDurationUnit: String = "s"
  val CallDurationBucketBoundaries: BucketBoundaries =
    BucketBoundaries(0.005, 0.01, 0.025, 0.05, 0.075, 0.1, 0.25, 0.5, 0.75, 1.0, 2.5, 5.0, 7.5, 10.0)

  def rpcMethod(serviceId: ShapeId, operationName: String): Attribute[String] =
    Attribute(RpcMethod, s"${serviceId.namespace}.${serviceId.name}/$operationName")

  def errorType(exitCase: Resource.ExitCase): Option[Attribute[String]] =
    exitCase match {
      case Resource.ExitCase.Succeeded => None
      case Resource.ExitCase.Errored(e) => Attribute(ErrorType, e.getClass.getName).some
      case Resource.ExitCase.Canceled => Attribute(ErrorType, CanceledErrorType).some
    }

  def callDurationHistogram[F[_] : Meter](role: RpcRole): F[Histogram[F, Double]] =
    Meter[F]
      .histogram[Double](role.callDurationMetricName)
      .withDescription(role.callDurationDescription)
      .withUnit(CallDurationUnit)
      .withExplicitBucketBoundaries(CallDurationBucketBoundaries)
      .create
}
