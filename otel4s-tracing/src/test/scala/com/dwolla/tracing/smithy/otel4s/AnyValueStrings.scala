package com.dwolla.tracing.smithy.otel4s

import org.typelevel.otel4s.AnyValue

import java.nio.charset.StandardCharsets

/**
 * Every string in an encoded value, at any depth: the string leaves, byte arrays decoded as UTF-8 (so a secret
 * recorded as bytes is still found), and the keys of every map. Numbers, booleans, and the empty value hold no string.
 */
object AnyValueStrings {
  def apply(value: AnyValue): List[String] =
    value match {
      case s: AnyValue.StringValue => List(s.value)
      case bytes: AnyValue.ByteArrayValue => List(StandardCharsets.UTF_8.decode(bytes.value).toString)
      case seq: AnyValue.SeqValue => seq.value.toList.flatMap(apply)
      case map: AnyValue.MapValue => map.value.toList.flatMap { case (key, nested) => key :: apply(nested) }
      case _: AnyValue.LongValue => Nil
      case _: AnyValue.DoubleValue => Nil
      case _: AnyValue.BooleanValue => Nil
      case _: AnyValue.EmptyValue => Nil
    }
}
