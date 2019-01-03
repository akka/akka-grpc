/*
 * Copyright (C) 2019 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc

package object javadsl {
  /**
   * Helper for creating akka.japi.function.Function instances from Scala
   * functions as Scala 2.11 does not know about SAMs.
   */
  def japiFunction[A, B](f: A => B): akka.japi.function.Function[A, B] =
    new akka.japi.function.Function[A, B]() {
      override def apply(a: A): B = f(a)
    }

  /**
   * Helper for creating java.util.function.Function instances from Scala
   * functions as Scala 2.11 does not know about SAMs.
   */
  def javaFunction[A, B](f: A => B): java.util.function.Function[A, B] =
    new java.util.function.Function[A, B]() {
      override def apply(a: A): B = f(a)
    }
}
