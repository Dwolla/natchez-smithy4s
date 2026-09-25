package com.example.tracing

import com.example.tracing.TracingServiceOperation.*
import org.scalacheck.*
import org.scalacheck.Arbitrary.arbitrary

trait TracingServiceArbitraries {
  implicit val arbTracingRequest: Arbitrary[TracingRequest] = Arbitrary {
    for {
      id <- Gen.identifier
      value <- Gen.chooseNum(1, 100)
      description <- Gen.option(Gen.alphaNumStr.suchThat(_.nonEmpty))
    } yield TracingRequest(id, value, description)
  }

  implicit val arbTracingServiceOperation: Arbitrary[TracingServiceOperation[_, _, _, _, _]] = Arbitrary {
    Gen.oneOf(
      Gen.const(GetStatus()),
      arbitrary[TracingRequest].map(ProcessRequest(_)),
    )
  }
}

object TracingServiceArbitraries extends TracingServiceArbitraries
