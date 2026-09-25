package com.dwolla.metrics.smithy

/**
 * Which side of a Remote Procedure Call an instrumented algebra represents. A smithy4s server
 * implementation and a smithy4s client have the same type, so the caller must say which one
 * they are instrumenting; it selects the OpenTelemetry metric the call durations are recorded to.
 */
sealed abstract class RpcRole private[smithy] (private[smithy] val callDurationMetricName: String,
                                               private[smithy] val callDurationDescription: String) extends Product with Serializable

object RpcRole {
  /** The algebra is a server-side implementation handling incoming calls. */
  case object Server extends RpcRole(
    "rpc.server.call.duration",
    "Measures the duration of an incoming Remote Procedure Call (RPC).",
  )

  /** The algebra is a client making outgoing calls. */
  case object Client extends RpcRole(
    "rpc.client.call.duration",
    "Measures the duration of an outgoing Remote Procedure Call (RPC).",
  )
}
