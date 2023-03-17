/*
 * Copyright (C) 2020-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.javadsl

import akka.grpc.gen.StdoutLogger
import com.google.protobuf.DescriptorProtos.{ FileDescriptorProto, FileOptions }
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorRequest
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ValidationSpec extends AnyWordSpec with Matchers {

  class JavaCodeGeneratorAccess extends JavaCodeGenerator {
    override def name: String = "mock_java_gode_gen"
  }

  private def getCodeGeneratorRequest(hasMultipleFiles: Seq[Boolean]) = {
    val builder = CodeGeneratorRequest.newBuilder()
    hasMultipleFiles.map { has =>
      val fileOption = FileOptions.newBuilder().setJavaMultipleFiles(has)
      val fileDescriptor = FileDescriptorProto.newBuilder().setOptions(fileOption).build
      builder.addProtoFile(fileDescriptor)
    }
    builder.build

  }

  "JavaCodeGenerator" should {
    "Not fail when java_multiple_files=true" in {
      noException should be thrownBy {
        new JavaCodeGeneratorAccess().run(getCodeGeneratorRequest(Seq(true)), StdoutLogger)
      }
    }

    "Not fail when multiple java_multiple_files=true" in {
      noException should be thrownBy {
        new JavaCodeGeneratorAccess().run(getCodeGeneratorRequest(Seq(true, true)), StdoutLogger)
      }
    }

    "Fail when only java_multiple_files=false" in {
      assertThrows[IllegalArgumentException] {
        new JavaCodeGeneratorAccess().run(getCodeGeneratorRequest(Seq(false)), StdoutLogger)
      }
    }
    "Fail when at least one java_multiple_files=false" in {
      assertThrows[IllegalArgumentException] {
        new JavaCodeGeneratorAccess().run(getCodeGeneratorRequest(Seq(true, false)), StdoutLogger)
      }
    }
  }
}
