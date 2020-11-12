/*
 * Copyright (C) 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import java.net.InetSocketAddress
import java.security.SecureRandom
import java.util.concurrent.CompletionStage

import akka.{ Done, NotUsed }
import akka.actor.ClassicActorSystemProvider
import akka.annotation.InternalApi
import akka.event.LoggingAdapter
import akka.grpc.GrpcProtocol.GrpcProtocolReader
import akka.grpc.{ GrpcClientSettings, GrpcResponseMetadata, GrpcSingleResponse, ProtobufSerializer }
import akka.http.scaladsl.model.HttpEntity.{ Chunk, Chunked, Default, LastChunk }
import akka.http.scaladsl.{ ClientTransport, ConnectionContext, Http }
import akka.http.scaladsl.model.{ AttributeKey, HttpHeader, HttpRequest, HttpResponse, RequestResponseAssociation, Uri }
import akka.http.scaladsl.settings.ClientConnectionSettings
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{ Keep, Sink, Source }
import akka.util.ByteString
import io.grpc.{ CallOptions, MethodDescriptor, Status, StatusRuntimeException }
import javax.net.ssl.{ KeyManager, SSLContext, TrustManager }

import scala.collection.immutable
import scala.compat.java8.FutureConverters.FutureOps
import scala.concurrent.{ Future, Promise }
import scala.util.{ Failure, Success }

import akka.http.scaladsl.model.StatusCodes

/**
 * INTERNAL API
 */
@InternalApi
object AkkaHttpClientUtils {

  /**
   * INTERNAL API
   */
  @InternalApi
  def createChannel(settings: GrpcClientSettings, log: LoggingAdapter)(
      implicit sys: ClassicActorSystemProvider): InternalChannel = {
    implicit val ec = sys.classicSystem.dispatcher

    // https://github.com/grpc/grpc/blob/master/doc/compression.md
    // since a client can't assume what algorithms a server supports, we
    // must default to no compression.
    // Configuring a different default could be a future feature.
    // Configuring compression per call could be a future power API feature.
    implicit val writer = GrpcProtocolNative.newWriter(Identity)

    // TODO FIXME discovery, loadbalancing etc
    val host = settings.serviceName

    val clientConnectionSettings = settings.overrideAuthority match {
      case None => ClientConnectionSettings(sys)
      case Some(authority) =>
        ClientConnectionSettings(sys).withTransport(ClientTransport.withCustomResolver((host, port) => {
          assert(host == authority)
          assert(port == settings.defaultPort)
          Future.successful(new InetSocketAddress(settings.serviceName, settings.defaultPort))
        }))
    }

    val builder = Http()
      .connectionTo(settings.overrideAuthority.getOrElse(host))
      .toPort(settings.defaultPort)
      .withClientConnectionSettings(clientConnectionSettings)

    val http2client =
      if (settings.useTls) {
        val connectionContext =
          ConnectionContext.httpsClient {
            settings.trustManager match {
              case None => SSLContext.getDefault
              case Some(trustManager) =>
                val sslContext: SSLContext = SSLContext.getInstance("TLS")
                sslContext.init(Array[KeyManager](), Array[TrustManager](trustManager), new SecureRandom)
                sslContext
            }
          }

        builder.withCustomHttpsConnectionContext(connectionContext).http2()
      } else {
        builder.http2WithPriorKnowledge()
      }

    val (queue, doneFuture) =
      Source
        .queue[HttpRequest](4242, OverflowStrategy.fail)
        .via(http2client)
        .toMat(Sink.foreach { res =>
          res.attribute(ResponsePromise.Key).get.promise.trySuccess(res)
        })(Keep.both)
        .run()

    def singleRequest(request: HttpRequest): Future[HttpResponse] = {
      val p = Promise[HttpResponse]()
      queue
        .offer(request.addAttribute(ResponsePromise.Key, ResponsePromise(p)))
        .flatMap(o => {
          log.info(s"XXX enqueue: $o")
          p.future.onComplete(t => log.info(s"XXX dequeue: ${t.isSuccess}"))
          p.future
        })
    }

    implicit def serializerFromMethodDescriptor[I, O](descriptor: MethodDescriptor[I, O]): ProtobufSerializer[I] =
      descriptor.getRequestMarshaller.asInstanceOf[WithProtobufSerializer[I]].protobufSerializer
    implicit def deserializerFromMethodDescriptor[I, O](descriptor: MethodDescriptor[I, O]): ProtobufSerializer[O] =
      descriptor.getResponseMarshaller.asInstanceOf[WithProtobufSerializer[O]].protobufSerializer

    new InternalChannel() {
      override def shutdown(): Unit = queue.complete()
      override def done: Future[Done] = doneFuture

      override def invoke[I, O](
          request: I,
          headers: MetadataImpl,
          descriptor: MethodDescriptor[I, O],
          options: CallOptions): Future[O] = {
        val src =
          invokeWithMetadata(Source.single(request), descriptor.getFullMethodName, headers, descriptor, false, options)
        val result = src.toMat(Sink.headOption)(Keep.right).run()
        result.flatMap {
          case Some(r) => Future.successful(r)
          case None    => Future.failed(throw new IllegalStateException("Successful status code but no data found"))
        }
      }

      override def invokeWithMetadata[I, O](
          request: I,
          headers: MetadataImpl,
          descriptor: MethodDescriptor[I, O],
          options: CallOptions): Future[GrpcSingleResponse[O]] = ???

      override def invokeWithMetadata[I, O](
          source: Source[I, NotUsed],
          headers: MetadataImpl,
          descriptor: MethodDescriptor[I, O],
          streamingResponse: Boolean,
          options: CallOptions): Source[O, Future[GrpcResponseMetadata]] = {
        implicit val serializer: ProtobufSerializer[I] = descriptor
        val deserializer: ProtobufSerializer[O] = descriptor
        val scheme = if (settings.useTls) "https" else "http"
        val httpRequest = GrpcRequestHelpers(
          Uri(s"${scheme}://${settings.overrideAuthority.getOrElse(host)}/" + descriptor.getFullMethodName),
          source)
        Source.lazyFutureSource[O, Future[GrpcResponseMetadata]](() => {
          singleRequest(httpRequest).map { response =>
            {
              Codecs.detect(response) match {
                case Success(codec) =>
                  log.info(s"XXX response $response started")
                  implicit val reader: GrpcProtocolReader = GrpcProtocolNative.newReader(codec)
                  val trailerPromise = Promise[immutable.Seq[HttpHeader]]()
                  // Completed with success or failure based on grpc-status and grpc-message trailing headers
                  val completionFuture: Future[Unit] =
                    trailerPromise.future.flatMap(trailers => parseResponseStatus(response, trailers))
                  completionFuture.foreach(_ => log.info(s"XXX response $response completion"))

                  response.entity match {
                    case Chunked(_, chunks) =>
                      chunks
                        .map {
                          case Chunk(data, _) =>
                            data
                          case LastChunk(_, trailer) =>
                            trailerPromise.success(trailer)
                            ByteString.empty
                        }
                        .watchTermination()((_, done) =>
                          done.onComplete(c => {
                            log.info(s"XXX response $response termination ${c.isSuccess}")
                            trailerPromise.trySuccess(immutable.Seq.empty)
                          }))
                        // This never adds any data to the stream, but makes sure it fails with the correct error code if applicable
                        .concat(Source
                          .maybe[ByteString]
                          .mapMaterializedValue(promise => promise.completeWith(completionFuture.map(_ => None))))
                        // Make sure we continue reading to get the trailing header even if we're no longer interested in the rest of the body
                        .via(new CancellationBarrierGraphStage)
                        .via(reader.dataFrameDecoder)
                        .map(data => deserializer.deserialize(data))
                        .mapMaterializedValue(_ =>
                          Future.successful(new GrpcResponseMetadata() {
                            override def headers: akka.grpc.scaladsl.Metadata =
                              new HeaderMetadataImpl(response.headers)

                            override def getHeaders(): akka.grpc.javadsl.Metadata =
                              new JavaMetadataImpl(new HeaderMetadataImpl(response.headers))

                            override def trailers: Future[akka.grpc.scaladsl.Metadata] =
                              trailerPromise.future.map(new HeaderMetadataImpl(_))

                            override def getTrailers(): CompletionStage[akka.grpc.javadsl.Metadata] =
                              trailerPromise.future
                                .map[akka.grpc.javadsl.Metadata](h => new JavaMetadataImpl(new HeaderMetadataImpl(h)))
                                .toJava
                          }))
                    case Default(_, _, _) => throw mapToStatusException(response, Seq.empty)
                    case e                => throw new IllegalStateException(s"Expected chunked or default response but got $e")
                  }
                case Failure(e) =>
                  Source.failed[O](e).mapMaterializedValue(_ => Future.failed(e))
              }
            }
          }
        })
      }.mapMaterializedValue(_.flatten)
    }
  }

