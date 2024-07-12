$version: "2.0"

namespace com.dwolla.tracing.smithy

use smithy4s.meta#typeclass

@trait
@typeclass(targetType: "natchez.TraceableValue", interpreter: "com.dwolla.tracing.smithy.SchemaVisitorTraceableValue")
structure traceable {
    redacted: String
}
