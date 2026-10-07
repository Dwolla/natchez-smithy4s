package com.dwolla.tracing.smithy.otel4s

import org.typelevel.otel4s.Attribute
import org.typelevel.otel4s.semconv.attributes.CodeAttributes
import smithy4s.ShapeId

/**
 * Span names and attribute keys, shared with otel4s-tagless so that one application records both libraries' calls
 * the same way. The keys are fixed (they never vary by service or operation), so one indexed key covers every
 * operation.
 */
private[otel4s] object TracingAttributes {
  /** An endpoint's encoded input: a map of input member name to value, which is how smithy4s passes the method's parameters. */
  val ArgumentsKey: String = "com.dwolla.code.function.arguments"

  /** An endpoint's encoded output, recorded only when the call succeeds. */
  val ReturnValueKey: String = "com.dwolla.code.function.return_value"

  def spanName(serviceId: ShapeId, endpointName: String): String = s"${serviceId.name}.$endpointName"

  def codeFunctionName(spanName: String): Attribute[String] = Attribute(CodeAttributes.CodeFunctionName, spanName)
}
