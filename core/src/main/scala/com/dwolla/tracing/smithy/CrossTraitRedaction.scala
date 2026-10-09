package com.dwolla.tracing.smithy

import cats.syntax.all.*
import smithy4s.{Document, Hints, ShapeId}

/**
 * Reads the `redacted` member of otel4s-smithy4s's `com.dwolla.tracing.smithy.otel4s#traceable` from a schema's
 * hints, without depending on otel4s-smithy4s. The trait is found by its shape ID, and its value is read through its own
 * schema as a `Document`, so a shape annotated for the otel4s backend can't leak through this one.
 *
 * The shape ID and the member name `redacted` are a permanent contract with otel4s-smithy4s, which reads this
 * module's trait the same way. `cross-redaction-tests` pins both directions against the real traits.
 */
private[smithy] object CrossTraitRedaction {
  val Otel4sTraceableId: ShapeId = ShapeId("com.dwolla.tracing.smithy.otel4s", "traceable")

  /**
   * The member's `redacted` first, then its target's. The levels are read separately, never through the merged
   * `Hints.toMap`, where a member's plain trait would replace its target's redacting one.
   */
  def fromOtel4sTrait(hints: Hints): Option[String] =
    List(hints.memberHintsMap, hints.targetHintsMap).collectFirstSome(redactedAt)

  private def redactedAt(hintsAtOneLevel: Map[ShapeId, Hints.Binding]): Option[String] =
    hintsAtOneLevel.get(Otel4sTraceableId).map(asDocument).flatMap {
      case Document.DObject(fields) => fields.get("redacted").collect { case Document.DString(redacted) => redacted }
      case _ => None
    }

  private def asDocument(binding: Hints.Binding): Document =
    binding match {
      case static: Hints.Binding.StaticBinding[?] => encodeStatic(static)
      case Hints.Binding.DynamicBinding(_, document) => document
    }

  private def encodeStatic[A](binding: Hints.Binding.StaticBinding[A]): Document =
    Document.Encoder.fromSchema(binding.key.schema).encode(binding.value)
}
