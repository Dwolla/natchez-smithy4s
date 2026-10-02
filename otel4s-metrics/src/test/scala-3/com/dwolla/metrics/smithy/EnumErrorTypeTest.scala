package com.dwolla.metrics.smithy

import cats.syntax.all.*
import com.example.tracing.TracingServiceOperation
import org.scalacheck.effect.PropF.forAllF

import scala.concurrent.duration.FiniteDuration

enum FooError {
  case NotFound, Conflict
  case Invalid(reason: String)
}

object Errors {
  enum BarError {
    case Missing
  }
}

class EnumErrorTypeTest extends AlgebraMetricsSuite {
  test("an escaped raise of a Scala 3 enum's simple case records the case's name, distinct from its siblings") {
    forAllF { (role: RpcRole, operation: TracingServiceOperation[?, ?, ?, ?, ?], latency: FiniteDuration) =>
      for {
        notFound <- escapedRaise(role, operation, latency, FooError.NotFound)
        conflict <- escapedRaise(role, operation, latency, FooError.Conflict)
      } yield {
        assertEquals(notFound._2.map(_.attributes), List(expectedAttributes(operation, "com.dwolla.metrics.smithy.FooError$NotFound".some)))
        assertEquals(conflict._2.map(_.attributes), List(expectedAttributes(operation, "com.dwolla.metrics.smithy.FooError$Conflict".some)))
      }
    }
  }

  test("an enum's simple and parameterized cases share the enum's name as a prefix") {
    forAllF { (role: RpcRole, operation: TracingServiceOperation[?, ?, ?, ?, ?], latency: FiniteDuration, reason: String) =>
      for {
        notFound <- escapedRaise(role, operation, latency, FooError.NotFound)
        invalid <- escapedRaise(role, operation, latency, FooError.Invalid(reason))
      } yield {
        assertEquals(notFound._2.map(_.attributes), List(expectedAttributes(operation, "com.dwolla.metrics.smithy.FooError$NotFound".some)))
        assertEquals(invalid._2.map(_.attributes), List(expectedAttributes(operation, "com.dwolla.metrics.smithy.FooError$Invalid".some)))
      }
    }
  }

  test("a simple case of an enum nested in an object is named with '$' throughout") {
    forAllF { (role: RpcRole, operation: TracingServiceOperation[?, ?, ?, ?, ?], latency: FiniteDuration) =>
      escapedRaise(role, operation, latency, Errors.BarError.Missing).map { case (_, points) =>
        assertEquals(points.map(_.attributes), List(expectedAttributes(operation, "com.dwolla.metrics.smithy.Errors$BarError$Missing".some)))
      }
    }
  }
}
