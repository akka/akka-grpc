package akka.grpc.gen.scaladsl

import scalapb.compiler.GeneratorParams

case class ExtendedGeneratorParams(baseParams: GeneratorParams = GeneratorParams(), serverPowerApis: Boolean = false)