  private def parseResponseStatus(response: HttpResponse, trailers: Seq[HttpHeader]): Future[Unit] = {
    val allHeaders = response.headers ++ trailers
    allHeaders.find(_.name == "grpc-status").map(_.value) match {
      case Some("0") =>
        Future.successful(())
      case _ =>
        Future.failed(mapToStatusException(response, trailers))
    }
  }

  private def mapToStatusException(response: HttpResponse, trailers: Seq[HttpHeader]): StatusRuntimeException = {
    val allHeaders = response.headers ++ trailers
    allHeaders.find(_.name == "grpc-status").map(_.value) match {
      case None =>
        new StatusRuntimeException(mapHttpStatus(response).withDescription("No grpc-status found"))
      case Some(statusCode) =>
        val description = allHeaders.find(_.name == "grpc-message").map(_.value)
        new StatusRuntimeException(Status.fromCodeValue(statusCode.toInt).withDescription(description.orNull))
    }
  }

  /**
   * See https://grpc.github.io/grpc/core/md_doc_http-grpc-status-mapping.html
   */
  private def mapHttpStatus(response: HttpResponse): Status = {
    response.status match {
      case StatusCodes.BadRequest         => Status.INTERNAL
      case StatusCodes.Unauthorized       => Status.UNAUTHENTICATED
      case StatusCodes.Forbidden          => Status.PERMISSION_DENIED
      case StatusCodes.NotFound           => Status.UNIMPLEMENTED
      case StatusCodes.TooManyRequests    => Status.UNAVAILABLE
      case StatusCodes.BadGateway         => Status.UNAVAILABLE
      case StatusCodes.ServiceUnavailable => Status.UNAVAILABLE
      case StatusCodes.GatewayTimeout     => Status.UNAVAILABLE
      case _                              => Status.UNKNOWN
    }
  }

  case class ResponsePromise(promise: Promise[HttpResponse]) extends RequestResponseAssociation
  object ResponsePromise {
    val Key = AttributeKey[ResponsePromise]("association-handle")
  }
}
