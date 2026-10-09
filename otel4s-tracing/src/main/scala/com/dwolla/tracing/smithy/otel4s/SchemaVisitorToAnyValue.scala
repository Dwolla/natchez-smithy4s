package com.dwolla.tracing.smithy.otel4s

import cats.syntax.all.*
import com.dwolla.tracing.otel4s.ToAnyValue
import org.typelevel.otel4s.AnyValue
import smithy.api.TimestampFormat.DATE_TIME
import smithy4s.capability.EncoderK
import smithy4s.schema.*
import smithy4s.{Schema, *}

/**
 * Derives an otel4s-tagless `ToAnyValue` from a smithy4s `Schema`. This is the interpreter smithy4s's codegen calls
 * for every shape annotated `@com.dwolla.tracing.smithy.otel4s#traceable`, and the encoder the algebra wrappers use
 * for endpoint inputs and outputs.
 *
 * There is deliberately no generic fallback: every node that carries hints checks for a `redacted` value before
 * reading the value at all, so a redaction is honored wherever it sits: on a member or its target, and inside
 * options, lists, sets, map keys and values, unions, and recursive shapes.
 */
object SchemaVisitorToAnyValue extends CachedSchemaCompiler.Impl[ToAnyValue] {
  override protected type Aux[A] = ToAnyValue[A]

  override def fromSchema[A](schema: Schema[A], cache: CompilationCache[ToAnyValue]): ToAnyValue[A] =
    schema.compile(new SchemaVisitorToAnyValue(cache))

  /** Lists, sets, and maps record at most this many elements or entries, then say how many more there were. */
  private[otel4s] val CollectionElementLimit: Int = 5

  /** The key of the extra entry a truncated map records, whose value says how many entries weren't recorded. */
  private[otel4s] val TruncatedMapKey: String = "(truncated)"
}

class SchemaVisitorToAnyValue(override protected val cache: CompilationCache[ToAnyValue]) extends SchemaVisitor.Cached[ToAnyValue] { self =>
  /**
   * This module's own `redacted` first (on the member, then on its target); natchez-smithy4s's only if this one sets
   * none (see [[CrossTraitRedaction]]). Each level is read separately, because smithy4s's merged view of the hints
   * lets a member's plain `@traceable` replace its target's redacting one.
   */
  private def maybeRedact[A](hints: Hints): Option[ToAnyValue[A]] =
    List(hints.memberHints, hints.targetHints)
      .collectFirstSome(_.get(Traceable.tagInstance).flatMap(_.redacted))
      .orElse(CrossTraitRedaction.fromNatchezTrait(hints))
      .map(redacted => ToAnyValue.instance[A](_ => AnyValue.string(redacted)))

  private implicit val anyValueEncoderK: EncoderK[ToAnyValue, AnyValue] = new EncoderK[ToAnyValue, AnyValue] {
    override def apply[A](fa: ToAnyValue[A], a: A): AnyValue = fa.toAnyValue(a)
    override def absorb[A](f: A => AnyValue): ToAnyValue[A] = ToAnyValue.instance(f)
  }

  override def primitive[P](shapeId: ShapeId, hints: Hints, tag: Primitive[P]): ToAnyValue[P] =
    maybeRedact[P](hints).getOrElse {
      implicit val blobToAnyValue: ToAnyValue[Blob] = ToAnyValue.instance(blob => AnyValue.string(blob.toBase64String))
      implicit val documentToAnyValue: ToAnyValue[Document] = ToAnyValue.instance(documentAsAnyValue)
      implicit val timestampToAnyValue: ToAnyValue[Timestamp] = ToAnyValue.instance(timestamp => AnyValue.string(timestamp.format(DATE_TIME)))

      Primitive.deriving[ToAnyValue].apply(tag)
    }

  private def documentAsAnyValue(document: Document): AnyValue =
    document match {
      case Document.DString(value) => AnyValue.string(value)
      case Document.DBoolean(value) => AnyValue.boolean(value)
      case Document.DNumber(value) => ToAnyValue.bigDecimalToAnyValue.toAnyValue(value)
      case Document.DNull => AnyValue.empty
      case Document.DArray(values) => AnyValue.seq(values.map(documentAsAnyValue))
      case Document.DObject(fields) => AnyValue.map(fields.map { case (key, value) => key -> documentAsAnyValue(value) })
    }

  override def collection[C[_], A](shapeId: ShapeId, hints: Hints, tag: CollectionTag[C], member: Schema[A]): ToAnyValue[C[A]] =
    maybeRedact[C[A]](hints).getOrElse {
      val memberToAnyValue = self(member)
      val limit = SchemaVisitorToAnyValue.CollectionElementLimit

      ToAnyValue.instance[C[A]] { as =>
        val recorded = tag.iterator(as).take(limit).map(memberToAnyValue.toAnyValue).toVector
        val unrecorded = tag.iterator(as).size - limit
        AnyValue.seq(if (unrecorded > 0) recorded :+ AnyValue.string(s"and $unrecorded more") else recorded)
      }
    }

