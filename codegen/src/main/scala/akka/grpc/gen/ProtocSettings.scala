/*
 * Copyright (C) 2019-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen

object ProtocSettings {

  /** Whitelisted options for the built-in Java protoc plugin */
  val protocJava = Seq("single_line_to_proto_string", "ascii_format_to_string", "retain_source_code_info")

  /** Whitelisted options for the ScalaPB protoc plugin */
  val scalapb = Seq(
    "java_conversions",
    "flat_package",
    "single_line_to_proto_string",
    "ascii_format_to_string",
    "no_lenses",
    "retain_source_code_info")
}
