package com.dwolla.metrics.smithy

import munit.FunSuite
import org.typelevel.otel4s.semconv.MetricSpec
import org.typelevel.otel4s.semconv.experimental.metrics.RpcExperimentalMetrics.{ClientCallDuration, ServerCallDuration}

class RpcSemanticConventionsTest extends FunSuite {

  private val roles: List[(RpcRole, MetricSpec)] = List(
    RpcRole.Server -> ServerCallDuration,
    RpcRole.Client -> ClientCallDuration,
  )

  roles.foreach { case (role, spec) =>
    test(s"$role call duration metric matches ${spec.name} from the OpenTelemetry semantic conventions") {
      assertEquals(role.callDurationMetricName, spec.name)
      assertEquals(role.callDurationDescription, spec.description)
      assertEquals(RpcSemanticConventions.CallDurationUnit, spec.unit)
    }
  }

  test("attribute keys match the OpenTelemetry semantic conventions for both roles") {
    assertEquals(RpcSemanticConventions.RpcSystemName, ServerCallDuration.AttributeSpecs.rpcSystemName.key)
    assertEquals(RpcSemanticConventions.RpcMethod, ServerCallDuration.AttributeSpecs.rpcMethod.key)
    assertEquals(RpcSemanticConventions.ErrorType, ServerCallDuration.AttributeSpecs.errorType.key)

    assertEquals(RpcSemanticConventions.RpcSystemName, ClientCallDuration.AttributeSpecs.rpcSystemName.key)
    assertEquals(RpcSemanticConventions.RpcMethod, ClientCallDuration.AttributeSpecs.rpcMethod.key)
    assertEquals(RpcSemanticConventions.ErrorType, ClientCallDuration.AttributeSpecs.errorType.key)
  }

  test("every attribute we set is one the semantic conventions define for the metric") {
    val ourKeys = Set(RpcSemanticConventions.RpcSystemName, RpcSemanticConventions.RpcMethod, RpcSemanticConventions.ErrorType)
    List(ServerCallDuration, ClientCallDuration).foreach { spec =>
      val specKeys = spec.attributeSpecs.map(_.key).toSet[Any]
      assert(ourKeys.forall(specKeys.contains), s"${spec.name} does not define ${ourKeys.filterNot(specKeys.contains)}")
    }
  }
}
