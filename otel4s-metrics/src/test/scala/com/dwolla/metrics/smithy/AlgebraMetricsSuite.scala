package com.dwolla.metrics.smithy

import cats.effect.IO
import cats.effect.testkit.TestControl
import cats.mtl.Handle
import cats.syntax.all.*
import com.example.tracing.*
import com.example.tracing.TracingServiceOperation.*
import munit.{CatsEffectSuite, ScalaCheckEffectSuite}
import org.scalacheck.{Arbitrary, Gen}
import org.typelevel.otel4s.{Attribute, Attributes}
import org.typelevel.otel4s.sdk.metrics.data.{MetricData, MetricPoints, PointData}
import org.typelevel.otel4s.sdk.testkit.metrics.MetricsTestkit

import scala.concurrent.duration.*

/** Fixtures for suites that instrument a `TracingService` and inspect the durations it records. */
abstract class AlgebraMetricsSuite
  extends CatsEffectSuite
    with ScalaCheckEffectSuite
    with TracingServiceArbitraries {

  protected val successfulResponse: TracingResponse = TracingResponse("id", 1, None)

  protected implicit val arbRpcRole: Arbitrary[RpcRole] = Arbitrary(Gen.oneOf(RpcRole.Server, RpcRole.Client))

  protected implicit val arbLatency: Arbitrary[FiniteDuration] = Arbitrary(Gen.chooseNum(0L, 30000L).map(_.millis))

  protected def expectedRpcMethod(operation: TracingServiceOperation[_, _, _, _, _]): String =
    operation match {
      case _: GetStatus => "com.example.tracing.TracingService/GetStatus"
      case _: ProcessRequest => "com.example.tracing.TracingService/ProcessRequest"
    }

  protected def invoke(alg: TracingService[IO], operation: TracingServiceOperation[_, _, _, _, _]): IO[Any] =
    TracingService.toPolyFunction(alg).apply(operation).widen[Any]

  protected def expectedAttributes(operation: TracingServiceOperation[_, _, _, _, _], errorType: Option[String]): Attributes =
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
  protected def recordedPoints[A](role: RpcRole, impl: TracingService[IO])
                                 (calls: TracingService[IO] => IO[A]): IO[(A, List[PointData.Histogram])] =
    TestControl.executeEmbed {
      MetricsTestkit.inMemory[IO]().use { testkit =>
        for {
          instrumented <- AlgebraMetrics(impl, role)(implicitly, testkit.meterProvider, implicitly)
          a <- calls(instrumented)
          metrics <- testkit.collectMetrics
        } yield (a, histogramPoints(metrics, role.callDurationMetricName))
      }
    }

  /**
   * Inside a cats-mtl `Handle.allowF` scope, an instrumented endpoint raises `error` through the
   * scope's `Raise`, and the raise escapes the endpoint to be recovered by the enclosing scope.
   * Returns what that scope recovered (the raised `error`, if instrumentation propagated cats-mtl's
   * encoding unchanged) and the recorded call-duration histogram points.
   */
  protected def escapedRaise[E](role: RpcRole,
                                operation: TracingServiceOperation[_, _, _, _, _],
                                latency: FiniteDuration,
                                error: E): IO[(Either[E, Any], List[PointData.Histogram])] =
    for {
      recorded <- IO.ref(List.empty[PointData.Histogram])
      recovered <- Handle.allowF[IO, E] { raise =>
        recordedPoints(role, new ControlledTracingService(latency, raise.raise[E, TracingResponse](error)))(invoke(_, operation).attempt)
          .flatMap { case (escaped, points) => recorded.set(points) >> escaped.liftTo[IO] }
      }.attempt
      points <- recorded.get
    } yield (recovered, points)

  protected def histogramPoints(metrics: List[MetricData], name: String): List[PointData.Histogram] =
    metrics.filter(_.name == name).flatMap { metric =>
      metric.data match {
        case histogram: MetricPoints.Histogram => histogram.points.toVector.toList
        case _ => Nil
      }
    }
}
