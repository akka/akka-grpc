/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */
package example.myapp.helloworld

import akka.NotUsed
import akka.actor.ActorSystem
import akka.grpc.scaladsl.Metadata
import akka.stream.scaladsl.Source
import example.myapp.helloworld.grpc.GreeterServiceFilterApi.GenericFilter
import example.myapp.helloworld.grpc.GreeterServiceFilterPowerApi.{ GenericFilter => PowerFilter }
import example.myapp.helloworld.grpc.{ HelloReply, HelloRequest }

import scala.concurrent.duration._
import scala.concurrent.{ ExecutionContext, Future, TimeoutException }

class FilterItems(sys: ActorSystem) {
  val log = sys.log
  object RequestResponse {

    val timeout: GenericFilter[HelloRequest, Future[HelloReply]] = new GenericFilter[HelloRequest, Future[HelloReply]] {
      override def apply(request: HelloRequest, next: HelloRequest => Future[HelloReply])(
          implicit ex: ExecutionContext): Future[HelloReply] = {
        val timeout = akka.pattern.after(1.second, sys.scheduler) {
          Future.failed(new TimeoutException("timeout"))
        }
        Future.firstCompletedOf(Seq(next(request), timeout)).recover {
          case _: TimeoutException =>
            log.info("request did not complete within 1 second")
            HelloReply("timeout")
        }
      }
    }

    val requestDuration: GenericFilter[HelloRequest, Future[HelloReply]] =
      new GenericFilter[HelloRequest, Future[HelloReply]] {
        override def apply(request: HelloRequest, next: HelloRequest => Future[HelloReply])(
            implicit ex: ExecutionContext): Future[HelloReply] = {
          val now = System.currentTimeMillis()
          next(request).map { resp =>
            val duration = System.currentTimeMillis() - now
            log.info(s"took $duration ms")
            resp
          }
        }
      }

    val addRemove: GenericFilter[HelloRequest, Future[HelloReply]] =
      new GenericFilter[HelloRequest, Future[HelloReply]] {
        override def apply(request: HelloRequest, next: HelloRequest => Future[HelloReply])(
            implicit ex: ExecutionContext): Future[HelloReply] = {
          val newRequest = addToRequest(request)
          next(newRequest).map(removeFromResponse)
        }
      }
    val addPower: PowerFilter[HelloRequest, Future[HelloReply]] = new PowerFilter[HelloRequest, Future[HelloReply]] {
      override def apply(request: (HelloRequest, Metadata), next: (HelloRequest, Metadata) => Future[HelloReply])(
          implicit ex: ExecutionContext): Future[HelloReply] = {
        next(addToRequest(request._1), request._2)
      }
    }

    val removePower: PowerFilter[HelloRequest, Future[HelloReply]] = new PowerFilter[HelloRequest, Future[HelloReply]] {
      override def apply(request: (HelloRequest, Metadata), next: (HelloRequest, Metadata) => Future[HelloReply])(
          implicit ex: ExecutionContext): Future[HelloReply] = next(request._1, request._2).map(removeFromResponse)
    }
  }

  object StreamRequestResponse {
    val add: GenericFilter[Source[HelloRequest, NotUsed], Future[HelloReply]] =
      new GenericFilter[Source[HelloRequest, akka.NotUsed], Future[HelloReply]] {
        override def apply(
            request: Source[HelloRequest, NotUsed],
            next: Source[HelloRequest, NotUsed] => Future[HelloReply])(
            implicit ex: ExecutionContext): Future[HelloReply] = {
          val in = request
          val transformed: Source[HelloRequest, akka.NotUsed] = in.map { item =>
            addToRequest(item)
          }
          next(transformed)
        }
      }

    val addPower: PowerFilter[Source[HelloRequest, NotUsed], Future[HelloReply]] =
      new PowerFilter[Source[HelloRequest, akka.NotUsed], Future[HelloReply]] {
        override def apply(
            request: (Source[HelloRequest, NotUsed], Metadata),
            next: (Source[HelloRequest, NotUsed], Metadata) => Future[HelloReply])(
            implicit ex: ExecutionContext): Future[HelloReply] = {
          val in = request._1
          val transformed: Source[HelloRequest, akka.NotUsed] = in.map { item =>
            addToRequest(item)
          }
          next(transformed, request._2)
        }
      }

    val remove: GenericFilter[Source[HelloRequest, NotUsed], Future[HelloReply]] =
      new GenericFilter[Source[HelloRequest, akka.NotUsed], Future[HelloReply]] {
        override def apply(
            request: Source[HelloRequest, NotUsed],
            next: Source[HelloRequest, NotUsed] => Future[HelloReply])(
            implicit ex: ExecutionContext): Future[HelloReply] = {
          next(request).map(removeFromResponse)
        }
      }

    val removePower: PowerFilter[Source[HelloRequest, NotUsed], Future[HelloReply]] =
      new PowerFilter[Source[HelloRequest, akka.NotUsed], Future[HelloReply]] {
        override def apply(
            request: (Source[HelloRequest, NotUsed], Metadata),
            next: (Source[HelloRequest, NotUsed], Metadata) => Future[HelloReply])(
            implicit ex: ExecutionContext): Future[HelloReply] = {
          next(request._1, request._2).map(removeFromResponse)
        }
      }
  }

