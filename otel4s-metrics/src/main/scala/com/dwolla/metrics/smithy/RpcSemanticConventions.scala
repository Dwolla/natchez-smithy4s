package com.dwolla.metrics.smithy

import cats.FlatMap
import cats.effect.kernel.Resource
import cats.syntax.all.*
import org.typelevel.otel4s.{Attribute, AttributeKey}
import org.typelevel.otel4s.metrics.{BucketBoundaries, Histogram, MeterProvider}
import smithy4s.ShapeId
import smithy4s.schema.ErrorSchema

/**
 * The subset of the OpenTelemetry RPC semantic conventions this library emits. These are
 * deliberately our own constants rather than a dependency on otel4s's experimental semconv
 * module, which makes no binary-compatibility guarantees; `RpcSemanticConventionsTest`
 * checks them against that module.
 */
private[smithy] object RpcSemanticConventions {
  /** The OpenTelemetry instrumentation scope this library records its metrics under. */
  val InstrumentationScopeName: String = "com.dwolla.metrics.smithy"

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

  /**
   * `error.type` for a call to an endpoint whose declared errors are `modeledErrors`: `canceled` for
   * a canceled call; otherwise, after unwrapping an escaped cats-mtl raise, the Smithy shape ID of a
   * declared error, or else the [[ErrorTypeName]] of the error.
   */
  def errorType[E](modeledErrors: Option[ErrorSchema[E]])(exitCase: Resource.ExitCase): Option[Attribute[String]] =
    exitCase match {
      case Resource.ExitCase.Succeeded => None
      case Resource.ExitCase.Errored(e) => Attribute(ErrorType, failedErrorType(modeledErrors, e)).some
      case Resource.ExitCase.Canceled => Attribute(ErrorType, CanceledErrorType).some
    }

  private def failedErrorType[E](modeledErrors: Option[ErrorSchema[E]], error: Throwable): String = {
    val raised: Any = error match {
      case EscapedRaise(raisedError) => raisedError
      case _ => error
    }
    raised match {
      case throwable: Throwable => modeledErrorShapeId(modeledErrors, throwable).fold(ErrorTypeName(throwable))(_.show)
      case other => ErrorTypeName(other)
    }
  }

  private def modeledErrorShapeId[E](modeledErrors: Option[ErrorSchema[E]], error: Throwable): Option[ShapeId] =
    modeledErrors.flatMap { errorSchema =>
      errorSchema.liftError(error).map(e => errorSchema.alternatives(errorSchema.ordinal(e)).schema.shapeId)
    }

  def callDurationHistogram[F[_] : FlatMap : MeterProvider](role: RpcRole): F[Histogram[F, Double]] =
    MeterProvider[F]
      .meter(InstrumentationScopeName)
      .withVersion(BuildInfo.version)
      .get
      .flatMap {
        _.histogram[Double](role.callDurationMetricName)
          .withDescription(role.callDurationDescription)
          .withUnit(CallDurationUnit)
          .withExplicitBucketBoundaries(CallDurationBucketBoundaries)
          .create
      }
}
