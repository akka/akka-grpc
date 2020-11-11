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
import akka.grpc.{ GrpcClientSettings, GrpcResponseMetadata, GrpcSingleResponse, ProtobufSerializer }
import akka.http.Http2Bridge
import akka.http.scaladsl.{ ClientTransport, ConnectionContext }
import akka.http.scaladsl.model.{ AttributeKey, HttpRequest, HttpResponse, RequestResponseAssociation, Uri }
import akka.http.scaladsl.settings.ClientConnectionSettings
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{ Keep, Sink, Source }
import com.github.ghik.silencer.silent
import io.grpc.{ CallOptions, MethodDescriptor }
import javax.net.ssl.{ KeyManager, SSLContext, TrustManager }

import scala.concurrent.{ Future, Promise }
import scala.util.{ Failure, Success }

/**
 * INTERNAL API
 */
@InternalApi
object AkkaHttpClientUtils {

  /**
   * INTERNAL API
   */
  @InternalApi
  @silent("never used")
  def createChannel(settings: GrpcClientSettings, log: LoggingAdapter)(
      implicit sys: ClassicActorSystemProvider): InternalChannel = {
    implicit val ec = sys.classicSystem.dispatcher
    implicit val writer = GrpcProtocolNative.newWriter(Codecs.supportedCodecs.head)

    // TODO FIXME discovery, loadbalancing etc
    val host = settings.serviceName

    val connectionContext = ConnectionContext.httpsClient {
      settings.trustManager match {
        case None => SSLContext.getDefault
        case Some(trustManager) =>
          val sslContext: SSLContext = SSLContext.getInstance("TLS")
          sslContext.init(Array[KeyManager](), Array[TrustManager](trustManager), new SecureRandom)
          sslContext
      }
    }

    val clientConnectionSettings = settings.overrideAuthority match {
      case None => ClientConnectionSettings(sys)
      case Some(authority) =>
        ClientConnectionSettings(sys).withTransport(ClientTransport.withCustomResolver((host, port) => {
          assert(host == authority)
          assert(port == settings.defaultPort)
          Future.successful(new InetSocketAddress(settings.serviceName, settings.defaultPort))
        }))
    }

    val (queue, doneFuture) =
      Source
        .queue[HttpRequest](4242, OverflowStrategy.fail)
        .via(
          Http2Bridge.connect(
            settings.overrideAuthority.getOrElse(host),
            settings.defaultPort,
            clientConnectionSettings,
            connectionContext))
        .toMat(Sink.foreach { res =>
          res.attribute(ResponsePromise.Key).get.promise.trySuccess(res)
        })(Keep.both)
        .run()

    def singleRequest(request: HttpRequest): Future[HttpResponse] = {
      val p = Promise[HttpResponse]()
      queue.offer(request.addAttribute(ResponsePromise.Key, ResponsePromise(p))).flatMap(_ => p.future)
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
        implicit val serializer: ProtobufSerializer[I] = descriptor
        val deserializer: ProtobufSerializer[O] = descriptor
        val httpRequest = GrpcRequestHelpers(
          Uri(s"https://${settings.overrideAuthority.getOrElse(host)}/" + descriptor.getFullMethodName),
          Source.single(request))
        singleRequest(httpRequest).flatMap(response => {
          httpRequest.toString()
          Codecs.detect(response) match {
            case Success(codec) =>
              implicit val reader = GrpcProtocolNative.newReader(codec)
              response.entity.dataBytes
                .via(reader.dataFrameDecoder)
                .map(data => deserializer.deserialize(data))
                // TODO probably shouldn't cancel after reading the element?
                .runWith(Sink.head[O])
            case Failure(e) =>
              Future.failed(e)
          }
        })
      }

      override def invokeWithMetadata[I, O](
          request: I,
          headers: MetadataImpl,
          descriptor: MethodDescriptor[I, O],
          options: CallOptions): Future[GrpcSingleResponse[O]] = ???

      override def invokeWithMetadata[I, O](
          source: Source[I, NotUsed],
          fqMethodName: String,
          headers: MetadataImpl,
          descriptor: MethodDescriptor[I, O],
          streamingResponse: Boolean,
          options: CallOptions): Source[O, Future[GrpcResponseMetadata]] = {
        implicit val serializer: ProtobufSerializer[I] = descriptor
        val deserializer: ProtobufSerializer[O] = descriptor
        val httpRequest = GrpcRequestHelpers(
          Uri(s"https://${settings.overrideAuthority.getOrElse(host)}/" + descriptor.getFullMethodName),
          source)
        Source.lazyFutureSource[O, Future[GrpcResponseMetadata]](() => {
          singleRequest(httpRequest).map { response =>
            {
              Codecs.detect(response) match {
                case Success(codec) =>
                  implicit val reader = GrpcProtocolNative.newReader(codec)
                  response.entity.dataBytes
                    .via(reader.dataFrameDecoder)
                    .map(data => deserializer.deserialize(data))
                    .mapMaterializedValue(_ =>
                      // TODO actually look at the chunks so we can construct the metadata including trailers
                      Future.successful(new GrpcResponseMetadata() {

                        /**
                         * Scala API: The response metadata, the metadata is only for reading and must not be mutated.
                         */
                        override def headers: akka.grpc.scaladsl.Metadata = ???

                        /**
                         * Java API: The response metadata, the metadata is only for reading and must not be mutated.
                         */
                        override def getHeaders(): akka.grpc.javadsl.Metadata = ???

                        /**
                         * Scala API: Trailers from the server, is completed after the response stream completes
                         */
                        override def trailers: Future[akka.grpc.scaladsl.Metadata] = ???

                        /**
                         * Java API: Trailers from the server, is completed after the response stream completes
                         */
                        override def getTrailers(): CompletionStage[akka.grpc.javadsl.Metadata] = ???
                      }))
                case Failure(e) =>
                  Source.failed[O](e).mapMaterializedValue(_ => Future.failed(e))
              }
            }
          }
        })
      }.mapMaterializedValue(_.flatten)
    }
  }

  case class ResponsePromise(promise: Promise[HttpResponse]) extends RequestResponseAssociation
  object ResponsePromise {
    val Key = AttributeKey[ResponsePromise]("association-handle")
  }
}
