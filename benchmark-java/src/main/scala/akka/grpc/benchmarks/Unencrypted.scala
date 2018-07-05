/**
 * Copyright (C) 2009-2018 Lightbend Inc. <http://www.lightbend.com>
 */
package akka.grpc.benchmarks

import java.util.concurrent.CompletionStage

import akka.actor.ActorSystem
import akka.http.javadsl.ConnectHttp
import akka.http.javadsl.model.HttpRequest
import akka.http.javadsl.model.HttpResponse
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.{ConnectionContext, Http, UseHttp2}
import akka.stream.Materializer

import scala.compat.java8.FutureConverters._
import scala.concurrent.Future

object Unencrypted {

  // needed to bind unencrypted http2 due to short coming in Javadsl with 10.1.3
  // can be removed when https://github.com/akka/akka-http/issues/2110 is fixed
  def connect(system: ActorSystem, f: akka.japi.Function[HttpRequest, CompletionStage[HttpResponse]], connect: ConnectHttp, mat: Materializer): CompletionStage[ServerBinding] = {
    Http(system)
      .bindAndHandleAsync(
        r => f.apply(r).toScala.asInstanceOf[Future[akka.http.scaladsl.model.HttpResponse]],
        connect.host,
        connect.port,
        ConnectionContext.noEncryption()(UseHttp2.Always))(mat)
      .toJava
  }
}
