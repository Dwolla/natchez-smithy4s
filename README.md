# Natchez-Smithy4s

Utilities for integration between [Natchez](https://github.com/typelevel/natchez) and [Smithy4s](https://disneystreaming.github.io/smithy4s/).

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
