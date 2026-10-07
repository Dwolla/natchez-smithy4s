package com.dwolla.tracing.smithy.otel4s

import smithy4s.{Document, Hints, ShapeId}

/**
 * Reads the `redacted` member of natchez-smithy4s's `com.dwolla.tracing.smithy#traceable` from a schema's
 * hints, without depending on natchez-smithy4s. The trait is found by its shape ID, and its value is read through its own
 * schema as a `Document`, so a shape annotated for the natchez backend can't leak through this one.
 *
 * The shape ID and the member name `redacted` are a permanent contract with natchez-smithy4s, which reads this
 * module's trait the same way. `cross-redaction-tests` pins both directions against the real traits.
 */
private[otel4s] object CrossTraitRedaction {
  val NatchezTraceableId: ShapeId = ShapeId("com.dwolla.tracing.smithy", "traceable")

  def fromNatchezTrait(hints: Hints): Option[String] =
    hints.toMap.get(NatchezTraceableId).map(asDocument).flatMap {
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
