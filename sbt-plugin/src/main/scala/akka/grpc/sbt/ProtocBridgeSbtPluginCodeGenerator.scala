package akka.grpc.sbt

import akka.grpc.gen.CodeGenerator
import akka.http.grpc.ScalaServerCodeGenerator
import com.google.protobuf.ExtensionRegistry
import com.google.protobuf.Descriptors._
import com.google.protobuf.compiler.PluginProtos.{ CodeGeneratorRequest, CodeGeneratorResponse }

import scala.collection.JavaConverters._
import com.trueaccord.scalapb.compiler.{ FunctionalPrinter, GeneratorParams, DescriptorPimps ⇒ DescriptorImplicits }
import protocbridge.Artifact

/**
 * Adapts existing [[akka.grpc.gen.CodeGenerator]] into the sbt-protoc required type
 */
class ProtocBridgeSbtPluginCodeGenerator(impl: akka.grpc.gen.CodeGenerator) extends protocbridge.ProtocCodeGenerator with DescriptorImplicits {

  override def run(request: Array[Byte]): Array[Byte] =
    impl.run(request)

  override def suggestedDependencies: Seq[Artifact] = Nil // TODO hm should we make use of that?

  // TODO meh somewhat hardcoded by sbt-protoc
  override val params: GeneratorParams = com.trueaccord.scalapb.compiler.GeneratorParams()

  //  def generateFile(fileDesc: FileDescriptor): CodeGeneratorResponse.File = {
  //    val b = CodeGeneratorResponse.File.newBuilder()
  //    b.setName(s"${fileDesc.scalaDirectory}/${fileDesc.fileDescriptorObjectName}Foo.scala")
  //    val fp = FunctionalPrinter()
  //      .add(s"package ${fileDesc.scalaPackageName}")
  //      .add("")
  //      .print(fileDesc.getMessageTypes.asScala) {
  //        case (p, m) =>
  //          p.add(s"object ${m.getName}Boo {")
  //            .indent
  //            .add(s"type T = ${m.scalaTypeName}")
  //            .add(s"val FieldCount = ${m.getFields.size}")
  //            .outdent
  //            .add("}")
  //      }
  //    b.setContent(fp.result)
  //    b.build
  //  }

  override def toString = s"ProtocBridgeSbtPluginCodeGenerator(${impl.name}: $impl)"
}
