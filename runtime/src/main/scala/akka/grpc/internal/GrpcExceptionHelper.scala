/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import akka.japi.{ Function => jFunction }
import akka.http.javadsl.model.{ HttpHeader => jHttpHeader }
import akka.http.scaladsl.model.{ HttpHeader => sHttpHeader }
import akka.http.scaladsl.model.headers.{ RawHeader => sRawHeader }
import akka.grpc.javadsl.{ GrpcErrorResponse => jGrpcErrorResponse }
import akka.grpc.scaladsl.{ GrpcErrorResponse => sGrpcErrorResponse }
import scala.collection.JavaConverters._

import akka.grpc.javadsl.{ scalaAnonymousPartialFunction, scalaPartialFunction }
import akka.actor.ActorSystem
import akka.annotation.InternalApi
import java.lang.{ Iterable => jIterable }

@InternalApi
object GrpcExceptionHelper {
  def asScala(r: jGrpcErrorResponse): sGrpcErrorResponse =
    sGrpcErrorResponse(r.status, r.headers.asScala.map(asScala).toSeq)

  def asScala(h: jHttpHeader): sHttpHeader = h match {
    case s: sHttpHeader => s
    case _              => sRawHeader(h.value, h.name)
  }

  def asScala(i: jIterable[jHttpHeader]): Seq[sHttpHeader] =
    i.asScala.map(asScala).toSeq

  def asScala(m: jFunction[ActorSystem, jFunction[Throwable, jGrpcErrorResponse]])
      : ActorSystem => PartialFunction[Throwable, sGrpcErrorResponse] =
    scalaAnonymousPartialFunction(m).andThen(f => f.andThen(asScala _))

}
