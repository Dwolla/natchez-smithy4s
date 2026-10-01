package com.example.lookalike

/** Shaped like cats-mtl's private `Handle.Submarine`, but not it, so it must not be unwrapped. */
final case class Submarine[E](e: E, marker: AnyRef) extends RuntimeException
