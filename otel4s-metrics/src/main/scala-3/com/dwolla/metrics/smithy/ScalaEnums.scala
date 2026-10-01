package com.dwolla.metrics.smithy

private[smithy] object ScalaEnums {
  /** Whether `value` is a case of a Scala 3 `enum`. */
  def isEnumCase(value: Any): Boolean = value.isInstanceOf[scala.reflect.Enum]
}
