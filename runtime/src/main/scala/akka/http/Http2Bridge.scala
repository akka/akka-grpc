/*
 * Copyright (C) 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.http

import akka.actor.ClassicActorSystemProvider
import akka.event.LoggingAdapter
import akka.http.impl.engine.http2.Http2
import akka.http.scaladsl.HttpsConnectionContext
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import akka.http.scaladsl.settings.ClientConnectionSettings
import akka.stream.scaladsl.Flow

/**
 * TODO FIXME remove as soon as we have a public HTTP2 client API
 * https://github.com/akka/akka-http/pull/3511 and further
 */
object Http2Bridge {
  def connect(
      host: String,
      port: Int,
      connectionContext: HttpsConnectionContext,
      settings: ClientConnectionSettings,
      log: LoggingAdapter)(implicit sys: ClassicActorSystemProvider): Flow[HttpRequest, HttpResponse, Any] = {
    Http2().outgoingConnection(host, port, connectionContext, settings, log)
  }
}
