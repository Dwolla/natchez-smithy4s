package com.dwolla.tracing.smithy.otel4s

import cats.effect.kernel.Resource
import cats.syntax.all.*
import com.dwolla.tagless.{ErrorTypeName, RaisedError}
import org.typelevel.otel4s.Attribute
import org.typelevel.otel4s.semconv.attributes.ErrorAttributes
import org.typelevel.otel4s.trace.{SpanFinalizer, StatusCode}
import smithy4s.schema.ErrorSchema

/**
 * How the span `SimpleAlgebraInstrumentation` opens is finalized. It's otel4s's `reportAbnormal` (status ERROR and an
 * exception event for a failure, and status ERROR "canceled" for cancellation), plus `error.type`, named as
 * otel4s-smithy4s-metrics names it:
 *  - `canceled` for a canceled call;
 *  - the Smithy shape ID of an error the operation declares;
 *  - otherwise tagless-core's `ErrorTypeName` of the error.
 * A cats-mtl raise that escaped the call is reported as the raised error itself, with status ERROR and no exception
 * event. Otherwise it would reach the span as cats-mtl's private `Submarine`, which names neither.
 */
private[otel4s] object EndpointSpanFinalization {
  private val CanceledErrorType: String = "canceled"

  def strategy[E](declaredErrors: Option[ErrorSchema[E]]): SpanFinalizer.Strategy = {
    case Resource.ExitCase.Errored(RaisedError(raised)) =>
      SpanFinalizer.setStatus(StatusCode.Error) |+| errorType(errorTypeName(declaredErrors, raised))
    case exitCase @ Resource.ExitCase.Errored(error) =>
      SpanFinalizer.Strategy.reportAbnormal(exitCase) |+| errorType(errorTypeName(declaredErrors, error))
    case exitCase @ Resource.ExitCase.Canceled =>
      SpanFinalizer.Strategy.reportAbnormal(exitCase) |+| errorType(CanceledErrorType)
  }

  private def errorType(name: String): SpanFinalizer =
    SpanFinalizer.addAttribute(Attribute(ErrorAttributes.ErrorType, name))

  private def errorTypeName[E](declaredErrors: Option[ErrorSchema[E]], error: Any): String =
    error match {
      case throwable: Throwable => declaredErrors.flatMap(declaredShapeId(_, throwable)).getOrElse(ErrorTypeName(throwable))
      case other => ErrorTypeName(other)
    }

  private def declaredShapeId[E](errorSchema: ErrorSchema[E], throwable: Throwable): Option[String] =
    errorSchema.liftError(throwable).map(e => errorSchema.alternatives(errorSchema.ordinal(e)).schema.shapeId.show)
}