  object RequestStreamResponse {
    val add: GenericFilter[HelloRequest, Source[HelloReply, akka.NotUsed]] =
      new GenericFilter[HelloRequest, Source[HelloReply, akka.NotUsed]] {
        override def apply(request: HelloRequest, next: HelloRequest => Source[HelloReply, akka.NotUsed])(
            implicit ex: ExecutionContext): Source[HelloReply, akka.NotUsed] = {
          next(addToRequestChar(request))
        }
      }

    val addPower: PowerFilter[HelloRequest, Source[HelloReply, akka.NotUsed]] =
      new PowerFilter[HelloRequest, Source[HelloReply, akka.NotUsed]] {
        override def apply(
            request: (HelloRequest, Metadata),
            next: (HelloRequest, Metadata) => Source[HelloReply, akka.NotUsed])(
            implicit ex: ExecutionContext): Source[HelloReply, akka.NotUsed] = {
          next(addToRequest(request._1), request._2).map(removeFromResponse)
        }
      }

    val remove: GenericFilter[HelloRequest, Source[HelloReply, akka.NotUsed]] =
      new GenericFilter[HelloRequest, Source[HelloReply, akka.NotUsed]] {
        override def apply(request: HelloRequest, next: HelloRequest => Source[HelloReply, akka.NotUsed])(
            implicit ex: ExecutionContext): Source[HelloReply, akka.NotUsed] = {
          next(request).map { resp =>
            removeFromResponseChar(resp)
          }
        }
      }
    val removePower: PowerFilter[HelloRequest, Source[HelloReply, akka.NotUsed]] =
      new PowerFilter[HelloRequest, Source[HelloReply, akka.NotUsed]] {
        override def apply(
            request: (HelloRequest, Metadata),
            next: (HelloRequest, Metadata) => Source[HelloReply, akka.NotUsed])(
            implicit ex: ExecutionContext): Source[HelloReply, akka.NotUsed] = {
          next(request._1, request._2).map(removeFromResponse)
        }
      }
  }

  object StreamRequestStreamResponse {
    val add: GenericFilter[Source[HelloRequest, akka.NotUsed], Source[HelloReply, akka.NotUsed]] =
      new GenericFilter[Source[HelloRequest, akka.NotUsed], Source[HelloReply, akka.NotUsed]] {
        override def apply(
            request: Source[HelloRequest, akka.NotUsed],
            next: Source[HelloRequest, akka.NotUsed] => Source[HelloReply, akka.NotUsed])(
            implicit ex: ExecutionContext): Source[HelloReply, akka.NotUsed] = {
          val in = request
          val transformed: Source[HelloRequest, akka.NotUsed] = in.map { item =>
            addToRequestChar(item)
          }
          next(transformed)
        }
      }

    val addPower: PowerFilter[Source[HelloRequest, akka.NotUsed], Source[HelloReply, akka.NotUsed]] =
      new PowerFilter[Source[HelloRequest, akka.NotUsed], Source[HelloReply, akka.NotUsed]] {
        override def apply(
            request: (Source[HelloRequest, akka.NotUsed], Metadata),
            next: (Source[HelloRequest, akka.NotUsed], Metadata) => Source[HelloReply, akka.NotUsed])(
            implicit ex: ExecutionContext): Source[HelloReply, akka.NotUsed] = {
          val in = request._1
          val transformed: Source[HelloRequest, akka.NotUsed] = in.map { item =>
            addToRequestChar(item)
          }
          next(transformed, request._2)
        }
      }

    val remove: GenericFilter[Source[HelloRequest, akka.NotUsed], Source[HelloReply, akka.NotUsed]] =
      new GenericFilter[Source[HelloRequest, akka.NotUsed], Source[HelloReply, akka.NotUsed]] {
        override def apply(
            request: Source[HelloRequest, akka.NotUsed],
            next: Source[HelloRequest, akka.NotUsed] => Source[HelloReply, akka.NotUsed])(
            implicit ex: ExecutionContext): Source[HelloReply, akka.NotUsed] = {
          next(request).map { resp =>
            removeFromResponseChar(resp)
          }
        }
      }

    val removePower: PowerFilter[Source[HelloRequest, akka.NotUsed], Source[HelloReply, akka.NotUsed]] =
      new PowerFilter[Source[HelloRequest, akka.NotUsed], Source[HelloReply, akka.NotUsed]] {
        override def apply(
            request: (Source[HelloRequest, akka.NotUsed], Metadata),
            next: (Source[HelloRequest, akka.NotUsed], Metadata) => Source[HelloReply, akka.NotUsed])(
            implicit ex: ExecutionContext): Source[HelloReply, akka.NotUsed] = {
          next(request._1, request._2).map(removeFromResponse)
        }
      }
  }

  private def addToRequest(item: HelloRequest): HelloRequest = item.copy(name = s"Mrs. ${item.name}")
  private def addToRequestChar(item: HelloRequest): HelloRequest =
    item.copy(name = item.name.toSeq.map(char => s"($char)").mkString)

  private def removeFromResponse(item: HelloReply): HelloReply = item.copy(message = item.message.replace("Mrs. ", ""))
  private def removeFromResponseChar(item: HelloReply): HelloReply =
    item.copy(message = item.message.replace("(", "").replace(")", ""))

}
