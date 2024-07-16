package com.dwolla.tracing.smithy

import natchez.{TraceValue, TraceableValue}
import smithy.api.TimestampFormat.DATE_TIME
import smithy4s.capability.EncoderK
import smithy4s.json.*
import smithy4s.schema.*
import smithy4s.{Schema, *}

object SchemaVisitorTraceableValue extends CachedSchemaCompiler.Impl[TraceableValue] {
  override protected type Aux[A] = TraceableValue[A]

  override def fromSchema[A](schema: Schema[A], cache: CompilationCache[TraceableValue]): TraceableValue[A] =
    schema.compile(new SchemaVisitorTraceableValue(cache))
}

class SchemaVisitorTraceableValue(override protected val cache: CompilationCache[TraceableValue]) extends SchemaVisitor.Cached[TraceableValue] { self =>
  private def maybeRedact[A](hints: Hints): Option[TraceableValue[A]] =
    hints.get(Traceable.tagInstance)
      .flatMap(_.redacted)
      .map(r => TraceableValue[String].contramap((_: A) => r))

  private def aToStringViaTraceableValue[A : TraceableValue](a: A): String =
    TraceableValue[A].toTraceValue(a) match {
      case TraceValue.StringValue(s) => s
      case TraceValue.NumberValue(n) => n.toString
      case TraceValue.BooleanValue(b) => b.toString
    }

  private implicit val traceValueEncoderK: EncoderK[TraceableValue, TraceValue] = new EncoderK[TraceableValue, TraceValue] {
    override def apply[A](fa: TraceableValue[A], a: A): TraceValue = fa.toTraceValue(a)
    override def absorb[A](f: A => TraceValue): TraceableValue[A] = f(_)
  }

  override def primitive[P](shapeId: ShapeId,
                            hints: Hints,
                            tag: Primitive[P]): TraceableValue[P] =
    maybeRedact[P](hints)
      .getOrElse {
        implicit val traceableShort: TraceableValue[Short] = TraceValue.NumberValue(_)
        implicit val traceableBigInt: TraceableValue[BigInt] = TraceValue.NumberValue(_)
        implicit val traceableBigDecimal: TraceableValue[BigDecimal] = TraceValue.NumberValue(_)
        implicit val traceableUUID: TraceableValue[java.util.UUID] = a => TraceValue.StringValue(a.toString)
        implicit val traceableByte: TraceableValue[Byte] = TraceValue.NumberValue(_)
        implicit val traceableBlob: TraceableValue[Blob] = a => TraceValue.StringValue(a.toBase64String)
        implicit val traceableDocument: TraceableValue[Document] = a => TraceValue.StringValue(Json.writeDocumentAsBlob(a).toUTF8String)
        implicit val traceableTimestamp: TraceableValue[Timestamp] = a => TraceValue.StringValue(a.format(DATE_TIME))

        Primitive.deriving[TraceableValue].apply(tag)
      }

  override def collection[C[_], A](shapeId: ShapeId,
                                   hints: Hints,
                                   tag: CollectionTag[C],
                                   member: Schema[A]): TraceableValue[C[A]] = {
    implicit val memberTV: TraceableValue[A] = self(member)

    maybeRedact[C[A]](hints)
      .getOrElse { (as: C[A]) =>
        val limit = 5
        val size = tag.iterator(as).size
        if (size < limit) tag.iterator(as).map(aToStringViaTraceableValue[A]).mkString("[", ", ", "]")
        else tag.iterator(as).take(limit).map(aToStringViaTraceableValue[A]).mkString("[", ", ", "") + s", and ${size - limit} more]"
      }
  }

  override def map[K, V](shapeId: ShapeId,
                         hints: Hints,
                         key: Schema[K],
                         value: Schema[V]): TraceableValue[Map[K, V]] =
    maybeRedact[Map[K, V]](hints).getOrElse {
      implicit val tvK: TraceableValue[K] = self(key)
      implicit val tvV: TraceableValue[V] = self(value)

      _.toList
        .map { case (k, v) =>
          List(aToStringViaTraceableValue(k), aToStringViaTraceableValue(v)).mkString(" -> ")
        }
        .mkString("[", ", ", "]")
    }

  override def enumeration[E](shapeId: ShapeId,
                              hints: Hints,
                              tag: EnumTag[E],
                              values: List[EnumValue[E]],
                              total: E => EnumValue[E]): TraceableValue[E] =
    maybeRedact[E](hints).getOrElse {
      val tvE: TraceableValue[EnumValue[E]] = tag match {
        case EnumTag.ClosedIntEnum | EnumTag.OpenIntEnum(_) =>
          TraceableValue.intToTraceValue.contramap(_.intValue)

        case EnumTag.ClosedStringEnum | EnumTag.OpenStringEnum(_) =>
          TraceableValue.stringToTraceValue.contramap(_.stringValue)
      }

      tvE.contramap(total)
    }

  override def struct[S](shapeId: ShapeId,
                         hints: Hints,
                         fields: Vector[Field[S, ?]],
                         make: IndexedSeq[Any] => S): TraceableValue[S] =
    maybeRedact[S](hints).getOrElse {
      def traceableStringValueFromField[A](s: S, field: Field[S, A]): String =
        aToStringViaTraceableValue(field.get(s))(self(field.schema))

      new TraceableValue[S] {
        override def toTraceValue(s: S): TraceValue =
          fields
            .map { f =>
              List(f.label, traceableStringValueFromField(s, f)).mkString(": ")
            }
            .mkString("{", ", ", "}")
      }
    }

  override def union[U](shapeId: ShapeId,
                        hints: Hints,
                        alternatives: Vector[Alt[U, ?]],
                        dispatch: Alt.Dispatcher[U]): TraceableValue[U] =
    maybeRedact[U](hints).getOrElse {
      val precompiler = new Alt.Precompiler[TraceableValue] {
        override def apply[A](label: String, schema: Schema[A]): TraceableValue[A] = { a =>
          s"$label: ${aToStringViaTraceableValue(a)(self(schema))}"
        }
      }

      dispatch.compile(precompiler)
    }

  override def biject[A, B](schema: Schema[A],
                            bijection: Bijection[A, B]): TraceableValue[B] =
    self(schema).contramap(bijection.from)

  override def refine[A, B](schema: Schema[A],
                            refinement: Refinement[A, B]): TraceableValue[B] =
    self(schema).contramap(refinement.from)

  override def lazily[A](suspend: Lazy[Schema[A]]): TraceableValue[A] =
    suspend.map(self(_)).value.toTraceValue(_)

  override def option[A](schema: Schema[A]): TraceableValue[Option[A]] =
    maybeRedact[Option[A]](schema.hints).getOrElse {
      val c: TraceableValue[A] = self(schema) // precompile the schema to TraceableValue[A]
      _.fold[TraceValue]("None")(c.toTraceValue)
    }
}
