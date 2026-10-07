package com.dwolla.tracing.smithy.otel4s

import org.typelevel.otel4s.AnyValue

/** Every string in an encoded value, at any depth: the string leaves, and the keys of every map. */
object AnyValueStrings {
  def apply(value: AnyValue): List[String] =
    value match {
      case s: AnyValue.StringValue => List(s.value)
      case seq: AnyValue.SeqValue => seq.value.toList.flatMap(apply)
      case map: AnyValue.MapValue => map.value.toList.flatMap { case (key, nested) => key :: apply(nested) }
      case _ => Nil
    }
}
