$version: "2.0"

namespace com.dwolla.tracing.smithy.otel4s

use smithy4s.meta#typeclass

/// Marks a shape or member for otel4s tracing: smithy4s generates an otel4s-tagless `ToAnyValue` instance for each
/// annotated shape. With `redacted`, the value is recorded as that string and never read.
@trait
@typeclass(targetType: "com.dwolla.tracing.otel4s.ToAnyValue", interpreter: "com.dwolla.tracing.smithy.otel4s.SchemaVisitorToAnyValue")
structure traceable {
    redacted: String
}