  override def map[K, V](shapeId: ShapeId, hints: Hints, key: Schema[K], value: Schema[V]): ToAnyValue[Map[K, V]] =
    maybeRedact[Map[K, V]](hints).getOrElse {
      val keyToAnyValue = self(key)
      val valueToAnyValue = self(value)

      val limit = SchemaVisitorToAnyValue.CollectionElementLimit

      ToAnyValue.instance[Map[K, V]] { m =>
        val recorded = m.iterator.take(limit).map { case (k, v) => mapKey(keyToAnyValue.toAnyValue(k)) -> valueToAnyValue.toAnyValue(v) }.toMap
        val unrecorded = m.size - limit
        AnyValue.map(if (unrecorded > 0) recorded + (SchemaVisitorToAnyValue.TruncatedMapKey -> AnyValue.string(s"and $unrecorded more")) else recorded)
      }
    }

  /** `AnyValue` map keys are strings: the same rendering otel4s-tagless uses for its own map instances. */
  private def mapKey(key: AnyValue): String =
    key match {
      case s: AnyValue.StringValue => s.value
      case l: AnyValue.LongValue => l.value.toString
      case d: AnyValue.DoubleValue => d.value.toString
      case b: AnyValue.BooleanValue => b.value.toString
      case other => other.show
    }

  override def enumeration[E](shapeId: ShapeId, hints: Hints, tag: EnumTag[E], values: List[EnumValue[E]], total: E => EnumValue[E]): ToAnyValue[E] =
    maybeRedact[E](hints).getOrElse {
      tag match {
        case EnumTag.ClosedIntEnum | EnumTag.OpenIntEnum(_) =>
          ToAnyValue.instance[E](e => AnyValue.long(total(e).intValue.toLong))
        case EnumTag.ClosedStringEnum | EnumTag.OpenStringEnum(_) =>
          ToAnyValue.instance[E](e => AnyValue.string(total(e).stringValue))
      }
    }

  override def struct[S](shapeId: ShapeId, hints: Hints, fields: Vector[Field[S, ?]], make: IndexedSeq[Any] => S): ToAnyValue[S] =
    maybeRedact[S](hints).getOrElse {
      if (shapeId == Schema.unit.shapeId) ToAnyValue.instance[S](_ => AnyValue.empty)
      else {
        val fieldEncoders = fields.map(fieldEncoder(_))
        ToAnyValue.instance[S](s => AnyValue.map(fieldEncoders.map(_(s)).toMap))
      }
    }

  private def fieldEncoder[S, A](field: Field[S, A]): S => (String, AnyValue) = {
    val fieldToAnyValue = self(field.schema)
    s => field.label -> fieldToAnyValue.toAnyValue(field.get(s))
  }

  override def union[U](shapeId: ShapeId, hints: Hints, alternatives: Vector[Alt[U, ?]], dispatch: Alt.Dispatcher[U]): ToAnyValue[U] =
    maybeRedact[U](hints).getOrElse {
      val precompiler = new Alt.Precompiler[ToAnyValue] {
        override def apply[A](label: String, schema: Schema[A]): ToAnyValue[A] = {
          val alternativeToAnyValue = self(schema)
          ToAnyValue.instance[A](a => AnyValue.map(Map(label -> alternativeToAnyValue.toAnyValue(a))))
        }
      }

      dispatch.compile(precompiler)
    }

  override def biject[A, B](schema: Schema[A], bijection: Bijection[A, B]): ToAnyValue[B] = {
    val underlying = self(schema)
    ToAnyValue.instance[B](b => underlying.toAnyValue(bijection.from(b)))
  }

  override def refine[A, B](schema: Schema[A], refinement: Refinement[A, B]): ToAnyValue[B] = {
    val underlying = self(schema)
    ToAnyValue.instance[B](b => underlying.toAnyValue(refinement.from(b)))
  }

  /** Compiled on first use, not here: a recursive shape refers back to itself through this node. */
  override def lazily[A](suspend: Lazy[Schema[A]]): ToAnyValue[A] = {
    val compiled = suspend.map(self(_))
    ToAnyValue.instance[A](a => compiled.value.toAnyValue(a))
  }

  override def option[A](schema: Schema[A]): ToAnyValue[Option[A]] =
    maybeRedact[Option[A]](schema.hints).getOrElse {
      val underlying = self(schema)
      ToAnyValue.instance[Option[A]](_.fold[AnyValue](AnyValue.empty)(underlying.toAnyValue))
    }
}
