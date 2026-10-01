package com.dwolla.metrics.smithy

import cats.effect.IO
import cats.effect.testkit.TestControl
import cats.syntax.all.*
import com.example.tracing.*
import com.example.tracing.TracingServiceOperation.*
import com.dwolla.metrics.smithy.syntax.*
import org.scalacheck.{Arbitrary, Gen}
import org.scalacheck.effect.PropF.forAllF
import org.typelevel.otel4s.metrics.MeterProvider
import org.typelevel.otel4s.sdk.testkit.metrics.MetricsTestkit

import scala.concurrent.duration.*

class AlgebraMetricsTest extends AlgebraMetricsSuite {

  /**
   * A failure paired with the `error.type` we expect for it on a given operation, written out
   * literally. `TracingError` is modeled only on `ProcessRequest`, so only there is it reported by
   * its Smithy shape ID; anywhere else it's just another exception, reported by class name.
   */
  private implicit val arbFailure: Arbitrary[(Throwable, TracingServiceOperation[_, _, _, _, _] => String)] = Arbitrary {
    Gen.oneOf(
      Gen.alphaStr.map(msg => (new RuntimeException(msg), (_: TracingServiceOperation[_, _, _, _, _]) => "java.lang.RuntimeException")),
      Gen.alphaStr.map(msg => (new IllegalStateException(msg), (_: TracingServiceOperation[_, _, _, _, _]) => "java.lang.IllegalStateException")),
      for {
        msg <- Gen.alphaStr
        code <- Gen.option(Gen.posNum[Int])
      } yield (TracingError(msg, code), {
        case _: ProcessRequest => "com.example.tracing#TracingError"
        case _: GetStatus => "com.example.tracing.TracingError"
      }: TracingServiceOperation[_, _, _, _, _] => String),
    )
  }

  test("a successful call records its duration in seconds, with the standard buckets and no error.type") {
    forAllF { (role: RpcRole, operation: TracingServiceOperation[_, _, _, _, _], latency: FiniteDuration) =>
      recordedPoints(role, new ControlledTracingService(latency, successfulResponse.pure[IO]))(invoke(_, operation))
        .map { case (_, points) =>
          assertEquals(points.map(_.attributes), List(expectedAttributes(operation, None)))
          assertEquals(points.flatMap(_.stats).map(_.count), List(1L))
          assertEqualsDouble(points.flatMap(_.stats).map(_.sum).sum, latency.toUnit(SECONDS), 1e-9)
          assertEquals(points.map(_.boundaries), List(RpcSemanticConventions.CallDurationBucketBoundaries))
        }
    }
  }

  test("a successful call's output is returned unchanged") {
    forAllF { (role: RpcRole, operation: TracingServiceOperation[_, _, _, _, _], latency: FiniteDuration) =>
      recordedPoints(role, new ControlledTracingService(latency, successfulResponse.pure[IO]))(invoke(_, operation))
        .map { case (output, _) => assertEquals(output, successfulResponse) }
    }
  }

  test("a failed call re-raises the same error and records error.type as the operation's modeled error shape ID, else the error's class name") {
    forAllF { (role: RpcRole, operation: TracingServiceOperation[_, _, _, _, _], latency: FiniteDuration, failure: (Throwable, TracingServiceOperation[_, _, _, _, _] => String)) =>
      val (error, expectedErrorType) = failure
      recordedPoints(role, new ControlledTracingService(latency, error.raiseError[IO, TracingResponse]))(invoke(_, operation).attempt)
        .map { case (result, points) =>
          assert(result.left.exists(_ eq error), s"expected the original error to propagate, got $result")
          assertEquals(points.map(_.attributes), List(expectedAttributes(operation, expectedErrorType(operation).some)))
          assertEqualsDouble(points.flatMap(_.stats).map(_.sum).sum, latency.toUnit(SECONDS), 1e-9)
        }
    }
  }

  private implicit val arbDomainError: Arbitrary[DomainError] = Arbitrary {
    Gen.oneOf(Gen.const(DomainError.NotFound), Gen.alphaStr.map(DomainError.Invalid(_)))
  }

  /** The `error.type` we expect for a non-`Throwable` domain error, written out literally. */
  private def expectedDomainErrorType(error: DomainError): String =
    error match {
      case DomainError.NotFound => "com.dwolla.metrics.smithy.DomainError$NotFound$"
      case _: DomainError.Invalid => "com.dwolla.metrics.smithy.DomainError$Invalid"
    }

  test("an escaped cats-mtl raise records the raised error's type, never cats-mtl's wrapper, and still reaches its handler") {
    forAllF { (role: RpcRole, operation: TracingServiceOperation[_, _, _, _, _], latency: FiniteDuration, error: DomainError) =>
      val expectedErrorType = expectedDomainErrorType(error)
      escapedRaise(role, operation, latency, error).map { case (recovered, points) =>
        assertEquals(recovered.left.toOption, error.some)
        assertEquals(points.map(_.attributes), List(expectedAttributes(operation, expectedErrorType.some)))
      }
    }
  }

  test("an escaped cats-mtl raise of an error the operation declares records its Smithy shape ID") {
    forAllF { (role: RpcRole, operation: TracingServiceOperation[_, _, _, _, _], latency: FiniteDuration, message: String) =>
      val expectedErrorType = operation match {
        case _: ProcessRequest => "com.example.tracing#TracingError"
        case _: GetStatus => "com.example.tracing.TracingError"
      }
      escapedRaise(role, operation, latency, TracingError(message)).map { case (_, points) =>
        assertEquals(points.map(_.attributes), List(expectedAttributes(operation, expectedErrorType.some)))
      }
    }
  }

