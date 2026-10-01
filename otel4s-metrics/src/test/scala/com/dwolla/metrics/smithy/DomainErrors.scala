package com.dwolla.metrics.smithy

import scala.util.control.NoStackTrace

/** A domain error type that isn't a `Throwable`, as a service would raise through cats-mtl's `Raise`. */
sealed trait DomainError
object DomainError {
  case object NotFound extends DomainError
  final case class Invalid(reason: String) extends DomainError
}

/**
 * Looks like a Scala 3 enum's simple cases (its values share one anonymous runtime class and differ
 * only in `productPrefix`) but isn't a `scala.reflect.Enum`, so its values keep their class name.
 */
sealed abstract class EnumLookalike extends Product with Serializable
object EnumLookalike {
  val NotFound: EnumLookalike = simpleCase("NotFound")
  val Conflict: EnumLookalike = simpleCase("Conflict")

  private def simpleCase(name: String): EnumLookalike =
    new EnumLookalike {
      override def productPrefix: String = name
      override def productArity: Int = 0
      override def productElement(n: Int): Any = throw new IndexOutOfBoundsException(n.toString)
      override def canEqual(that: Any): Boolean = that.isInstanceOf[EnumLookalike]
    }
}

/** A plain exception, thrown in [[ErrorCreator]] as an anonymous subclass. */
case class PlainError(message: String) extends RuntimeException(message)

object ErrorCreator {
  def withoutStackTrace(message: String): Throwable = new PlainError(message) with NoStackTrace
}

/** Holds an exception whose class is nested inside an anonymous class. */
trait NestedErrorBox {
  def error: Throwable
}
object AnonymousHolder {
  val nestedError: Throwable =
    new NestedErrorBox {
      final class LocalError(message: String) extends RuntimeException(message)
      override val error: Throwable = new LocalError("nested")
    }.error
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
