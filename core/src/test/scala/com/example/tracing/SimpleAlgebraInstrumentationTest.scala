package com.example.tracing

import cats.data.Chain
import cats.effect.IO
import com.dwolla.tracing.smithy.syntax.*
import com.example.tracing.TracingServiceOperation._
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import natchez.{Span, Trace}
import natchez.InMemory
import natchez.InMemory.Lineage.*
import natchez.InMemory.NatchezCommand.*
import org.scalacheck.*
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.effect.PropF.forAllF

/**
 * Tests for SimpleAlgebraInstrumentation.
 * These tests verify that the instrumentation correctly creates spans for service operations.
 */
class SimpleAlgebraInstrumentationTest
  extends CatsEffectSuite
    with ScalaCheckEffectSuite
    with natchez.Arbitraries {

  implicit val arbTracingRequest: Arbitrary[TracingRequest] = Arbitrary {
    for {
      id <- Gen.identifier
      value <- Gen.chooseNum(1, 100)
      description <- Gen.option(Gen.alphaNumStr.suchThat(_.nonEmpty))
    } yield TracingRequest(id, value, description)
  }

  implicit val arbTracingServiceOperation: Arbitrary[TracingServiceOperation[_, _, _, _, _]] = Arbitrary {
    Gen.oneOf(
      Gen.const(GetStatus()),
      arbitrary[TracingRequest].map(ProcessRequest(_)),
    )
  }

  test("SimpleAlgebraInstrumentation should create spans for arbitrary operations") {
    forAllF { (operation: TracingServiceOperation[_, _, _, _, _],
               maybeSpanOptions: Option[Span.Options],
               maybeException: Option[Throwable],
              ) =>
      for {
        entryPoint <- InMemory.EntryPoint.create[IO]
        serviceImpl = TracingServiceImpl[IO](maybeException)

        untracedResponse <- TracingService.toPolyFunction(serviceImpl).apply(operation).attempt
        tracedResponse <-
          entryPoint.root(s"SimpleAlgebraInstrumentation should create spans for ${operation.endpoint.name}")
            .evalMap(Trace.ioTrace)
            .map { implicit t =>
              maybeSpanOptions.fold(serviceImpl.withSimpleInstrumentation())(serviceImpl.withSimpleInstrumentation(_))
            }
            .use(TracingService.toPolyFunction(_).apply(operation).attempt)

        natchezDirectives: Chain[(InMemory.Lineage, InMemory.NatchezCommand)] <- entryPoint.ref.get
      } yield {
        val defaultSpanOptions = Span.Options.Defaults.withSpanKind(Span.SpanKind.Server)
        val expectedDirectives: Chain[(InMemory.Lineage, InMemory.NatchezCommand)] =
          expectedNatchezDirectivesBeforePotentialError(operation.endpoint.name, maybeSpanOptions.getOrElse(defaultSpanOptions)) ++
            expectedNatchezDirectivesForPotentialError(operation.endpoint.name, maybeException) ++
            expectedNatchezDirectivesAfterPotentialError(operation.endpoint.name)

        assertEquals(untracedResponse, tracedResponse)
        assertEquals(natchezDirectives.toList, expectedDirectives.toList)
      }
    }
  }

  private def expectedNatchezDirectivesBeforePotentialError(name: String,
                                                            spanOptions: Span.Options): Chain[(InMemory.Lineage, InMemory.NatchezCommand)] =
    Chain(
      Root("root") -> CreateRootSpan(s"SimpleAlgebraInstrumentation should create spans for $name", natchez.Kernel(Map.empty), Span.Options.Defaults),
      Root(s"SimpleAlgebraInstrumentation should create spans for $name") -> CreateSpan(s"TracingService.$name", spanOptions.parentKernel, spanOptions),
    )

  private def expectedNatchezDirectivesForPotentialError(name: String,
                                                         maybeException: Option[Throwable]) =
      maybeException.map[Chain[(InMemory.Lineage, InMemory.NatchezCommand)]] { ex =>
        Chain(
          Child(s"TracingService.$name", Root(s"SimpleAlgebraInstrumentation should create spans for $name")) -> AttachError(ex, List.empty),
        )
      }.getOrElse(Chain.empty)

  private def expectedNatchezDirectivesAfterPotentialError(name: String): Chain[(InMemory.Lineage, InMemory.NatchezCommand)] =
    Chain(
      Root(s"SimpleAlgebraInstrumentation should create spans for $name") -> ReleaseSpan(s"TracingService.$name"),
      Root("root") -> ReleaseRootSpan(s"SimpleAlgebraInstrumentation should create spans for $name"),
    )
}
