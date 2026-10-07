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
}
