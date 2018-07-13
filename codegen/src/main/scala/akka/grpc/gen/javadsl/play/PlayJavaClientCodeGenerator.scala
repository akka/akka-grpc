/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.gen.javadsl.play

import akka.grpc.gen.javadsl.{ JavaCodeGenerator, Service }
import com.google.protobuf.compiler.PluginProtos.CodeGeneratorResponse
import templates.PlayJava.txt.{ AkkaGrpcClientModule, ClientProvider }

import scala.annotation.tailrec

object PlayJavaClientCodeGenerator extends JavaCodeGenerator {
  override def name: String = "akka-grpc-play-client-java"

  override def perServiceContent = super.perServiceContent + generateClientProvider

  private val generateClientProvider: Service => CodeGeneratorResponse.File = service => {
    val b = CodeGeneratorResponse.File.newBuilder()
    b.setContent(ClientProvider(service).body)
    b.setName(s"${service.packageName.replace('.', '/')}/${service.name}ClientProvider.java")
    b.build
  }

  override def staticContent(allServices: Seq[Service]): Set[CodeGeneratorResponse.File] = {
    if (allServices.nonEmpty) {
      val packageName = packageForSharedModuleFile(allServices)
      val b = CodeGeneratorResponse.File.newBuilder()
      b.setContent(AkkaGrpcClientModule(packageName, allServices).body)
      b.setName(s"${packageName.replace('.', '/')}/AkkaGrpcClientModule.java")
      val set = Set(b.build)
      println(s"Generated [${packageName}.AkkaGrpcClientModule] add it to play.modules.enabled and a section " +
        "with Akka gRPC client config under akka.grpc.client.[servicepackage.ServiceName] to be able to inject " +
        "client instances.")
      set

    } else Set.empty
  }

  private[play] def packageForSharedModuleFile(allServices: Seq[Service]): String =
    // single service or all services in single package - use that
    if (allServices.forall(_.packageName == allServices.head.packageName)) allServices.head.packageName
    else {
      // try to find longest common prefix
      allServices.tail.foldLeft(allServices.head.packageName)((packageName, service) =>
        if (packageName == service.packageName) packageName
        else commonPackage(packageName, service.packageName))
    }

  private[play] def commonPackage(a: String, b: String): String = {
    val aPackages = a.split('.')
    val bPackages = b.split('.')
    @tailrec
    def countIdenticalPackage(pos: Int): Int = {
      if (aPackages(pos) == bPackages(pos)) countIdenticalPackage(pos + 1)
      else pos
    }

    val prefixLength = countIdenticalPackage(0)
    if (prefixLength == 0) "" // no common, use root package
    else aPackages.take(prefixLength).mkString(".")

  }

}
