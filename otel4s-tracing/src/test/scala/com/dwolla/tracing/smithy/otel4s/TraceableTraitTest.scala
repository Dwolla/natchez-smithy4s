package com.dwolla.tracing.smithy.otel4s

import munit.FunSuite
import smithy4s.ShapeId

/** The otel4s `@traceable` trait is its own shape, distinct from natchez-smithy4s's, with the same one member. */
class TraceableTraitTest extends FunSuite {
  test("the trait's shape ID is in the otel4s namespace") {
    assertEquals(Traceable.id, ShapeId("com.dwolla.tracing.smithy.otel4s", "traceable"))
  }

  test("the trait carries an optional redaction string") {
    assertEquals(Traceable(redacted = Some("<redacted>")).redacted, Some("<redacted>"))
    assertEquals(Traceable().redacted, None)
  }
}
