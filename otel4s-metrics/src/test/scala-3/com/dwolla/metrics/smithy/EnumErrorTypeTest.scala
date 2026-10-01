package com.dwolla.metrics.smithy

import com.example.tracing.TracingServiceOperation
import org.scalacheck.effect.PropF.forAllF

import scala.concurrent.duration.FiniteDuration

enum FooError {
  case NotFound, Conflict
}

class EnumErrorTypeTest extends AlgebraMetricsSuite {
  test("an escaped raise of a Scala 3 enum's simple case records the case's name, distinct from its siblings") {
    forAllF { (role: RpcRole, operation: TracingServiceOperation[?, ?, ?, ?, ?], latency: FiniteDuration) =>
      for {
        notFound <- escapedRaise(role, operation, latency, FooError.NotFound)
        conflict <- escapedRaise(role, operation, latency, FooError.Conflict)
      } yield {
        assertEquals(notFound._2.map(_.attributes), List(expectedAttributes(operation, Some("com.dwolla.metrics.smithy.FooError.NotFound"))))
        assertEquals(conflict._2.map(_.attributes), List(expectedAttributes(operation, Some("com.dwolla.metrics.smithy.FooError.Conflict"))))
      }
    }
  }
}
