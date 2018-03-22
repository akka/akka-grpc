package akka.grpc.gen

sealed trait MethodType
case object Unary extends MethodType
case object ClientStreaming extends MethodType
case object ServerStreaming extends MethodType
case object BidiStreaming extends MethodType
