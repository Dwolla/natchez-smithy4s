package com.dwolla.tracing.smithy.otel4s

import com.example.otel4s.encoding.*
import munit.FunSuite
import org.typelevel.otel4s.AnyValue
import smithy4s.{Blob, Document, Schema, Timestamp}

import java.util.UUID

/** What each schema node encodes to (spec §4), for values with no redaction anywhere. */
class SchemaVisitorToAnyValueTest extends FunSuite {
  private def encode[A](schema: Schema[A], a: A): AnyValue = SchemaVisitorToAnyValue.fromSchema(schema).toAnyValue(a)

  test("strings, booleans, and UUIDs") {
    assertEquals(encode(Schema.string, "hello"), AnyValue.string("hello"))
    assertEquals(encode(Schema.boolean, true), AnyValue.boolean(true))
    assertEquals(
      encode(Schema.uuid, UUID.fromString("00000000-0000-0000-0000-000000000001")),
      AnyValue.string("00000000-0000-0000-0000-000000000001"),
    )
  }

  test("integral numbers are longs, and floating-point numbers are doubles") {
    assertEquals(encode(Schema.byte, 1.toByte), AnyValue.long(1L))
    assertEquals(encode(Schema.short, 2.toShort), AnyValue.long(2L))
    assertEquals(encode(Schema.int, 3), AnyValue.long(3L))
    assertEquals(encode(Schema.long, 4L), AnyValue.long(4L))
    assertEquals(encode(Schema.float, 1.5f), AnyValue.double(1.5))
    assertEquals(encode(Schema.double, 2.5), AnyValue.double(2.5))
  }

  test("arbitrary-precision numbers are recorded exactly") {
    assertEquals(encode(Schema.bigint, BigInt(7)), AnyValue.long(7L))
    assertEquals(
      encode(Schema.bigint, BigInt("123456789012345678901234567890")),
      AnyValue.string("123456789012345678901234567890"),
    )
    assertEquals(encode(Schema.bigdecimal, BigDecimal("1.5")), AnyValue.double(1.5))
  }

  test("timestamps are DATE_TIME strings, and blobs are base64 strings") {
    assertEquals(encode(Schema.timestamp, Timestamp.fromEpochSecond(0L)), AnyValue.string("1970-01-01T00:00:00Z"))
    assertEquals(encode(Schema.blob, Blob("hello")), AnyValue.string("aGVsbG8="))
  }

  test("documents keep their structure") {
    val document = Document.obj(
      "name" -> Document.fromString("Ada"),
      "age" -> Document.fromInt(36),
      "admin" -> Document.fromBoolean(false),
      "tags" -> Document.array(Document.fromString("x"), Document.nullDoc),
    )
    assertEquals(
      encode(Schema.document, document),
      AnyValue.map(Map(
        "name" -> AnyValue.string("Ada"),
        "age" -> AnyValue.long(36L),
        "admin" -> AnyValue.boolean(false),
        "tags" -> AnyValue.seq(List(AnyValue.string("x"), AnyValue.empty)),
      )),
    )
  }

  test("Unit is the empty value") {
    assertEquals(encode(Schema.unit, ()), AnyValue.empty)
  }

  private val bob = Customer(
    name = "Bob",
    tags = Nil,
    scores = Map.empty,
    status = Status.CLOSED,
    priority = Priority.LOW,
    contact = Contact.PhoneCase(5551234L),
  )

  test("structures are maps of member name to value; absent optional members are empty") {
    val ada = Customer(
      name = "Ada",
      tags = List("a", "b"),
      scores = Map("math" -> 90),
      status = Status.ACTIVE,
      priority = Priority.HIGH,
      contact = Contact.EmailCase("ada@example.com"),
      nickname = None,
      next = Some(bob),
    )
    val encodedBob = AnyValue.map(Map(
      "name" -> AnyValue.string("Bob"),
      "nickname" -> AnyValue.empty,
      "tags" -> AnyValue.seq(Nil),
      "scores" -> AnyValue.map(Map.empty),
      "status" -> AnyValue.string("closed"),
      "priority" -> AnyValue.long(1L),
      "contact" -> AnyValue.map(Map("phone" -> AnyValue.long(5551234L))),
      "next" -> AnyValue.empty,
    ))
    assertEquals(
      encode(Customer.schema, ada),
      AnyValue.map(Map(
        "name" -> AnyValue.string("Ada"),
        "nickname" -> AnyValue.empty,
        "tags" -> AnyValue.seq(List(AnyValue.string("a"), AnyValue.string("b"))),
        "scores" -> AnyValue.map(Map("math" -> AnyValue.long(90L))),
        "status" -> AnyValue.string("active"),
        "priority" -> AnyValue.long(2L),
        "contact" -> AnyValue.map(Map("email" -> AnyValue.string("ada@example.com"))),
        "next" -> encodedBob,
      )),
    )
  }

  private def encodedTags(n: Int): AnyValue =
    SchemaVisitorToAnyValue.fromSchema(Customer.schema).toAnyValue(bob.copy(tags = (1 to n).map(i => s"t$i").toList)) match {
      case map: AnyValue.MapValue => map.value("tags")
      case other => fail(s"expected a map, got $other")
    }

  private def strings(values: String*): List[AnyValue] = values.toList.map(AnyValue.string)

  test("a list of up to 5 elements is recorded in full") {
    assertEquals(encodedTags(5), AnyValue.seq(strings("t1", "t2", "t3", "t4", "t5")))
  }

  test("a list of more than 5 elements records the first 5, then how many more there were") {
    assertEquals(encodedTags(6), AnyValue.seq(strings("t1", "t2", "t3", "t4", "t5", "and 1 more")))
    assertEquals(encodedTags(7), AnyValue.seq(strings("t1", "t2", "t3", "t4", "t5", "and 2 more")))
  }

  test("a map of up to 5 entries is recorded in full") {
    val scores = (1 to 5).map(i => s"s$i" -> i).toMap
    assertEquals(
      encode(Scores.underlyingSchema, scores),
      AnyValue.map(scores.map { case (k, v) => k -> AnyValue.long(v.toLong) }),
    )
  }

  private def assertTruncatedScores(entryCount: Int, expectedMarker: String)(implicit loc: munit.Location): Unit = {
    val scores = (1 to entryCount).map(i => s"s$i" -> i).toMap
    encode(Scores.underlyingSchema, scores) match {
      case map: AnyValue.MapValue =>
        val (marker, entries) = map.value.partition { case (key, _) => key == "(truncated)" }
        assertEquals(marker, Map[String, AnyValue]("(truncated)" -> AnyValue.string(expectedMarker)))
        assertEquals(entries.size, 5)
        assert(entries.forall { case (key, value) => scores.get(key).map(i => AnyValue.long(i.toLong)).contains(value) }, entries)
      case other => fail(s"expected a map, got $other")
    }
  }

  test("a map of more than 5 entries records 5 of them, then how many more there were") {
    assertTruncatedScores(7, "and 2 more")
  }

  test("a map of 6 entries records 5 of them, then says there was 1 more") {
    assertTruncatedScores(6, "and 1 more")
  }
}
