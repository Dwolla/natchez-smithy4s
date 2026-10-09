package com.dwolla.tracing.smithy.otel4s

/** A domain error that isn't a `Throwable`, as a service would raise through cats-mtl's `Raise`. */
sealed trait DomainError
object DomainError {
  case object NotFound extends DomainError
}
