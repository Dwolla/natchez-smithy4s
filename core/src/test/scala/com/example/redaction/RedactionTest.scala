package com.example.redaction

import com.dwolla.tracing.smithy.SchemaVisitorTraceableValue
import munit.FunSuite
import natchez.TraceValue
import smithy4s.Document

/**
 * Every place a `@traceable(redacted = …)` value can sit — on a member or its target shape, and
 * inside optional, nested, collection, set, map key and value, union, recursive, and document
 * shapes — renders the redaction placeholder, never the secret.
 */
class RedactionTest extends FunSuite {
  private val everything = Everything(
    targetRedacted = TargetRedactedSecret("SECRET-target"),
    refined = RefinedSecret("SECRET-refined"),
    holder = SecretHolder("SECRET-member"),
    list = List("SECRET-list"),
    set = Set("SECRET-set"),
    holders = List(SecretHolder("SECRET-holder-in-list")),
    valueMap = Map("key" -> "SECRET-map-value"),
    keyMap = Map("SECRET-map-key" -> "value"),
    holderMap = Map("key" -> SecretHolder("SECRET-holder-in-map")),
    union = SecretUnion.SecretCase("SECRET-union"),
    node = Node(Some("SECRET-recursive"), Some(Node(Some("SECRET-recursive-nested"), None))),
    document = SecretDocument(Document.obj("password" -> Document.fromString("SECRET-document"))),
    optionalSecret = Some(TargetRedactedSecret("SECRET-optional-target")),
    optionalMemberSecret = Some("SECRET-optional-member"),
  )

  private def traced(value: Everything): String =
    SchemaVisitorTraceableValue.fromSchema(Everything.schema).toTraceValue(value) match {
      case TraceValue.StringValue(s) => s
      case other => fail(s"expected a string trace value, got $other")
    }

  private val rendered = traced(everything)

  private def assertRedacted(rendered: String, secret: String, placeholder: String)(implicit loc: munit.Location): Unit = {
    assert(!rendered.contains(secret), s"$secret leaked into: $rendered")
    assert(rendered.contains(placeholder), s"$placeholder missing from: $rendered")
  }

  test("no secret appears anywhere in the rendered value") {
    assert(!rendered.contains("SECRET"), rendered)
  }

  test("a secret redacted on its target shape")(assertRedacted(rendered, "SECRET-target", "<target-redacted>"))
  test("a refined (constrained) secret")(assertRedacted(rendered, "SECRET-refined", "<refined-redacted>"))
  test("an optional member whose target is redacted")(assertRedacted(rendered, "SECRET-optional-target", "<target-redacted>"))
  test("an optional member redacted on the member")(assertRedacted(rendered, "SECRET-optional-member", "<optional-member-redacted>"))
  test("a member of a nested structure")(assertRedacted(rendered, "SECRET-member", "<member-redacted>"))
  test("a list member")(assertRedacted(rendered, "SECRET-list", "<list-member-redacted>"))
  test("a set member")(assertRedacted(rendered, "SECRET-set", "<set-member-redacted>"))
  test("a structure inside a list")(assertRedacted(rendered, "SECRET-holder-in-list", "<member-redacted>"))
  test("a map value")(assertRedacted(rendered, "SECRET-map-value", "<map-value-redacted>"))
  test("a map key")(assertRedacted(rendered, "SECRET-map-key", "<map-key-redacted>"))
  test("a structure inside a map value")(assertRedacted(rendered, "SECRET-holder-in-map", "<member-redacted>"))
  test("a union alternative")(assertRedacted(rendered, "SECRET-union", "<union-member-redacted>"))
  test("a recursive (lazily compiled) structure, at every depth") {
    assertRedacted(rendered, "SECRET-recursive", "<recursive-redacted>")
    assert(!rendered.contains("SECRET-recursive-nested"), rendered)
  }
  test("a document")(assertRedacted(rendered, "SECRET-document", "<document-redacted>"))

  test("a structure inside a union alternative") {
    val holderInUnion = traced(everything.copy(union = SecretUnion.HolderCase(SecretHolder("SECRET-holder-in-union"))))
    assertRedacted(holderInUnion, "SECRET-holder-in-union", "<member-redacted>")
  }
}
