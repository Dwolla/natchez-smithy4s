package com.dwolla.tracing.smithy

import munit.FunSuite
import natchez.TraceValue
import smithy4s.Schema

/** Lists and sets render at most 5 elements, and say how many more there were only when there were more. */
class CollectionRenderingTest extends FunSuite {
  private def rendered(n: Int): TraceValue =
    SchemaVisitorTraceableValue.fromSchema(Schema.list(Schema.string)).toTraceValue((1 to n).map(i => s"e$i").toList)

  test("a list of up to 5 elements renders in full") {
    assertEquals(rendered(0), TraceValue.StringValue("[]"))
    assertEquals(rendered(4), TraceValue.StringValue("[e1, e2, e3, e4]"))
    assertEquals(rendered(5), TraceValue.StringValue("[e1, e2, e3, e4, e5]"))
  }

  test("a list of more than 5 elements renders the first 5, then how many more there were") {
    assertEquals(rendered(6), TraceValue.StringValue("[e1, e2, e3, e4, e5, and 1 more]"))
    assertEquals(rendered(8), TraceValue.StringValue("[e1, e2, e3, e4, e5, and 3 more]"))
  }
}
