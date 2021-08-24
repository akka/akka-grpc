/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

//#full-service-impl
package example.myapp.helloworld

import scala.concurrent.Future
import akka.NotUsed
import akka.stream.Materializer
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import com.google.protobuf.timestamp.Timestamp
import example.myapp.helloworld.grpc.GreeterServiceFilterApi.GenericFilter
import example.myapp.helloworld.grpc._
import scala.concurrent.duration._
class GreeterServiceImpl(implicit mat: Materializer) extends GreeterService {
  import mat.executionContext

  override def sayHello(in: HelloRequest): Future[HelloReply] = {
    println(s"sayHello to ${in.name}")
    lazy val response = Future.successful(HelloReply(s"Hello, ${in.name}", Some(Timestamp.apply(123456, 123))))
    if (in.name.contains("sleep")) {
      akka.pattern.after(5.second, mat.system.scheduler)(response)
    } else response
  }

  override def itKeepsTalking(in: Source[HelloRequest, NotUsed]): Future[HelloReply] = {
    println(s"sayHello to in stream...")
    in.runWith(Sink.seq)
      .map(elements => HelloReply(s"Hello, ${elements.map(_.name).mkString(", ")}", Some(Timestamp.apply(123456, 123))))
  }

  override def itKeepsReplying(in: HelloRequest): Source[HelloReply, NotUsed] = {
    println(s"sayHello to ${in.name} with stream of chars...")
    Source(s"Hello, ${in.name}".toList).map(character => HelloReply(character.toString))
  }

  override def streamHellos(in: Source[HelloRequest, NotUsed]): Source[HelloReply, NotUsed] = {
    println(s"sayHello to stream...")
    in.map(request => HelloReply(s"Hello, ${request.name}", Some(Timestamp.apply(123456, 123))))
  }

  val FilterItems = new FilterItems(mat.system)

  override val sayHelloFilter: Seq[GenericFilter[HelloRequest, Future[HelloReply]]] =
    Seq(
      FilterItems.RequestResponse.requestDuration,
      FilterItems.RequestResponse.timeout,
      FilterItems.RequestResponse.add,
      FilterItems.RequestResponse.remove)

  override val itKeepsTalkingFilter
      : Seq[GreeterServiceFilterApi.GenericFilter[Source[HelloRequest, NotUsed], Future[HelloReply]]] =
    Seq(FilterItems.StreamRequestResponse.add, FilterItems.StreamRequestResponse.remove)

  override val itKeepsReplyingFilter
      : Seq[GreeterServiceFilterApi.GenericFilter[HelloRequest, Source[HelloReply, akka.NotUsed]]] =
    Seq(FilterItems.RequestStreamResponse.add, FilterItems.RequestStreamResponse.remove)

  override val streamHellosFilter
      : Seq[GreeterServiceFilterApi.GenericFilter[Source[HelloRequest, NotUsed], Source[HelloReply, akka.NotUsed]]] =
    Seq(FilterItems.StreamRequestStreamResponse.add, FilterItems.StreamRequestStreamResponse.remove)

}
//#full-service-impl
