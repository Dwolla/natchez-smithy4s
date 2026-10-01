$version: "2.0"

namespace com.example.redaction

use com.dwolla.tracing.smithy#traceable

/// A secret marked for redaction on its target shape.
@traceable(redacted: "<target-redacted>")
string TargetRedactedSecret

/// A constrained (refined) secret, redacted on its target shape.
@length(min: 1)
@traceable(redacted: "<refined-redacted>")
string RefinedSecret

structure SecretHolder {
    @required
    @traceable(redacted: "<member-redacted>")
    secret: String
}

list SecretList {
    @traceable(redacted: "<list-member-redacted>")
    member: String
}

@uniqueItems
list SecretSet {
    @traceable(redacted: "<set-member-redacted>")
    member: String
}

list HolderList {
    member: SecretHolder
}

map SecretValueMap {
    key: String
    @traceable(redacted: "<map-value-redacted>")
    value: String
}

map SecretKeyMap {
    @traceable(redacted: "<map-key-redacted>")
    key: String
    value: String
}

map HolderMap {
    key: String
    value: SecretHolder
}

union SecretUnion {
    @traceable(redacted: "<union-member-redacted>")
    secret: String
    holder: SecretHolder
}

/// A recursive shape, so smithy4s compiles it lazily.
structure Node {
    @traceable(redacted: "<recursive-redacted>")
    secret: String
    next: Node
}

@traceable(redacted: "<document-redacted>")
document SecretDocument

structure Everything {
    @required
    targetRedacted: TargetRedactedSecret
    @required
    refined: RefinedSecret
    optionalSecret: TargetRedactedSecret
    @traceable(redacted: "<optional-member-redacted>")
    optionalMemberSecret: String
    @required
    holder: SecretHolder
    @required
    list: SecretList
    @required
    set: SecretSet
    @required
    holders: HolderList
    @required
    valueMap: SecretValueMap
    @required
    keyMap: SecretKeyMap
    @required
    holderMap: HolderMap
    @required
    union: SecretUnion
    @required
    node: Node
    @required
    document: SecretDocument
}
