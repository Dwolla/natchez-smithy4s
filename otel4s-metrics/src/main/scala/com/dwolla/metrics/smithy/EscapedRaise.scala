package com.dwolla.metrics.smithy

import cats.syntax.all.*

/**
 * Recognizes an error raised through a cats-mtl `Raise` (e.g. one from `Handle.allowF`) that escaped
 * an endpoint before its handler recovered it. cats-mtl encodes such a raise as its private
 * `Handle.Submarine` case class wrapping the raised error; it's matched by runtime class name so this
 * module needs no cats-mtl dependency, and so that look-alikes elsewhere aren't unwrapped.
 */
private[smithy] object EscapedRaise {
  private val SubmarineClassName = "cats.mtl.Handle$Submarine"

  /** The raised error, if `error` is cats-mtl's encoding of an escaped raise. */
  def unapply(error: Throwable): Option[Any] =
    error match {
      case submarine: Product if submarine.getClass.getName == SubmarineClassName && submarine.productArity > 0 =>
        submarine.productElement(0).some
      case _ => None
    }
}
