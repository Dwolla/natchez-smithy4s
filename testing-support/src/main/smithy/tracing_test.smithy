$version: "2.0"

namespace com.example.tracing

use smithy.api#required
use smithy.api#readonly
use smithy.api#http
use smithy.api#title
use smithy.api#documentation
use smithy.api#error

/// A simple request structure
structure TracingRequest {
    /// The unique identifier for the request
    @required
    id: String,

    /// A numeric value associated with the request
    @required
    value: Integer,

    /// An optional description
    description: String,
}

/// A simple response structure
structure TracingResponse {
    /// The unique identifier for the response
    @required
    id: String,

    /// A result value
    @required
    result: Integer,

    /// A status message
    status: String,
}

/// A simple error structure
@error("server")
structure TracingError {
    /// Error message
    @required
    message: String,

    /// Error code
    code: Integer,
}

/// A simple service for testing tracing
@title("Tracing Test Service")
service TracingService {
    version: "1.0",
    operations: [ProcessRequest, GetStatus]
}

/// Process a request and return a response
@http(method: "POST", uri: "/process", code: 200)
operation ProcessRequest {
    input: TracingRequest,
    output: TracingResponse,
    errors: [TracingError]
}

/// Get the status of the service
@readonly
@http(method: "GET", uri: "/status", code: 200)
operation GetStatus {
    output: TracingResponse
}