  test("errors sharing one anonymous class, like a Scala 3 enum's simple cases, record their own names") {
    forAllF { (role: RpcRole, operation: TracingServiceOperation[_, _, _, _, _], latency: FiniteDuration) =>
      for {
        notFound <- escapedRaise(role, operation, latency, StandInEnum.NotFound)
        conflict <- escapedRaise(role, operation, latency, StandInEnum.Conflict)
      } yield {
        assertEquals(notFound._2.map(_.attributes), List(expectedAttributes(operation, "com.dwolla.metrics.smithy.StandInEnum.NotFound".some)))
        assertEquals(conflict._2.map(_.attributes), List(expectedAttributes(operation, "com.dwolla.metrics.smithy.StandInEnum.Conflict".some)))
      }
    }
  }

  test("an exception merely shaped like cats-mtl's wrapper is not unwrapped") {
    forAllF { (role: RpcRole, operation: TracingServiceOperation[_, _, _, _, _], latency: FiniteDuration) =>
      val lookalike = com.example.lookalike.Submarine(DomainError.NotFound, new AnyRef)
      recordedPoints(role, new ControlledTracingService(latency, lookalike.raiseError[IO, TracingResponse]))(invoke(_, operation).attempt)
        .map { case (_, points) =>
          assertEquals(points.map(_.attributes), List(expectedAttributes(operation, "com.example.lookalike.Submarine".some)))
        }
    }
  }

  test("a call canceled mid-flight records its duration with error.type canceled") {
    forAllF { (role: RpcRole, operation: TracingServiceOperation[_, _, _, _, _], latency: FiniteDuration) =>
      val cancelAfter = latency + 1.milli
      recordedPoints(role, new ControlledTracingService(latency, IO.never))(invoke(_, operation).void.timeoutTo(cancelAfter, IO.unit))
        .map { case (_, points) =>
          assertEquals(points.map(_.attributes), List(expectedAttributes(operation, RpcSemanticConventions.CanceledErrorType.some)))
          assertEqualsDouble(points.flatMap(_.stats).map(_.sum).sum, cancelAfter.toUnit(SECONDS), 1e-9)
        }
    }
  }

  test("repeated calls aggregate into one metric, one point per rpc.method") {
    forAllF { (role: RpcRole, operations: List[TracingServiceOperation[_, _, _, _, _]], latency: FiniteDuration) =>
      recordedPoints(role, new ControlledTracingService(latency, successfulResponse.pure[IO]))(alg => operations.traverse_(invoke(alg, _)))
        .map { case (_, points) =>
          val expectedCounts = operations.groupBy(expectedRpcMethod).map { case (method, calls) => method -> calls.size.toLong }
          val recordedCounts = points.flatMap { p =>
            (p.attributes.get[String]("rpc.method").map(_.value), p.stats.map(_.count)).tupled.toList
          }.toMap
          assertEquals(points.size, expectedCounts.size)
          assertEquals(recordedCounts, expectedCounts)
        }
    }
  }

  test("durations are recorded under this library's instrumentation scope, versioned with the library") {
    forAllF { (role: RpcRole, operation: TracingServiceOperation[_, _, _, _, _]) =>
      TestControl.executeEmbed {
        MetricsTestkit.inMemory[IO]().use { testkit =>
          for {
            instrumented <- AlgebraMetrics(new ControlledTracingService(1.second, successfulResponse.pure[IO]), role)(implicitly, testkit.meterProvider, implicitly)
            _ <- invoke(instrumented, operation)
            metrics <- testkit.collectMetrics
          } yield {
            val scopes = metrics.filter(_.name == role.callDurationMetricName).map(_.instrumentationScope)
            assertEquals(scopes.map(_.name), List("com.dwolla.metrics.smithy"))
            assertEquals(scopes.map(_.version), List(BuildInfo.version.some))
          }
        }
      }
    }
  }

  test("the role selects the metric the durations are recorded to") {
    forAllF { (role: RpcRole, operation: TracingServiceOperation[_, _, _, _, _]) =>
      val otherMetric = role match {
        case RpcRole.Server => "rpc.client.call.duration"
        case RpcRole.Client => "rpc.server.call.duration"
      }
      TestControl.executeEmbed {
        MetricsTestkit.inMemory[IO]().use { testkit =>
          for {
            instrumented <- AlgebraMetrics(new ControlledTracingService(1.second, successfulResponse.pure[IO]), role)(implicitly, testkit.meterProvider, implicitly)
            _ <- invoke(instrumented, operation)
            metrics <- testkit.collectMetrics
          } yield {
            val expectedMetric = role match {
              case RpcRole.Server => "rpc.server.call.duration"
              case RpcRole.Client => "rpc.client.call.duration"
            }
            assertEquals(metrics.map(_.name).filter(_.startsWith("rpc.")), List(expectedMetric))
            assert(!metrics.exists(_.name == otherMetric))
          }
        }
      }
    }
  }

  test("withMetrics syntax instruments the algebra the same way AlgebraMetrics does") {
    forAllF { (role: RpcRole, operation: TracingServiceOperation[_, _, _, _, _], latency: FiniteDuration) =>
      TestControl.executeEmbed {
        MetricsTestkit.inMemory[IO]().use { testkit =>
          implicit val meterProvider: MeterProvider[IO] = testkit.meterProvider
          for {
            instrumented <- new ControlledTracingService(latency, successfulResponse.pure[IO]).withMetrics(role)
            _ <- invoke(instrumented, operation)
            metrics <- testkit.collectMetrics
          } yield {
            val points = histogramPoints(metrics, role.callDurationMetricName)
            assertEquals(points.map(_.attributes), List(expectedAttributes(operation, None)))
            assertEqualsDouble(points.flatMap(_.stats).map(_.sum).sum, latency.toUnit(SECONDS), 1e-9)
          }
        }
      }
    }
  }
}
