package com.dwolla.tracing.smithy.otel4s

import cats.effect.IO
import com.dwolla.tracing.smithy.otel4s.syntax.*
import com.example.tracing.TracingResponse
import io.opentelemetry.api.common.AttributeType
import munit.CatsEffectSuite
import org.typelevel.otel4s.oteljava.testkit.trace.TracesTestkit

import scala.jdk.CollectionConverters.*

/**
 * The full stack on otel4s-oteljava, the backend Dwolla runs. Structured values reach OTel Java as `VALUE`-typed
 * attributes: an input or output structure is always a map, which OTel Java never narrows. The README's table
 * documents this.
 */
class OtelJavaEndToEndTest extends CatsEffectSuite {
  test("span name and attribute types on the OTel Java SDK") {
    TracesTestkit.inMemory[IO]().use { testkit =>
      implicit val tracerProvider: org.typelevel.otel4s.trace.TracerProvider[IO] = testkit.tracerProvider
      new FixedTracingService(IO.pure(TracingResponse(id = "r", result = 4, status = Some("ok"))))
        .withTracedInputs().withTracedOutputs().withSimpleInstrumentation()
        .flatMap(_.processRequest("id-1", 2))
        .flatMap(_ => testkit.finishedSpans)
    }.map { spans =>
      assertEquals(spans.map(_.getName), List("TracingService.ProcessRequest"))
      val types = spans.flatMap(_.getAttributes.asMap.asScala.keys.map(k => k.getKey -> k.getType)).toMap
      assertEquals(types, Map(
        "code.function.name" -> AttributeType.STRING,
        "com.dwolla.code.function.arguments" -> AttributeType.VALUE,
        "com.dwolla.code.function.return_value" -> AttributeType.VALUE,
      ))
    }
  }
}
