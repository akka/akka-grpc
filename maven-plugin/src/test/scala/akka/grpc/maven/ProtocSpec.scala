/*
 * Copyright (C) 2018-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.maven

import java.io.{ File, FileOutputStream }
import java.nio.file.Files
import java.util.jar.{ JarEntry, JarOutputStream }

import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec

class ProtocSpec extends AnyWordSpec with Matchers {
  "The protoc error messages" must {
    "be parsed into details" in {
      AbstractGenerateMojo.parseError(
        "notifications.proto:12:1: Expected top-level statement (e.g. \"message\").") should
      ===(
        Left(AbstractGenerateMojo
          .ProtocError("notifications.proto", 12, 1, "Expected top-level statement (e.g. \"message\").")))
    }
    "be kept if not parseable" in {
      AbstractGenerateMojo.parseError("My hovercraft is full of eels") should ===(
        Right("My hovercraft is full of eels"))
    }
  }

  import scala.jdk.CollectionConverters._

  "Parsing generator settings" should {
    "filter out the false values" in {
      val settings = Map("1" -> "true", "2" -> "false", "3" -> "False", "4" -> "")
      AbstractGenerateMojo.parseGeneratorSettings(settings.asJava) shouldBe Seq("1", "4")
    }

    "convert camelCase into snake_case of keys" in {
      val settings = Map("flatPackage" -> "true", "serverPowerApis" -> "true")
      AbstractGenerateMojo.parseGeneratorSettings(settings.asJava) shouldBe Seq("flat_package", "server_power_apis")
    }
  }

  "Selecting the protoc runner" should {
    "use the local protoc when an executable is set" in {
      AbstractGenerateMojo.useLocalProtoc("/usr/bin/protoc") shouldBe true
    }

    "fall back to the downloaded protoc when not set" in {
      AbstractGenerateMojo.useLocalProtoc(null) shouldBe false
      AbstractGenerateMojo.useLocalProtoc("") shouldBe false
      AbstractGenerateMojo.useLocalProtoc("   ") shouldBe false
    }
  }

  "Extracting the standard types" should {
    "pick the google/protobuf definitions out of a protobuf-java jar" in {
      val jar = File.createTempFile("protobuf-java", ".jar")
      jar.deleteOnExit()
      val out = new JarOutputStream(new FileOutputStream(jar))
      try {
        Seq(
          "google/protobuf/timestamp.proto",
          "google/protobuf/any.proto",
          "google/protobuf/Timestamp.class",
          "META-INF/MANIFEST.MF").foreach { name =>
          out.putNextEntry(new JarEntry(name))
          out.write(name.getBytes("UTF-8"))
          out.closeEntry()
        }
      } finally out.close()

      val targetDir = Files.createTempDirectory("std-types").toFile
      val extracted = AbstractGenerateMojo.extractStdTypes(jar, targetDir)

      extracted.map(_.getName).toSet shouldBe Set("timestamp.proto", "any.proto")
      extracted.foreach { file =>
        file.getParentFile.getCanonicalPath shouldBe new File(targetDir, "google/protobuf").getCanonicalPath
      }
      new String(Files.readAllBytes(new File(targetDir, "google/protobuf/any.proto").toPath), "UTF-8") shouldBe
      "google/protobuf/any.proto"
    }
  }

  "Running a local protoc" should {
    "execute the given binary and return its exit code" in {
      // 'true' and 'false' are standard POSIX utilities returning 0 and 1 respectively
      AbstractGenerateMojo.runLocalProtoc("true", Seq.empty) shouldBe 0
      AbstractGenerateMojo.runLocalProtoc("false", Seq.empty) shouldBe 1
    }
  }
}
