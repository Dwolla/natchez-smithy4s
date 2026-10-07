package com.dwolla.tracing.smithy.otel4s

import munit.FunSuite

/**
 * The otel4s module never brings natchez in, directly or transitively: an application that has moved its tracing
 * to otel4s must be able to drop natchez from its classpath entirely.
 */
class NatchezIsolationTest extends FunSuite {
  private def assertNotOnClasspath(className: String)(implicit loc: munit.Location): Unit =
    assertEquals(intercept[ClassNotFoundException](Class.forName(className)).getMessage, className)

  test("natchez is not on the classpath")(assertNotOnClasspath("natchez.Trace"))
  test("natchez-smithy4s is not on the classpath")(assertNotOnClasspath("com.dwolla.tracing.smithy.Traceable"))
}
