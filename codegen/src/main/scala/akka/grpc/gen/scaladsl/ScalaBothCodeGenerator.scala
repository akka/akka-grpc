package akka.grpc.gen.scaladsl

object ScalaBothCodeGenerator extends ScalaServerCodeGenerator with ScalaClientCodeGenerator {
  override def name = "akka-grpc-scaladsl-both"
}
