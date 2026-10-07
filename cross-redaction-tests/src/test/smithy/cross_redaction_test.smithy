$version: "2.0"

namespace com.example.crossredaction

/// Annotated only for natchez.
@com.dwolla.tracing.smithy#traceable(redacted: "<natchez-target>")
string NatchezRedactedSecret

/// Annotated only for otel4s.
@com.dwolla.tracing.smithy.otel4s#traceable(redacted: "<otel4s-target>")
string Otel4sRedactedSecret

structure Mixed {
    @required
    natchezTarget: NatchezRedactedSecret

    @required
    otel4sTarget: Otel4sRedactedSecret

    @required
    @com.dwolla.tracing.smithy#traceable(redacted: "<natchez-member>")
    natchezMember: String

    @required
    @com.dwolla.tracing.smithy.otel4s#traceable(redacted: "<otel4s-member>")
    otel4sMember: String

    @required
    @com.dwolla.tracing.smithy#traceable(redacted: "<both-natchez>")
    @com.dwolla.tracing.smithy.otel4s#traceable(redacted: "<both-otel4s>")
    bothRedacted: String

    @required
    @com.dwolla.tracing.smithy#traceable(redacted: "<natchez-only-redacts>")
    @com.dwolla.tracing.smithy.otel4s#traceable
    natchezRedactsOtel4sPlain: String

    @required
    @com.dwolla.tracing.smithy#traceable
    @com.dwolla.tracing.smithy.otel4s#traceable(redacted: "<otel4s-only-redacts>")
    otel4sRedactsNatchezPlain: String

    /// A natchez trait with no `redacted` means "trace this value": the otel4s visitor must not redact it either.
    @required
    @com.dwolla.tracing.smithy#traceable
    natchezPlainOnly: String

    /// An otel4s trait with no `redacted` means "trace this value": the natchez visitor must not redact it either.
    @required
    @com.dwolla.tracing.smithy.otel4s#traceable
    otel4sPlainOnly: String

    /// A plain trait on a member never cancels a redaction on its target, whichever trait each one is.
    @required
    @com.dwolla.tracing.smithy#traceable
    natchezPlainOverOtel4sTarget: Otel4sRedactedSecret

    @required
    @com.dwolla.tracing.smithy.otel4s#traceable
    otel4sPlainOverNatchezTarget: NatchezRedactedSecret

    @required
    @com.dwolla.tracing.smithy#traceable
    natchezPlainOverNatchezTarget: NatchezRedactedSecret

    @required
    @com.dwolla.tracing.smithy.otel4s#traceable
    otel4sPlainOverOtel4sTarget: Otel4sRedactedSecret
}
