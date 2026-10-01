# Natchez-Smithy4s

Utilities for integration between [Smithy4s](https://disneystreaming.github.io/smithy4s/) and both
[Natchez](https://github.com/typelevel/natchez) (tracing) and [otel4s](https://typelevel.org/otel4s/) (metrics).

## Making `natchez.TraceableValue[A]` instances available for Smithy shapes

Add the library to your build:

```scala
"com.dwolla" %% "natchez-smithy4s" % {version} 
```

Then create a file to [annotate your shapes](https://disneystreaming.github.io/smithy4s/docs/guides/model-preprocessing/#note-on-third-party-models):

```smithy
$version: "2.0"
namespace com.dwolla.example.smithy

use com.dwolla.tracing.smithy#traceable

apply CipherText @traceable
apply PlainText @traceable(redacted: "redacted plaintext value")
```

The `@traceable` trait can be applied without any modifier, in which case a `natchez.TraceableValue` instance will be generated that includes the actual value of the field.

If the `@traceable` trait is used with a `redacted` modifier, the `TraceableValue` instance will emit the passed string and not reference the actual value of the field in any way.

## Instrumenting Service Algebras for Enhanced Tracing

When working with Smithy-generated service algebras, you'll often want to gain 
deeper visibility into its operations using Natchez. This library provides
convenient syntax enhancements to automatically instrument your algebra. 
These enhancements allow you to:

1.  **Create new Natchez spans** for each operation invocation, providing a 
    clear, isolated span for each call.
2.  **Capture operation inputs** as attributes on the trace span, making it
    easier to understand the context of an operation.
3.  **Capture operation outputs** as attributes on the trace span, allowing
    you to see the result of an operation directly in your traces.

These enhancements are available as extension methods when you 
import `com.dwolla.tracing.smithy.syntax.*`.

### Core Enhancement Methods

*   `algebra.withSimpleInstrumentation()`: This is the primary method for
    creating new spans. It wraps your algebra so that a *new child span* 
    is created each time one of its methods is called. This new span
    becomes the current span for the duration of the operation.
*   `algebra.withTracedInputs()`: This method enhances your algebra to 
    add the input parameters of an operation as attributes to the *current* 
    Natchez span.
*   `algebra.withTracedOutputs()`: This method enhances your algebra to
    add the output (or any errors thrown) of an operation as attributes 
    to the *current* Natchez span.

### Combining Enhancements

You can combine these methods to achieve comprehensive tracing. The recommended 
approach for creating new spans for each operation and including its inputs and 
outputs as attributes within those new spans is as follows:
```scala
// Import the syntax enhancements
import com.dwolla.tracing.smithy.syntax.*

val algebra: MyAlgebra[IO] = new MyAlgebraImpl[IO]

// To create new spans for each operation and include inputs and outputs
// as attributes on those new spans, apply withSimpleInstrumentation last:
val instrumentedAlgebra: MyAlgebra[IO] =
  new MyAlgebraImpl[IO]
    .withTracedInputs()      // Prepare to trace inputs
    .withTracedOutputs()     // Prepare to trace outputs
    .withSimpleInstrumentation() // Create new spans, making them current for input/output tracing
```
When an operation on `instrumentedAlgebra` is called:
1. `withSimpleInstrumentation` creates a new span and makes it active.
2. `withTracedInputs` adds the operation's inputs to this new span.
3. The actual `MyAlgebraImpl` operation executes.
4. `withTracedOutputs` adds the operation's outputs (or errors) to this new span.

### Important Considerations
- **Order of Application Matters:** Enhancements are applied like layers. To ensure
  that inputs and outputs are recorded as attributes on the _new span created
  for an operation_, `withSimpleInstrumentation` should be the final enhancement 
  applied. If other enhancers (like `withTracedInputs`) wrap
  `withSimpleInstrumentation`, they would add attributes to the span that was 
  active _before_ the operation-specific span was created.
- **Selective Instrumentation:** You are not required to use all enhancements.
  - For new spans only, without input/output details:
    ``` scala
    val algebraWithSpansOnly: MyAlgebra[IO] = algebra.withSimpleInstrumentation()
    ```
  - To trace inputs/outputs to an _existing_ parent span (e.g., one created by a 
    middleware):
    ``` scala
    val algebraAddingAttributesToParentSpan: MyAlgebra[IO] =
      algebra
        .withTracedInputs()
        .withTracedOutputs()
    ```

- **Respects `@traceable` Redaction:** The `withTracedInputs` and 
  `withTracedOutputs` methods leverage `SchemaVisitorTraceableValue` internally. 
  This means that any redaction rules you've defined in your Smithy model 
  using the `@traceable(redacted = "…")` trait (as described in the "Usage" 
  section regarding annotating shapes) will be automatically respected. 
  Sensitive fields will be redacted as configured in your traces.

## Recording otel4s Metrics for Service Algebras

The `otel4s-smithy4s-metrics` module (no natchez dependency) records the duration of every call
to a Smithy-generated algebra, following the OpenTelemetry
[RPC metrics semantic conventions](https://opentelemetry.io/docs/specs/semconv/rpc/rpc-metrics/).

```scala
libraryDependencies += "com.dwolla" %% "otel4s-smithy4s-metrics" % "<version>"
```

```scala
import com.dwolla.metrics.smithy.RpcRole
import com.dwolla.metrics.smithy.syntax.*

// withMetrics needs an implicit otel4s MeterProvider[IO] in scope (e.g. from OtelJava or the otel4s SDK)
val instrumented: IO[MyAlgebra[IO]] =
  new MyAlgebraImpl[IO].withMetrics(RpcRole.Server)
```

Use `RpcRole.Server` for a service implementation and `RpcRole.Client` for a smithy4s client.
Durations are recorded in seconds to a histogram named `rpc.server.call.duration` or
`rpc.client.call.duration`, using the bucket boundaries the conventions recommend. Each
measurement has these attributes:

| Attribute         | Value                                                                              |
|-------------------|------------------------------------------------------------------------------------|
| `rpc.system.name` | `smithy`                                                                           |
| `rpc.method`      | `<namespace>.<Service>/<Operation>`, e.g. `com.dwolla.example.smithy.MyAlgebra/GetStatus` |
| `error.type`      | only on failure; see below                                                         |

A failed call's `error.type` is one of:

- `canceled` if the call was canceled;
- the Smithy shape ID of an error the operation declares, e.g. `com.dwolla.example.smithy#NotFound`;
- otherwise, the fully-qualified class name of the error raised, e.g. `java.lang.IllegalStateException`
  or, for a smithy4s client, `smithy4s.http.UnknownErrorResponse`. Values that share one anonymous
  class, like the simple cases of a Scala 3 `enum`, are named by their enclosing type and case instead,
  e.g. `com.dwolla.example.FooError.NotFound`.

An error raised through a [cats-mtl](https://typelevel.org/cats-mtl/) `Raise` (e.g. from
`Handle.allowF`) that escapes an endpoint is reported as the raised error itself — its shape ID or
type name, by the rules above — rather than as cats-mtl's internal wrapper.

On Scala.js, recognizing that wrapper and naming enum cases both rely on runtime class names, which
Scala.js keeps by default. If an application's linker rewrites them (its `runtimeClassNameMapper`
semantics), `error.type` falls back to whatever class names the linker produces.

**Instrumentation scope.** The histogram is recorded through a `Meter` this module obtains from the
`MeterProvider`, with the instrumentation scope `com.dwolla.metrics.smithy` and this library's version.
natchez-tagless's `otel4s-tagless-metrics` records the same RPC metric with an identical name, unit,
description, and buckets under its own scope, so in a service that uses both (say, for thrift and
smithy4s endpoints) the two are directly comparable, told apart by `rpc.system.name`, and a query that
aggregates by `rpc.system.name` combines both.

**Using natchez-tagless's metrics module too.** Both modules' `withMetrics` syntaxes can be imported
into the same file, and each call resolves to its own library. But `otel4s-tagless-metrics` defines its own
`com.dwolla.metrics.otel4s.RpcRole`, so wildcard-importing both `com.dwolla.metrics.smithy._` and
`com.dwolla.metrics.otel4s._` makes `RpcRole` ambiguous. Import each `RpcRole` by name instead, renaming
to tell them apart:

```scala
import com.dwolla.metrics.otel4s.syntax._
import com.dwolla.metrics.otel4s.{RpcRole => TaglessRpcRole, RpcService, RpcSystem}
import com.dwolla.metrics.smithy.syntax._
import com.dwolla.metrics.smithy.{RpcRole => SmithyRpcRole}

// a smithy4s algebra
fooService.withMetrics(SmithyRpcRole.Server)
// a cats-tagless algebra
barService.withMetrics(TaglessRpcRole.Client, RpcSystem("thrift"), RpcService("com.example.BarService"))
```

`withMetrics` returns `F[MyAlgebra[F]]` because creating the histogram is effectful, so it can't be
chained directly with the tracing enhancements above the way they chain with each other — compose it
with `.map` instead. Its timing brackets whatever algebra it wraps, so apply it to the innermost layer
you want timed:

```scala
val instrumented: IO[MyAlgebra[IO]] =
  new MyAlgebraImpl[IO]
    .withTracedInputs()
    .withMetrics(RpcRole.Server)
    .map(_.withSimpleInstrumentation())
```
