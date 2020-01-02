/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

//#full-service-impl
package example.myapp.helloworld

import akka.NotUsed
import akka.grpc.scaladsl.Metadata
import akka.stream.Materializer
import akka.stream.scaladsl.{ Sink, Source }
import example.myapp.helloworld.grpc._

import scala.concurrent.Future

class PowerGreeterServiceImpl(materializer: Materializer) extends GreeterServicePowerApi {
  import materializer.executionContext
  private implicit val mat: Materializer = materializer

  override def sayHello(in: HelloRequest, metadata: Metadata): Future[HelloReply] = {
    val greetee = authTaggedName(in, metadata)
    println(s"sayHello to $greetee")
    Future.successful(HelloReply(s"Hello, $greetee"))
  }

  override def itKeepsTalking(in: Source[HelloRequest, NotUsed], metadata: Metadata): Future[HelloReply] = {
    println(s"sayHello to in stream...")
    in.runWith(Sink.seq)
      .map(elements => HelloReply(s"Hello, ${elements.map(authTaggedName(_, metadata)).mkString(", ")}"))
  }

  override def itKeepsReplying(in: HelloRequest, metadata: Metadata): Source[HelloReply, NotUsed] = {
    val greetee = authTaggedName(in, metadata)
    println(s"sayHello to $greetee with stream of chars...")
    Source(s"Hello, $greetee".toList).map(character => HelloReply(character.toString))
  }

  override def streamHellos(in: Source[HelloRequest, NotUsed], metadata: Metadata): Source[HelloReply, NotUsed] = {
    println(s"sayHello to stream...")
    in.map(request => HelloReply(s"Hello, ${authTaggedName(request, metadata)}"))
  }

  // Bare-bones just for GRPC metadata demonstration purposes
  private def isAuthenticated(metadata: Metadata): Boolean =
    metadata.getText("authorization").nonEmpty

  private def authTaggedName(in: HelloRequest, metadata: Metadata): String = {
    val authenticated = isAuthenticated(metadata)
    s"${in.name} (${if (!authenticated) "not " else ""}authenticated)"
  }
}
//#full-service-impl
