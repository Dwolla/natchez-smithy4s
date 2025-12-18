package com.example.tracing

import cats.data.Chain
import cats.effect.IO
import cats.syntax.all.*
import com.dwolla.tracing.smithy.SchemaVisitorTraceableValue
import com.dwolla.tracing.smithy.syntax.*
import com.example.tracing.TracingServiceOperation.*
import munit.{CatsEffectSuite, Location, ScalaCheckEffectSuite, TestOptions}
import natchez.*
import natchez.InMemory.Lineage.*
import natchez.InMemory.NatchezCommand.*
import org.scalacheck.*
import org.scalacheck.Arbitrary.arbitrary
import org.scalacheck.effect.PropF.forAllF

class AlgebraInstrumentationTest
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

  instrumentationTest("SimpleAlgebraInstrumentation should create spans for arbitrary operations") { (_, _, _) => _ =>
    InstrumentationTestParameters.defaults
  }

  instrumentationTest("SimpleAlgebraInstrumentation can be composed with AlgebraInstrumentationWithInputs") { (operation, _, _) => implicit t =>
    // helps the Scala 2.13 compiler unify the `I` type when going from `Schema[I]` to `TraceableValue[I].toTraceValue`
    def getTraceValueForOperationInput[I, E, O, SI, SO](op: TracingServiceOperation[I, E, O, SI, SO]): TraceValue =
      SchemaVisitorTraceableValue.fromSchema(op.endpoint.schema.input).toTraceValue(op.input)

    InstrumentationTestParameters(
      implTransform = _.withTracedInputs(),
      additionalDirectives = Chain(
        Child(s"${TracingService.id.name}.${operation.endpoint.name}", Root("SimpleAlgebraInstrumentation can be composed with AlgebraInstrumentationWithInputs")) ->
          Put(List(s"${TracingService.id.name}.${operation.endpoint.schema.id.name}.${operation.endpoint.schema.input.shapeId.name}" -> getTraceValueForOperationInput(operation)))
      )
    )
  }

  instrumentationTest("SimpleAlgebraInstrumentation can be composed with AlgebraInstrumentationWithOutputs") { (operation, _, _) => implicit t =>
    // helps the Scala 2.13 compiler unify the `O` type when going from `Schema[O]` to `TraceableValue[O].toTraceValue`
    def getTraceValueForOperationOutput[I, E, O, SI, SO](op: TracingServiceOperation[I, E, O, SI, SO]): TracingService[IO] => IO[TraceValue] =
      TracingService.toPolyFunction(_)
        .apply(op)
        .map(SchemaVisitorTraceableValue.fromSchema(op.endpoint.schema.output).toTraceValue)

    val traceDirectives =
      getTraceValueForOperationOutput(operation)(_: TracingService[IO])
        .attemptT
        .map { traceValue =>
          Chain(
            Child(s"${TracingService.id.name}.${operation.endpoint.name}", Root("SimpleAlgebraInstrumentation can be composed with AlgebraInstrumentationWithOutputs")) ->
              Put(List(s"${TracingService.id.name}.${operation.endpoint.schema.id.name}.returnValue" -> traceValue))
          )
        }
        .getOrElse(Chain.empty)

    InstrumentationTestParameters(
      implTransform = _.withTracedOutputs(),
      additionalDirectives = traceDirectives
    )
  }

  def instrumentationTest(options: TestOptions)
                         (f: (TracingServiceOperation[_, _, _, _, _], Option[Span.Options], Option[Throwable]) => Trace[IO] => InstrumentationTestParameters)
                         (implicit loc: Location): Unit = {
    test(options) {
      forAllF { (operation: TracingServiceOperation[_, _, _, _, _],
                 maybeSpanOptions: Option[Span.Options],
                 maybeException: Option[Throwable],
                ) =>
        for {
          entryPoint <- InMemory.EntryPoint.create[IO]
          serviceImpl = TracingServiceImpl[IO](maybeException)
          tuple <- entryPoint.root(options.name)
            .evalMap(Trace.ioTrace)
            .map { implicit t =>
              f(operation, maybeSpanOptions, maybeException)(implicitly)
                .leftMap(_(serviceImpl))
                .map(_(serviceImpl))
                .leftMap { s =>
                  maybeSpanOptions.fold(s.withSimpleInstrumentation())(s.withSimpleInstrumentation(_))
                }
            }
            .use { case (impl: TracingService[IO], additionalDirectives: IO[Chain[(InMemory.Lineage, InMemory.NatchezCommand)]]) =>
              TracingService.toPolyFunction(impl).apply(operation).attempt.product(additionalDirectives)
            }
          (tracedResponse, additionalDirectives) = tuple

          untracedResponse <- TracingService.toPolyFunction(serviceImpl).apply(operation).attempt

          natchezDirectives: Chain[(InMemory.Lineage, InMemory.NatchezCommand)] <- entryPoint.ref.get
        } yield {
          val defaultSpanOptions = Span.Options.Defaults
          val expectedDirectives: Chain[(InMemory.Lineage, InMemory.NatchezCommand)] =
            expectedNatchezDirectivesBeforePotentialError(options, operation.endpoint.name, maybeSpanOptions.getOrElse(defaultSpanOptions)) ++
              additionalDirectives ++
              expectedNatchezDirectivesForPotentialError(options, operation.endpoint.name, maybeException) ++
              expectedNatchezDirectivesAfterPotentialError(options, operation.endpoint.name)

          assertEquals(untracedResponse, tracedResponse)
          assertEquals(natchezDirectives.toList, expectedDirectives.toList)
        }
      }
    }
  }

  private def expectedNatchezDirectivesBeforePotentialError(options: TestOptions,
                                                            name: String,
                                                            spanOptions: Span.Options): Chain[(InMemory.Lineage, InMemory.NatchezCommand)] =
    Chain(
      Root("root") -> CreateRootSpan(options.name, natchez.Kernel(Map.empty), Span.Options.Defaults),
      Root(options.name) -> CreateSpan(s"TracingService.$name", spanOptions.parentKernel, spanOptions),
    )

  private def expectedNatchezDirectivesForPotentialError(options: TestOptions,
                                                         name: String,
                                                         maybeException: Option[Throwable]) =
      maybeException.map[Chain[(InMemory.Lineage, InMemory.NatchezCommand)]] { ex =>
        Chain(
          Child(s"TracingService.$name", Root(options.name)) -> AttachError(ex, List.empty),
        )
      }.getOrElse(Chain.empty)

  private def expectedNatchezDirectivesAfterPotentialError(options: TestOptions,
                                                           name: String): Chain[(InMemory.Lineage, InMemory.NatchezCommand)] =
    Chain(
      Root(options.name) -> ReleaseSpan(s"TracingService.$name"),
      Root("root") -> ReleaseRootSpan(options.name),
    )
}

case class InstrumentationTestParameters(implTransform: TracingService[IO] => TracingService[IO],
                                         additionalDirectives: TracingService[IO] => IO[Chain[(InMemory.Lineage, InMemory.NatchezCommand)]]) {
  def leftMap[A](f: (TracingService[IO] => TracingService[IO]) => A): (A, TracingService[IO] => IO[Chain[(InMemory.Lineage, InMemory.NatchezCommand)]]) =
    f(implTransform) -> additionalDirectives
}
object InstrumentationTestParameters {
  def apply(implTransform: TracingService[IO] => TracingService[IO],
            additionalDirectives: TracingService[IO] => IO[Chain[(InMemory.Lineage, InMemory.NatchezCommand)]]): InstrumentationTestParameters =
    new InstrumentationTestParameters(implTransform, additionalDirectives)

  def apply(implTransform: TracingService[IO] => TracingService[IO],
            additionalDirectives: Chain[(InMemory.Lineage, InMemory.NatchezCommand)]): InstrumentationTestParameters =
    InstrumentationTestParameters(implTransform, _ => additionalDirectives.pure[IO])

  val defaults: InstrumentationTestParameters = InstrumentationTestParameters(identity, Chain.empty)
}
