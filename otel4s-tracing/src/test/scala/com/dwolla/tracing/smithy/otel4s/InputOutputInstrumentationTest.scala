package com.dwolla.tracing.smithy.otel4s

import cats.effect.IO
import com.dwolla.tracing.smithy.otel4s.syntax.*
import com.example.tracing.*
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import org.scalacheck.effect.PropF.forAllF
import org.typelevel.otel4s.{AnyValue, Attribute, Attributes}

class InputOutputInstrumentationTest
  extends CatsEffectSuite
    with ScalaCheckEffectSuite
    with SpanRecording
    with TracingServiceArbitraries {

  private val response = TracingResponse(id = "r", result = 4, status = Some("ok"))
  private val encodedResponse = AnyValue.map(Map(
    "id" -> AnyValue.string("r"),
    "result" -> AnyValue.long(4L),
    "status" -> AnyValue.string("ok"),
  ))
  private val impl: TracingService[IO] = new FixedTracingService(IO.pure(response))

  private def invoke(alg: TracingService[IO], operation: TracingServiceOperation[_, _, _, _, _]): IO[Either[Throwable, Any]] =
    TracingService.toPolyFunction(alg).apply(operation).attempt

  // helps Scala 2.13 unify `I` between the endpoint's input schema and the operation's input
  private def encodedInput[I, E, O, SI, SO](operation: TracingServiceOperation[I, E, O, SI, SO]): AnyValue =
    SchemaVisitorToAnyValue.fromSchema(operation.endpoint.schema.input).toAnyValue(operation.input)

  private def argumentsAttribute(value: AnyValue): List[Attribute[?]] =
    if (value == AnyValue.empty) Nil else List(Attribute[AnyValue]("com.dwolla.code.function.arguments", value))

  test("inputs and outputs land on the span withSimpleInstrumentation opens, when it's applied last") {
    forAllF { (operation: TracingServiceOperation[_, _, _, _, _]) =>
      resultAndSpans { implicit tracerProvider =>
        impl.withTracedInputs().withTracedOutputs().withSimpleInstrumentation().flatMap(invoke(_, operation))
      }.map { case (_, spans) =>
        val name = s"TracingService.${operation.endpoint.name}"
        val expected =
          List[Attribute[?]](Attribute("code.function.name", name)) ++
            argumentsAttribute(encodedInput(operation)) :+
            Attribute[AnyValue]("com.dwolla.code.function.return_value", encodedResponse)
        assertEquals(spans.map(_.attributes.elements), List(Attributes(expected: _*)))
      }
    }
  }

  test("an input is recorded as a map of its member names to their values") {
    resultAndSpans { implicit tracerProvider =>
      impl.withTracedInputs().withSimpleInstrumentation().flatMap(_.processRequest("id-1", 2))
    }.map { case (_, spans) =>
      val arguments = spans.flatMap(_.attributes.elements.get[AnyValue]("com.dwolla.code.function.arguments").map(_.value))
      assertEquals(arguments, List(AnyValue.map(Map(
        "id" -> AnyValue.string("id-1"),
        "value" -> AnyValue.long(2L),
        "description" -> AnyValue.empty,
      ))))
    }
  }

  test("a Unit input records no arguments attribute") {
    resultAndSpans { implicit tracerProvider =>
      impl.withTracedInputs().withSimpleInstrumentation().flatMap(_.getStatus())
    }.map { case (_, spans) =>
      assertEquals(spans.map(_.attributes.elements.get[AnyValue]("com.dwolla.code.function.arguments")), List(None))
    }
  }

  test("a failed call records its input but no return value") {
    resultAndSpans { implicit tracerProvider =>
      new FixedTracingService(IO.raiseError(TracingError("boom")))
        .withTracedInputs().withTracedOutputs().withSimpleInstrumentation()
        .flatMap(_.processRequest("id-1", 2).attempt)
    }.map { case (_, spans) =>
      assertEquals(spans.map(_.attributes.elements.get[AnyValue]("com.dwolla.code.function.arguments").isDefined), List(true))
      assertEquals(spans.map(_.attributes.elements.get[AnyValue]("com.dwolla.code.function.return_value")), List(None))
    }
  }

  test("a canceled call records no return value") {
    resultAndSpans { implicit tracerProvider =>
      new FixedTracingService(IO.canceled >> IO.never)
        .withTracedOutputs().withSimpleInstrumentation()
        .flatMap(_.getStatus().start.flatMap(_.join))
    }.map { case (outcome, spans) =>
      assert(outcome.isCanceled)
      assertEquals(spans.map(_.attributes.elements.get[AnyValue]("com.dwolla.code.function.return_value")), List(None))
    }
  }

  test("applied without withSimpleInstrumentation, inputs and outputs land on the current span") {
    resultAndSpans { implicit tracerProvider =>
      tracerProvider.get("app").flatMap { appTracer =>
        appTracer.span("parent").surround {
          impl.withTracedInputs().withTracedOutputs().flatMap(_.processRequest("id-1", 2))
        }
      }
    }.map { case (_, spans) =>
      assertEquals(spans.map(_.name), List("parent"))
      assertEquals(
        spans.flatMap(_.attributes.elements.get[AnyValue]("com.dwolla.code.function.return_value").map(_.value)),
        List(encodedResponse),
      )
    }
  }

  test("two calls recording onto one shared span: the last call's values win") {
    resultAndSpans { implicit tracerProvider =>
      tracerProvider.get("app").flatMap { appTracer =>
        appTracer.span("parent").surround {
          impl.withTracedInputs().flatMap(alg => alg.processRequest("first", 1) >> alg.processRequest("second", 2))
        }
      }
    }.map { case (_, spans) =>
      val ids = spans.flatMap(_.attributes.elements.get[AnyValue]("com.dwolla.code.function.arguments").map(_.value)).collect {
        case map: AnyValue.MapValue => map.value.get("id")
      }
      assertEquals(ids, List(Some(AnyValue.string("second"))))
    }
  }
}
