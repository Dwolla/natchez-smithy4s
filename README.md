# Natchez-Smithy4s

Utilities for integration between [Natchez](https://github.com/typelevel/natchez) and [Smithy4s](https://disneystreaming.github.io/smithy4s/).

## Usage

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
