package com.dwolla.metrics.smithy

/**
 * Names an error's type for `error.type`: its runtime class name, except that values sharing one
 * anonymous class are told apart by their `productPrefix`. Scala 3 compiles every simple case of an
 * `enum` to the same anonymous class (e.g. `com.example.FooError$$anon$1`), so `FooError.NotFound`
 * is named `com.example.FooError.NotFound`. Anonymity is read from the class name, rather than
 * `Class#isAnonymousClass`, so it works on Scala.js too.
 */
private[smithy] object ErrorTypeName {
  private val AnonymousClassMarker = "$$anon$"

  def apply(error: Any): String = {
    val className = error.getClass.getName
    val anonymousAt = className.indexOf(AnonymousClassMarker)
    error match {
      case product: Product if anonymousAt > 0 => s"${className.substring(0, anonymousAt)}.${product.productPrefix}"
      case _ => className
    }
  }
}
