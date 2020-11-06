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
import akka.http.Http2Bridge
import akka.http.scaladsl.model.HttpEntity.{ Chunk, ChunkStreamPart, Chunked, LastChunk }
import akka.http.scaladsl.{ ClientTransport, ConnectionContext }
import akka.http.scaladsl.model.{ AttributeKey, HttpHeader, HttpRequest, HttpResponse, RequestResponseAssociation, Uri }
import akka.http.scaladsl.settings.ClientConnectionSettings
import akka.stream.OverflowStrategy
import akka.stream.scaladsl.{ Keep, Sink, Source }
import akka.util.ByteString
import com.github.ghik.silencer.silent
import io.grpc.{ CallOptions, MethodDescriptor, Status, StatusRuntimeException }
import javax.net.ssl.{ KeyManager, SSLContext, TrustManager }

import scala.collection.immutable
import scala.compat.java8.FutureConverters.FutureOps
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

    val connectionContext =
      // TODO https://github.com/akka/akka-http/pull/3586
      //if (settings.useTls)
      ConnectionContext.httpsClient {
        settings.trustManager match {
          case None => SSLContext.getDefault
          case Some(trustManager) =>
            val sslContext: SSLContext = SSLContext.getInstance("TLS")
            sslContext.init(Array[KeyManager](), Array[TrustManager](trustManager), new SecureRandom)
            sslContext
        }
      }
//      else
//        HttpConnectionContext()

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
            connectionContext,
            clientConnectionSettings,
            log))
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
                  log.info(s"XXX response $response started")
                  implicit val reader: GrpcProtocolReader = GrpcProtocolNative.newReader(codec)
                  val trailerPromise = Promise[immutable.Seq[HttpHeader]]()
                  // Completed with success or failure based on grpc-status and grpc-message trailing headers
                  val completionFuture: Future[Unit] = trailerPromise.future.flatMap(parseResponseStatus)
                  completionFuture.foreach(_ => log.info(s"XXX response $response completion"))

                  response.entity match {
                    case Chunked(_, chunks) =>
                      chunks
                        .concat(Source
                          .maybe[ChunkStreamPart]
                          .mapMaterializedValue(promise => promise.completeWith(completionFuture.map(_ => None))))
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
                    case _ => throw new IllegalStateException("Expected chunked response")
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

  private def parseResponseStatus(trailers: Seq[HttpHeader]): Future[Unit] = {
    trailers.find(_.name == "grpc-status").map(_.value) match {
      case Some("0") =>
        Future.successful(())
      case None =>
        Future.failed(new StatusRuntimeException(Status.INTERNAL.withDescription("No return status found")))
      case Some(statusCode) => {
        val description = trailers.find(_.name == "grpc-message").map(_.value)
        Future.failed(
          new StatusRuntimeException(Status.fromCodeValue(statusCode.toInt).withDescription(description.orNull)))
      }
    }
  }

  case class ResponsePromise(promise: Promise[HttpResponse]) extends RequestResponseAssociation
  object ResponsePromise {
    val Key = AttributeKey[ResponsePromise]("association-handle")
  }
}
