$version: "2.0"

namespace com.example.otel4s.encoding

/// A structure exercising every non-primitive node: optional members, lists, maps, both enum kinds, a union, and
/// recursion.
structure Customer {
    @required
    name: String
    nickname: String
    @required
    tags: Tags
    @required
    scores: Scores
    @required
    status: Status
    @required
    priority: Priority
    @required
    contact: Contact
    next: Customer
}

list Tags {
    member: String
}

map Scores {
    key: String
    value: Integer
}

enum Status {
    ACTIVE = "active"
    CLOSED = "closed"
}

intEnum Priority {
    LOW = 1
    HIGH = 2
}

union Contact {
    email: String
    phone: Long
}
