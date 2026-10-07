package com.example.otel4s.redaction

import com.dwolla.tracing.smithy.otel4s.{AnyValueStrings, SchemaVisitorToAnyValue}
import munit.FunSuite
import smithy4s.Document

/**
 * Every place an otel4s `@traceable(redacted = …)` value can sit (on a member or its target shape, and inside
 * optional, nested, list, set, map key and value, union, recursive, and document shapes) records the placeholder,
 * never the secret. Secrets are searched for in every string leaf *and every map key* of the encoded tree.
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

  private def recordedStrings(value: Everything): List[String] =
    AnyValueStrings(SchemaVisitorToAnyValue.fromSchema(Everything.schema).toAnyValue(value))

  private val recorded = recordedStrings(everything)

  private def assertRedacted(recorded: List[String], secret: String, placeholder: String)(implicit loc: munit.Location): Unit = {
    assert(!recorded.exists(_.contains(secret)), s"$secret leaked into: $recorded")
    assert(recorded.contains(placeholder), s"$placeholder missing from: $recorded")
  }

  test("no secret appears anywhere in the recorded value") {
    assert(!recorded.exists(_.contains("SECRET")), recorded)
  }

  test("a secret redacted on its target shape")(assertRedacted(recorded, "SECRET-target", "<target-redacted>"))
  test("a refined (constrained) secret")(assertRedacted(recorded, "SECRET-refined", "<refined-redacted>"))
  test("an optional member whose target is redacted")(assertRedacted(recorded, "SECRET-optional-target", "<target-redacted>"))
  test("an optional member redacted on the member")(assertRedacted(recorded, "SECRET-optional-member", "<optional-member-redacted>"))
  test("a member of a nested structure")(assertRedacted(recorded, "SECRET-member", "<member-redacted>"))
  test("a list member")(assertRedacted(recorded, "SECRET-list", "<list-member-redacted>"))
  test("a set member")(assertRedacted(recorded, "SECRET-set", "<set-member-redacted>"))
  test("a structure inside a list")(assertRedacted(recorded, "SECRET-holder-in-list", "<member-redacted>"))
  test("a map value")(assertRedacted(recorded, "SECRET-map-value", "<map-value-redacted>"))
  test("a map key")(assertRedacted(recorded, "SECRET-map-key", "<map-key-redacted>"))
  test("a structure inside a map value")(assertRedacted(recorded, "SECRET-holder-in-map", "<member-redacted>"))
  test("a union alternative")(assertRedacted(recorded, "SECRET-union", "<union-member-redacted>"))
  test("a recursive (lazily compiled) structure, at every depth") {
    assertRedacted(recorded, "SECRET-recursive", "<recursive-redacted>")
    assert(!recorded.exists(_.contains("SECRET-recursive-nested")), recorded)
  }
  test("a document")(assertRedacted(recorded, "SECRET-document", "<document-redacted>"))

  test("a structure inside a union alternative") {
    val holderInUnion = recordedStrings(everything.copy(union = SecretUnion.HolderCase(SecretHolder("SECRET-holder-in-union"))))
    assertRedacted(holderInUnion, "SECRET-holder-in-union", "<member-redacted>")
  }

  test("the generated companion instance redacts too") {
    val viaCompanion = AnyValueStrings(TargetRedactedSecret.targetRedactedSecretTraceable.toAnyValue(TargetRedactedSecret("SECRET-companion")))
    assertEquals(viaCompanion, List("<target-redacted>"))
  }
}
