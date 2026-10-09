package com.dwolla.tracing.smithy.otel4s

import cats.effect.IO
import cats.mtl.Handle
import cats.syntax.all.*
import com.dwolla.tracing.smithy.otel4s.syntax.*
import com.example.tracing.*
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import org.scalacheck.effect.PropF.forAllF
import org.typelevel.otel4s.sdk.trace.data.SpanData
import org.typelevel.otel4s.trace.{SpanKind, StatusCode}
import org.typelevel.otel4s.{Attribute, Attributes}

class SimpleAlgebraInstrumentationTest
  extends CatsEffectSuite
    with ScalaCheckEffectSuite
    with SpanRecording
    with TracingServiceArbitraries {

  private val response = TracingResponse(id = "r", result = 4, status = Some("ok"))
  private val impl: TracingService[IO] = new FixedTracingService(IO.pure(response))

  private def invoke(alg: TracingService[IO], operation: TracingServiceOperation[_, _, _, _, _]): IO[Either[Throwable, Any]] =
    TracingService.toPolyFunction(alg).apply(operation).attempt

  private def errorType(span: SpanData): Option[String] =
    span.attributes.elements.get[String]("error.type").map(_.value)

  test("each call runs in its own Internal span named <Service>.<Operation>, under this library's versioned scope") {
    forAllF { (operation: TracingServiceOperation[_, _, _, _, _]) =>
      for {
        untraced <- invoke(impl, operation)
        tracedAndSpans <- resultAndSpans { implicit tracerProvider =>
          impl.withSimpleInstrumentation().flatMap(invoke(_, operation))
        }
        (traced, spans) = tracedAndSpans
      } yield {
        val name = s"TracingService.${operation.endpoint.name}"
        assertEquals(traced, untraced)
        assertEquals(spans.map(_.name), List(name))
        assertEquals(spans.map(_.kind), List(SpanKind.Internal))
        assertEquals(spans.map(_.instrumentationScope.name), List("com.dwolla.tracing.smithy.otel4s"))
        assertEquals(spans.map(_.instrumentationScope.version), List(Some(BuildInfo.version)))
        assertEquals(spans.map(_.attributes.elements), List(Attributes(Attribute("code.function.name", name))))
        assertEquals(spans.map(_.status.status), List(StatusCode.Unset))
      }
    }
  }

  test("the span kind can be chosen") {
    resultAndSpans { implicit tracerProvider =>
      impl.withSimpleInstrumentation(SpanKind.Server).flatMap(_.getStatus())
    }.map { case (_, spans) => assertEquals(spans.map(_.kind), List(SpanKind.Server)) }
  }

  test("the syntax also applies to an algebra inside F, so wrappers chain") {
    resultAndSpans { implicit tracerProvider =>
      impl.pure[IO].withSimpleInstrumentation().flatMap(_.getStatus())
    }.map { case (_, spans) => assertEquals(spans.map(_.name), List("TracingService.GetStatus")) }
  }

  test("an error the operation declares records its shape ID as error.type, status ERROR, and an exception event") {
    val error = TracingError("boom")
    resultAndSpans { implicit tracerProvider =>
      new FixedTracingService(IO.raiseError(error)).withSimpleInstrumentation()
        .flatMap(_.processRequest("id-1", 1).attempt)
    }.map { case (result, spans) =>
      assertEquals(result, Left(error))
      assertEquals(spans.map(_.status.status), List(StatusCode.Error))
      assertEquals(spans.map(errorType), List(Some("com.example.tracing#TracingError")))
      assertEquals(spans.map(_.events.elements.map(_.name).toList), List(List("exception")))
    }
  }

  test("an error the operation doesn't declare records its class name as error.type") {
    resultAndSpans { implicit tracerProvider =>
      new FixedTracingService(IO.raiseError(TracingError("boom"))).withSimpleInstrumentation()
        .flatMap(_.getStatus().attempt)
    }.map { case (_, spans) =>
      assertEquals(spans.map(errorType), List(Some("com.example.tracing.TracingError")))
    }
  }

  test("a cats-mtl raise that escapes the call records the raised error as error.type, with no exception event") {
    for {
      recordedSpans <- IO.ref(List.empty[SpanData])
      recovered <- Handle.allowF[IO, DomainError] { raise =>
        resultAndSpans { implicit tracerProvider =>
          new FixedTracingService(raise.raise[DomainError, TracingResponse](DomainError.NotFound))
            .withSimpleInstrumentation()
            .flatMap(_.getStatus().attempt)
        }.flatMap { case (escaped, spans) => recordedSpans.set(spans) >> escaped.liftTo[IO] }
      }.attempt
      spans <- recordedSpans.get
    } yield {
      assertEquals(recovered.left.toOption, Some(DomainError.NotFound))
      assertEquals(spans.map(_.status.status), List(StatusCode.Error))
      assertEquals(spans.map(errorType), List(Some("com.dwolla.tracing.smithy.otel4s.DomainError$NotFound$")))
      assertEquals(spans.map(_.events.elements.toList), List(Nil))
    }
  }

  test("a canceled call records status ERROR (canceled) and error.type canceled") {
    resultAndSpans { implicit tracerProvider =>
      new FixedTracingService(IO.canceled >> IO.never).withSimpleInstrumentation()
        .flatMap(_.getStatus().start.flatMap(_.join))
    }.map { case (outcome, spans) =>
      assert(outcome.isCanceled)
      assertEquals(spans.map(s => (s.status.status, s.status.description)), List((StatusCode.Error, Some("canceled"))))
      assertEquals(spans.map(errorType), List(Some("canceled")))
    }
  }
}
