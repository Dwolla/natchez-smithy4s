package com.dwolla.metrics.smithy

import cats.effect.IO
import cats.effect.testkit.TestControl
import cats.syntax.all.*
import com.example.tracing.*
import com.example.tracing.TracingServiceOperation.*
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import org.scalacheck.{Arbitrary, Gen}
import org.scalacheck.effect.PropF.forAllF
import org.typelevel.otel4s.{Attribute, Attributes}
import org.typelevel.otel4s.sdk.metrics.data.{MetricData, MetricPoints, PointData}
import org.typelevel.otel4s.sdk.testkit.metrics.MetricsTestkit

import scala.concurrent.duration.*

class AlgebraMetricsTest
  extends CatsEffectSuite
    with ScalaCheckEffectSuite
    with TracingServiceArbitraries {

  private val successfulResponse = TracingResponse("id", 1, None)

  private implicit val arbRpcRole: Arbitrary[RpcRole] = Arbitrary(Gen.oneOf(RpcRole.Server, RpcRole.Client))

  private implicit val arbLatency: Arbitrary[FiniteDuration] = Arbitrary(Gen.chooseNum(0L, 30000L).map(_.millis))

  /** A failure paired with the `error.type` we expect for it, written out literally. */
  private implicit val arbFailure: Arbitrary[(Throwable, String)] = Arbitrary {
    Gen.oneOf(
      Gen.alphaStr.map(msg => (new RuntimeException(msg), "java.lang.RuntimeException")),
      Gen.alphaStr.map(msg => (new IllegalStateException(msg), "java.lang.IllegalStateException")),
      for {
        msg <- Gen.alphaStr
        code <- Gen.option(Gen.posNum[Int])
      } yield (TracingError(msg, code), "com.example.tracing.TracingError"),
    )
  }

  private def expectedRpcMethod(operation: TracingServiceOperation[_, _, _, _, _]): String =
    operation match {
      case _: GetStatus => "com.example.tracing.TracingService/GetStatus"
      case _: ProcessRequest => "com.example.tracing.TracingService/ProcessRequest"
    }

  private def invoke(alg: TracingService[IO], operation: TracingServiceOperation[_, _, _, _, _]): IO[Any] =
    TracingService.toPolyFunction(alg).apply(operation).widen[Any]

  private def expectedAttributes(operation: TracingServiceOperation[_, _, _, _, _], errorType: Option[String]): Attributes =
    Attributes.fromSpecific(
      List[Attribute[_]](
        Attribute("rpc.system.name", "smithy"),
        Attribute("rpc.method", expectedRpcMethod(operation)),
      ) ++ errorType.map(Attribute("error.type", _)).toList
    )

  /**
   * Instruments `impl` for `role` against a fresh in-memory SDK, runs `calls` under virtual time,
   * and returns the collected call-duration histogram points (plus whatever `calls` returned).
   */
  private def recordedPoints[A](role: RpcRole, impl: TracingService[IO])
                               (calls: TracingService[IO] => IO[A]): IO[(A, List[PointData.Histogram])] =
    TestControl.executeEmbed {
      MetricsTestkit.inMemory[IO]().use { testkit =>
        for {
          meter <- testkit.meterProvider.get("AlgebraMetricsTest")
          instrumented <- AlgebraMetrics(impl, role)(implicitly, meter, implicitly)
          a <- calls(instrumented)
          metrics <- testkit.collectMetrics
        } yield (a, histogramPoints(metrics, role.callDurationMetricName))
      }
    }

  private def histogramPoints(metrics: List[MetricData], name: String): List[PointData.Histogram] =
    metrics.filter(_.name == name).flatMap { metric =>
      metric.data match {
        case histogram: MetricPoints.Histogram => histogram.points.toVector.toList
        case _ => Nil
      }
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

  test("a failed call re-raises the same error and records error.type as the error's class name") {
    forAllF { (role: RpcRole, operation: TracingServiceOperation[_, _, _, _, _], latency: FiniteDuration, failure: (Throwable, String)) =>
      val (error, expectedErrorType) = failure
      recordedPoints(role, new ControlledTracingService(latency, error.raiseError[IO, TracingResponse]))(invoke(_, operation).attempt)
        .map { case (result, points) =>
          assert(result.left.exists(_ eq error), s"expected the original error to propagate, got $result")
          assertEquals(points.map(_.attributes), List(expectedAttributes(operation, expectedErrorType.some)))
          assertEqualsDouble(points.flatMap(_.stats).map(_.sum).sum, latency.toUnit(SECONDS), 1e-9)
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
          assertEquals(recordedCounts, expectedCounts)
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
            meter <- testkit.meterProvider.get("AlgebraMetricsTest")
            instrumented <- AlgebraMetrics(new ControlledTracingService(1.second, successfulResponse.pure[IO]): TracingService[IO], role)(implicitly, meter, implicitly)
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
}
