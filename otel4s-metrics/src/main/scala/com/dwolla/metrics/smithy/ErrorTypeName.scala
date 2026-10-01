package com.dwolla.metrics.smithy

/**
 * Names an error's type for `error.type`: its runtime class name, except for the simple cases of a
 * Scala 3 `enum`. Scala 3 compiles all of an enum's simple cases to one anonymous class (e.g.
 * `com.example.FooError$$anon$1`), so each is named by that class name up to its final `$$anon$<n>`
 * marker, then `$` and the case's `productPrefix`: `FooError.NotFound` is named
 * `com.example.FooError$NotFound`, consistent with a parameterized case's class, `com.example.FooError$Invalid`.
 * Any other value, including one of some other anonymous class, keeps its class name. Anonymity is
 * read from the class name, rather than `Class#isAnonymousClass`, so it works on Scala.js too.
 */
private[smithy] object ErrorTypeName {
  /** A class name ending in an anonymous-class marker, capturing everything before that final marker. */
  private val AnonymousClassName = """(.+)\$\$anon\$\d+""".r

  /** The name for a raised `null` (e.g. an escaped `raise(null)`), matching `String.valueOf(null)`; no class can be named `null`. */
  private val NullErrorName = "null"

  def apply(error: Any): String =
    if (error == null) NullErrorName
    else nonNullErrorTypeName(error)

  private def nonNullErrorTypeName(error: Any): String = {
    val className = error.getClass.getName
    (error, className) match {
      case (enumCase: Product, AnonymousClassName(enclosingName)) if ScalaEnums.isEnumCase(enumCase) && enumCase.productPrefix.nonEmpty =>
        s"$enclosingName$$${enumCase.productPrefix}"
      case _ => className
    }
  }
}
