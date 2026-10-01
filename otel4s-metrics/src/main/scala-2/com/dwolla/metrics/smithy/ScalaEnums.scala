package com.dwolla.metrics.smithy

private[smithy] object ScalaEnums {
  /** Whether `value` is a case of a Scala 3 `enum`; Scala 2 has no enums, so never. */
  def isEnumCase(value: Any): Boolean = false
}
