package com.dwolla.metrics.smithy

/** A domain error type that isn't a `Throwable`, as a service would raise through cats-mtl's `Raise`. */
sealed trait DomainError
object DomainError {
  case object NotFound extends DomainError
  final case class Invalid(reason: String) extends DomainError
}

/**
 * Stands in for a Scala 3 enum on every Scala version: like an enum's simple cases, its values share
 * one anonymous runtime class and differ only in `productPrefix`.
 */
sealed abstract class StandInEnum extends Product with Serializable
object StandInEnum {
  val NotFound: StandInEnum = simpleCase("NotFound")
  val Conflict: StandInEnum = simpleCase("Conflict")

  private def simpleCase(name: String): StandInEnum =
    new StandInEnum {
      override def productPrefix: String = name
      override def productArity: Int = 0
      override def productElement(n: Int): Any = throw new IndexOutOfBoundsException(n.toString)
      override def canEqual(that: Any): Boolean = that.isInstanceOf[StandInEnum]
    }
}

/** An anonymous `Product` that leaves `productPrefix` at its default, empty value. */
object UnlabeledProduct {
  val value: Product =
    new Product {
      override def productArity: Int = 0
      override def productElement(n: Int): Any = throw new IndexOutOfBoundsException(n.toString)
      override def canEqual(that: Any): Boolean = false
    }
}
