package com.example.crossredaction

import com.dwolla.tracing.smithy.SchemaVisitorTraceableValue
import com.dwolla.tracing.smithy.otel4s.{AnyValueStrings, SchemaVisitorToAnyValue}
import munit.FunSuite
import natchez.TraceValue
import org.typelevel.otel4s.AnyValue
import smithy4s.{Document, Hints, Schema, ShapeId}

/**
 * Each backend's visitor also honors the other backend's `@traceable(redacted = …)`, matched by shape ID, so a
 * shape annotated for one backend never leaks through the other. On a member carrying both traits, each visitor's
 * own redaction string wins, and a trait without `redacted` never cancels the other's redaction.
 */
class CrossTraitRedactionTest extends FunSuite {
  private val mixed = Mixed(
    natchezTarget = NatchezRedactedSecret("SECRET-natchez-target"),
    otel4sTarget = Otel4sRedactedSecret("SECRET-otel4s-target"),
    natchezMember = "SECRET-natchez-member",
    otel4sMember = "SECRET-otel4s-member",
    bothRedacted = "SECRET-both",
    natchezRedactsOtel4sPlain = "SECRET-natchez-only",
    otel4sRedactsNatchezPlain = "SECRET-otel4s-only",
    natchezPlainOnly = "visible-value",
    otel4sPlainOnly = "visible-otel4s-value",
    natchezPlainOverOtel4sTarget = Otel4sRedactedSecret("SECRET-natchez-plain-over-otel4s-target"),
    otel4sPlainOverNatchezTarget = NatchezRedactedSecret("SECRET-otel4s-plain-over-natchez-target"),
    natchezPlainOverNatchezTarget = NatchezRedactedSecret("SECRET-natchez-plain-over-natchez-target"),
    otel4sPlainOverOtel4sTarget = Otel4sRedactedSecret("SECRET-otel4s-plain-over-otel4s-target"),
  )

  private val natchezRendered: String =
    SchemaVisitorTraceableValue.fromSchema(Mixed.schema).toTraceValue(mixed) match {
      case TraceValue.StringValue(s) => s
      case other => fail(s"expected a string trace value, got $other")
    }

  private val otel4sRecorded: List[String] =
    AnyValueStrings(SchemaVisitorToAnyValue.fromSchema(Mixed.schema).toAnyValue(mixed))

  test("the otel4s visitor leaks no secret, including natchez-annotated ones") {
    assert(!otel4sRecorded.exists(_.contains("SECRET")), otel4sRecorded)
  }

  test("the natchez visitor leaks no secret, including otel4s-annotated ones") {
    assert(!natchezRendered.contains("SECRET"), natchezRendered)
  }

  test("the otel4s visitor honors natchez redaction on a target shape and on a member") {
    assert(otel4sRecorded.contains("<natchez-target>"), otel4sRecorded)
    assert(otel4sRecorded.contains("<natchez-member>"), otel4sRecorded)
  }

  test("the natchez visitor honors otel4s redaction on a target shape and on a member") {
    assert(natchezRendered.contains("<otel4s-target>"), natchezRendered)
    assert(natchezRendered.contains("<otel4s-member>"), natchezRendered)
  }

  test("with both traits redacting, each visitor uses its own string") {
    assert(otel4sRecorded.contains("<both-otel4s>") && !otel4sRecorded.contains("<both-natchez>"), otel4sRecorded)
    assert(natchezRendered.contains("<both-natchez>") && !natchezRendered.contains("<both-otel4s>"), natchezRendered)
  }

  test("a trait without `redacted` never cancels the other trait's redaction") {
    assert(otel4sRecorded.contains("<natchez-only-redacts>"), otel4sRecorded)
    assert(natchezRendered.contains("<otel4s-only-redacts>"), natchezRendered)
  }

  test("a natchez trait without `redacted` doesn't make the otel4s visitor redact") {
    assert(otel4sRecorded.contains("visible-value"), otel4sRecorded)
  }

  test("an otel4s trait without `redacted` doesn't make the natchez visitor redact") {
    assert(natchezRendered.contains("visible-otel4s-value"), natchezRendered)
  }

  private def assertBothVisitorsRedact(secret: String, placeholder: String)(implicit loc: munit.Location): Unit = {
    assert(!otel4sRecorded.exists(_.contains(secret)), s"$secret leaked through the otel4s visitor: $otel4sRecorded")
    assert(otel4sRecorded.contains(placeholder), s"$placeholder missing from the otel4s visitor's: $otel4sRecorded")
    assert(!natchezRendered.contains(secret), s"$secret leaked through the natchez visitor: $natchezRendered")
    assert(natchezRendered.contains(placeholder), s"$placeholder missing from the natchez visitor's: $natchezRendered")
  }

  test("a plain natchez trait on a member doesn't cancel an otel4s redaction on its target") {
    assertBothVisitorsRedact("SECRET-natchez-plain-over-otel4s-target", "<otel4s-target>")
  }

  test("a plain otel4s trait on a member doesn't cancel a natchez redaction on its target") {
    assertBothVisitorsRedact("SECRET-otel4s-plain-over-natchez-target", "<natchez-target>")
  }

  test("a plain natchez trait on a member doesn't cancel a natchez redaction on its target") {
    assertBothVisitorsRedact("SECRET-natchez-plain-over-natchez-target", "<natchez-target>")
  }

  test("a plain otel4s trait on a member doesn't cancel an otel4s redaction on its target") {
    assertBothVisitorsRedact("SECRET-otel4s-plain-over-otel4s-target", "<otel4s-target>")
  }

  test("a dynamically-bound natchez trait is honored by the otel4s visitor") {
    val schema = Schema.string.addHints(
      Hints.dynamic(ShapeId("com.dwolla.tracing.smithy", "traceable"), Document.obj("redacted" -> Document.fromString("<dynamic-natchez>")))
    )
    assertEquals(SchemaVisitorToAnyValue.fromSchema(schema).toAnyValue("SECRET-dynamic"), AnyValue.string("<dynamic-natchez>"))
  }

  test("a dynamically-bound otel4s trait is honored by the natchez visitor") {
    val schema = Schema.string.addHints(
      Hints.dynamic(ShapeId("com.dwolla.tracing.smithy.otel4s", "traceable"), Document.obj("redacted" -> Document.fromString("<dynamic-otel4s>")))
    )
    assertEquals(SchemaVisitorTraceableValue.fromSchema(schema).toTraceValue("SECRET-dynamic"), TraceValue.StringValue("<dynamic-otel4s>"))
  }
}
