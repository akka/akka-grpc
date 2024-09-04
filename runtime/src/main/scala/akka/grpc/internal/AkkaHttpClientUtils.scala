/*
 * Copyright (C) 2020-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import akka.actor.ClassicActorSystemProvider
import akka.annotation.InternalApi
import akka.event.LoggingAdapter
import akka.grpc.GrpcProtocol.GrpcProtocolReader
import akka.grpc._
import akka.http.scaladsl.model.HttpEntity.Chunk
import akka.http.scaladsl.model.HttpEntity.Chunked
import akka.http.scaladsl.model.HttpEntity.LastChunk
import akka.http.scaladsl.model.HttpEntity.Strict
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.settings.ClientConnectionSettings
import akka.http.scaladsl.ClientTransport
import akka.http.scaladsl.ConnectionContext
import akka.http.scaladsl.Http
import akka.http.scaladsl.HttpsConnectionContext
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.GraphDSL
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.stream.FlowShape
import akka.stream.Materializer
import akka.stream.OverflowStrategy
import akka.util.ByteString
import akka.Done
import akka.NotUsed
import io.grpc.CallOptions
import io.grpc.MethodDescriptor
import io.grpc.Status
import io.grpc.StatusRuntimeException

import java.net.InetSocketAddress
import java.security.SecureRandom
import java.util.concurrent.CompletionStage
import javax.net.ssl.KeyManager
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import scala.collection.immutable
import scala.compat.java8.FutureConverters.FutureOps
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.util.Failure
import scala.util.Success

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

    log.debug("Creating gRPC client channel")

    // https://github.com/grpc/grpc/blob/master/doc/compression.md
    // since a client can't assume what algorithms a server supports, we
    // must default to no compression.
    // Configuring a different default could be a future feature.
    // Configuring compression per call could be a future power API feature.
    implicit val writer = GrpcProtocolNative.newWriter(Identity)

    // TODO FIXME adapt to new API's for discovery, loadbalancing etc
    // https://github.com/akka/akka-grpc/issues/1196
    // https://github.com/akka/akka-grpc/issues/1197

    var roundRobin: Int = 0
    val clientConnectionSettings =
      ClientConnectionSettings(sys).withTransport(ClientTransport.withCustomResolver((host, _) => {
        settings.overrideAuthority.foreach { authority =>
          if (host != authority)
            throw new IllegalArgumentException(s"Unexpected host [$host], expected authority [$authority]")
        }
        settings.serviceDiscovery.lookup(settings.serviceName, 10.seconds).map { resolved =>
          // quasi-roundrobin is nicer than random selection: somewhat lower chance of making
          // an 'unlucky choice' multiple times in a row.
          roundRobin += 1
          val target = resolved.addresses(roundRobin % resolved.addresses.size)
          target.address match {
            case Some(address) =>
              new InetSocketAddress(address, target.port.getOrElse(settings.defaultPort))
            case None =>
              new InetSocketAddress(target.host, target.port.getOrElse(settings.defaultPort))
          }
        }
      }))

    val builder = Http()
      .connectionTo(settings.overrideAuthority.getOrElse(settings.serviceName))
      .withClientConnectionSettings(clientConnectionSettings)

    val http2client =
      if (settings.useTls) {
        val connectionContext: HttpsConnectionContext =
          settings.sslContextProvider
            .map(provider => ConnectionContext.httpsClient((host, port) => provider().createSSLEngine(host, port)))
            .getOrElse {
              val sslContext = settings.sslContext.getOrElse(settings.trustManager match {
                case None => SSLContext.getDefault
                case Some(trustManager) =>
                  val sslContext: SSLContext = SSLContext.getInstance("TLS")
                  sslContext.init(Array[KeyManager](), Array[TrustManager](trustManager), new SecureRandom)
                  sslContext
              })
              ConnectionContext.httpsClient(sslContext)
            }

        builder.withCustomHttpsConnectionContext(connectionContext).managedPersistentHttp2()
      } else {
        builder.managedPersistentHttp2WithPriorKnowledge()
      }

    // make sure we always fail all queued on http client fail to connect
    val cancelFailed: Flow[HttpRequest, HttpRequest, NotUsed] = {
      Flow.fromGraph(GraphDSL.create() { implicit b =>
        import GraphDSL.Implicits._

        val switch = b.add(new SwitchOnCancel[HttpRequest])

        // when failed over
        val failover = b.add(Sink.foreach[(Throwable, HttpRequest)] {
          case (error, request) =>
            request.entity.discardBytes()
            request.getAttribute(ResponsePromise.Key).get().promise.tryFailure(error)
        })
        switch.out1 ~> failover.in

        FlowShape[HttpRequest, HttpRequest](switch.in, switch.out0)
      })
    }

    val (queue, doneFuture) =
      Source
        .queue[HttpRequest](4242, OverflowStrategy.fail)
        .via(cancelFailed)
        .via(http2client)
        .toMat(Sink.foreach { res =>
          res.attribute(ResponsePromise.Key).get.promise.trySuccess(res)
        })(Keep.both)
        .run()

    def singleRequest(request: HttpRequest): Future[HttpResponse] = {
      val p = Promise[HttpResponse]()
      queue.offer(request.addAttribute(ResponsePromise.Key, ResponsePromise(p))).flatMap(_ => p.future).recover {
        case ex: RuntimeException if ex.getMessage.contains("Connection failed") =>
          throw new GrpcServiceException(
            Status.UNAVAILABLE
              .withCause(ex)
              .withDescription(s"Connection to ${settings.serviceName}:${settings.defaultPort} failed"))
      }
    }

    def serializerFromMethodDescriptor[I, O](descriptor: MethodDescriptor[I, O]): ProtobufSerializer[I] =
      descriptor.getRequestMarshaller.asInstanceOf[WithProtobufSerializer[I]].protobufSerializer

    def deserializerFromMethodDescriptor[I, O](descriptor: MethodDescriptor[I, O]): ProtobufSerializer[O] =
      descriptor.getResponseMarshaller.asInstanceOf[WithProtobufSerializer[O]].protobufSerializer

    new InternalChannel() {
      override def shutdown(): Unit = queue.complete()

      override def done: Future[Done] = doneFuture

      override def invoke[I, O](
          request: I,
          headers: MetadataImpl,
          descriptor: MethodDescriptor[I, O],
          options: CallOptions): Future[O] =
        invokeWithMetadata(request, headers, descriptor, options).map(_.value)

      override def invokeWithMetadata[I, O](
          request: I,
          headers: MetadataImpl,
          descriptor: MethodDescriptor[I, O],
          options: CallOptions): Future[GrpcSingleResponse[O]] = {
        val src =
          invokeWithMetadata(Source.single(request), headers, descriptor, streamingResponse = false, options)
        val (metadataFuture, resultFuture) = src.toMat(Sink.head)(Keep.both).run()
        metadataFuture.zip(resultFuture).map {
          case (metadata, result) =>
            new GrpcSingleResponse[O] {
              def value: O = result

              def getValue(): O = result

              def headers = metadata.headers

              def getHeaders() = metadata.getHeaders()

              def trailers = metadata.trailers

              def getTrailers() = metadata.getTrailers()
            }
        }
      }

      override def invokeWithMetadata[I, O](
          source: Source[I, NotUsed],
          headers: MetadataImpl,
          descriptor: MethodDescriptor[I, O],
          streamingResponse: Boolean,
          options: CallOptions): Source[O, Future[GrpcResponseMetadata]] = {
        implicit val serializer: ProtobufSerializer[I] = serializerFromMethodDescriptor(descriptor)
        val deserializer: ProtobufSerializer[O] = deserializerFromMethodDescriptor(descriptor)
        val scheme = if (settings.useTls) "https" else "http"
        val httpRequest = GrpcRequestHelpers(
          Uri(
            s"${scheme}://${settings.overrideAuthority.getOrElse(settings.serviceName)}/" + descriptor.getFullMethodName),
          GrpcEntityHelpers.metadataHeaders(headers.entries),
          source)
        responseToSource(httpRequest.uri, singleRequest(httpRequest), deserializer, streamingResponse)
      }
    }
  }

  /**
   * INTERNAL API
   */
  @InternalApi
  def responseToSource[O](
      requestUri: Uri,
      response: Future[HttpResponse],
      deserializer: ProtobufSerializer[O],
      streamingResponse: Boolean)(
      implicit ec: ExecutionContext,
      mat: Materializer): Source[O, Future[GrpcResponseMetadata]] = {
    Source.lazyFutureSource[O, Future[GrpcResponseMetadata]](() => {
      response.map { response =>
        {
          if (response.status != StatusCodes.OK) {
            response.entity.discardBytes()
            val failure = mapToStatusException(requestUri, response, immutable.Seq.empty)
            Source.failed(failure).mapMaterializedValue(_ => Future.failed(failure))
          } else {
            Codecs.detect(response) match {
              case Success(codec) =>
                implicit val reader: GrpcProtocolReader = GrpcProtocolNative.newReader(codec)
                val trailerPromise = Promise[immutable.Seq[HttpHeader]]()
                // Completed with success or failure based on grpc-status and grpc-message trailing headers
                val completionFuture: Future[Unit] =
                  trailerPromise.future.flatMap(trailers => parseResponseStatus(requestUri, response, trailers))

                val responseData =
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
                          done.onComplete(_ => trailerPromise.trySuccess(immutable.Seq.empty)))
                    case Strict(_, data) =>
                      val rawTrailers = response.attribute(AttributeKeys.trailer).map(_.headers).getOrElse(Seq.empty)
                      val trailers = rawTrailers.map(h => RawHeader(h._1, h._2))
                      trailerPromise.success(trailers)
                      Source.single[ByteString](data)
                    case _ =>
                      response.entity.discardBytes()
                      throw mapToStatusException(requestUri, response, Seq.empty)
                  }
                val baseFlow = responseData
                  // This never adds any data to the stream, but makes sure it fails with the correct error code if applicable
                  .concat(
                    Source
                      .maybe[ByteString]
                      .mapMaterializedValue(promise => promise.completeWith(completionFuture.map(_ => None))))
                val flow = if (streamingResponse) {
                  baseFlow
                } else {
                  // Make sure we continue reading to get the trailing header even if we're no longer interested in the rest of the body
                  baseFlow.via(new CancellationBarrierGraphStage)
                }
                flow
                  .via(reader.dataFrameDecoder)
                  .map(deserializer.deserialize)
                  .mapMaterializedValue(_ =>
                    Future.successful(new GrpcResponseMetadata() {
                      override def headers: akka.grpc.scaladsl.Metadata =
                        new HttpMessageMetadataImpl(response)

                      override def getHeaders(): akka.grpc.javadsl.Metadata =
                        new JavaMetadataImpl(new HttpMessageMetadataImpl(response))

                      override def trailers: Future[akka.grpc.scaladsl.Metadata] =
                        trailerPromise.future.map(new HeaderMetadataImpl(_))

                      override def getTrailers(): CompletionStage[akka.grpc.javadsl.Metadata] =
                        trailerPromise.future
                          .map[akka.grpc.javadsl.Metadata](h => new JavaMetadataImpl(new HeaderMetadataImpl(h)))
                          .toJava
                    }))
              case Failure(e) =>
                Source.failed[O](e).mapMaterializedValue(_ => Future.failed(e))
            }
          }
        }
      }
    })
  }.mapMaterializedValue(_.flatten)

  private def parseResponseStatus(requestUri: Uri, response: HttpResponse, trailers: Seq[HttpHeader]): Future[Unit] = {
    val allHeaders = response.headers ++ trailers
    allHeaders.find(_.name == "grpc-status").map(_.value) match {
      case Some("0") =>
        Future.successful(())
      case _ =>
        Future.failed(mapToStatusException(requestUri, response, trailers))
    }
  }

  private def mapToStatusException(
      requestUri: Uri,
      response: HttpResponse,
      trailers: Seq[HttpHeader]): StatusRuntimeException = {
    val allHeaders = response.headers ++ trailers
    val metadata: io.grpc.Metadata = new MetadataImpl(new HeaderMetadataImpl(allHeaders).asList).toGoogleGrpcMetadata()
    allHeaders.find(_.name == "grpc-status").map(_.value) match {
      case None =>
        val status = mapHttpStatus(response)
          .withDescription("No grpc-status found")
          .augmentDescription(s"When calling rpc service: ${requestUri.toString()}")
        new StatusRuntimeException(status, metadata)
      case Some(statusCode) =>
        val description = allHeaders.find(_.name == "grpc-message").map(_.value)
        val status = Status
          .fromCodeValue(statusCode.toInt)
          .withDescription(description.orNull)
          .augmentDescription(s"When calling rpc service: ${requestUri.toString()}")
        new StatusRuntimeException(status, metadata)
    }
  }

  /**
   * See https://github.com/grpc/grpc/blob/master/doc/http-grpc-status-mapping.md
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
