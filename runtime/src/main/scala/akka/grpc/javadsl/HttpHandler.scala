package akka.grpc.javadsl

import akka.NotUsed
import akka.grpc.GrpcProtocol
import akka.grpc.internal.HttpTranscoding._
import akka.grpc.internal.{ Codecs, GrpcProtocolNative }
import akka.http.javadsl.unmarshalling.{ Unmarshaller => JUnmarshaller }
import akka.http.javadsl.{ model => jm }
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.japi.function.{ Function => JFunction }
import akka.stream.scaladsl.{ Sink, Source }
import akka.stream.{ Materializer, javadsl => jstream }
import akka.util.ByteString
import com.google.api.http.HttpRule
import com.google.protobuf.Descriptors._
import com.google.protobuf.any.{ Any => ProtobufAny }
import com.google.protobuf.{ DynamicMessage, MessageOrBuilder, ByteString => ProtobufByteString }

import java.util.concurrent.CompletionStage
import java.util.regex.Matcher
import scala.collection.JavaConverters._
import scala.compat.java8.FutureConverters._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.control.NonFatal
import scala.util.{ Failure, Success }

final class HttpHandler(
    val methDesc: MethodDescriptor,
    val rule: HttpRule,
    grpcHandler: JFunction[jm.HttpRequest, CompletionStage[jm.HttpResponse]])(
    implicit mat: Materializer,
    ec: ExecutionContext)
    extends akka.grpc.HttpHandler
    with JFunction[jm.HttpRequest, CompletionStage[jm.HttpResponse]] {

  import akka.grpc.HttpHandler._
  private[this] def updateRequest(req: jm.HttpRequest, message: DynamicMessage): jm.HttpRequest = {
    req
      .withMethod(HttpMethods.POST)
      .withUri(Uri(path = Path / methDesc.getService.getFullName / methDesc.getName).toString())
      .withHeaders((req.getHeaders().asScala.toSeq :+ IdentityHeader).asJava)
      .withEntity(HttpEntity.Chunked(
        ContentTypes.`application/grpc+proto`,
        Source.single(grpcWriter.encodeFrame(GrpcProtocol.DataFrame(ByteString.fromArrayUnsafe(message.toByteArray))))))
      .withProtocol(HttpProtocols.`HTTP/2.0`)
  }

  private def transformRequest(req: jm.HttpRequest, matcher: Matcher): CompletionStage[_ <: jm.HttpRequest] = {
    if (rule.body.nonEmpty && req.entity.getContentType != ContentTypes.`application/json`) {
      Future.failed(IllegalRequestException(StatusCodes.BadRequest, "Content-type must be application/json!")).toJava
    } else {
      val inputBuilder = DynamicMessage.newBuilder(methDesc.getInputType)
      rule.body match {
        case "" => // Iff empty body rule, then only query parameters
          req.discardEntityBytes(mat)
          parseRequestParametersInto(methDesc, req.getUri.asScala().query().toMultiMap, inputBuilder)
          parsePathParametersInto(matcher, inputBuilder)
          Future.successful(updateRequest(req, inputBuilder.build)).toJava
        case "*" => // Iff * body rule, then no query parameters, and only fields not mapped in path variables
          val jf = japiFunction[String, jm.HttpRequest](str => {
            jsonParser.merge(str, inputBuilder)
            parsePathParametersInto(matcher, inputBuilder)
            updateRequest(req, inputBuilder.build)
          })
          JUnmarshaller.entityToString.unmarshal(req.entity(), mat).thenApply(jf.apply)
        case fieldName => // Iff fieldName body rule, then all parameters not mapped in path variables
          val jf = japiFunction[String, jm.HttpRequest](str => {
            val subField = lookupFieldByName(methDesc.getInputType, fieldName)
            val subInputBuilder = DynamicMessage.newBuilder(subField.getMessageType)
            jsonParser.merge(str, subInputBuilder)
            parseRequestParametersInto(methDesc, req.getUri.asScala().query().toMultiMap, inputBuilder)
            parsePathParametersInto(matcher, inputBuilder)
            inputBuilder.setField(subField, subInputBuilder.build())
            updateRequest(req, inputBuilder.build)
          })
          JUnmarshaller.entityToString.unmarshal(req.entity(), mat).thenApply(jf.apply)
      }
    }
  }
  private def transformResponse(
      grpcRequest: jm.HttpRequest,
      futureResponse: Future[(List[jm.HttpHeader], jstream.Source[ProtobufAny, NotUsed])]): Future[jm.HttpResponse] = {
    def extractContentTypeFromHttpBody(entityMessage: MessageOrBuilder): ContentType =
      entityMessage.getField(entityMessage.getDescriptorForType.findFieldByName("content_type")) match {
        case null | "" => ContentTypes.NoContentType
        case string: String =>
          ContentType
            .parse(string)
            .fold(
              list =>
                throw new IllegalResponseException(
                  list.headOption.getOrElse(ErrorInfo.fromCompoundString("Unknown error"))),
              identity)
      }

    def extractDataFromHttpBody(entityMessage: MessageOrBuilder): ByteString =
      ByteString.fromArrayUnsafe(
        entityMessage
          .getField(entityMessage.getDescriptorForType.findFieldByName("data"))
          .asInstanceOf[ProtobufByteString]
          .toByteArray)

    val defaultResponse =
      jm.HttpResponse.create().withStatus(jm.StatusCodes.OK).withProtocol(HttpProtocols.`HTTP/1.1`)

    if (methDesc.isServerStreaming) {
      val header: java.util.Optional[Boolean] = grpcRequest
        .getHeader(classOf[jm.headers.Accept])
        .map(header =>
          // TODO use MediaRanges.match
          header.getMediaRanges.asScala.exists(_.mainType().startsWith(MediaTypes.`text/event-stream`.toString)))

      val sseAccepted: Boolean = header.orElse(false)

      futureResponse.flatMap {
        case (headers, data) =>
          if (sseAccepted) {
            import EventStreamMarshalling._
            Marshal(data.asScala.map(parseResponseBody).map { em =>
              ServerSentEvent(jsonPrinter.print(em))
            }).to[HttpResponse].map(response => response.withHeaders(headers.asJava))
          } else if (isHttpBodyResponse) {
            val entity: jm.ResponseEntity = HttpEntity.Chunked(
              headers
                .find(_.lowercaseName() == "content-type")
                .flatMap(ct => ContentType.parse(ct.value()).toOption)
                .getOrElse(ContentTypes.`application/octet-stream`),
              data.asScala.map(em => HttpEntity.Chunk(extractDataFromHttpBody(parseResponseBody(em)))))
            Future.successful(
              defaultResponse
                .withEntity(entity)
                .withHeaders(headers.filterNot(_.lowercaseName() == "content-type").asJava))
          } else {
            val entity: jm.ResponseEntity = HttpEntity.Chunked(
              ContentTypes.`application/json`,
              data.asScala
                .map(parseResponseBody)
                .map(em => HttpEntity.Chunk(ByteString(jsonPrinter.print(em)) ++ NEWLINE_BYTES)))
            Future.successful(defaultResponse.withEntity(entity).withHeaders(headers.asJava))
          }
      }

    } else {
      for {
        (headers, data) <- futureResponse
        protobuf <- data.asScala.runWith(Sink.head)
      } yield {
        val entityMessage = parseResponseBody(protobuf)
        val entity: jm.ResponseEntity = if (isHttpBodyResponse) {
          HttpEntity(extractContentTypeFromHttpBody(entityMessage), extractDataFromHttpBody(entityMessage))
        } else {
          HttpEntity(ContentTypes.`application/json`, ByteString(jsonPrinter.print(entityMessage)))
        }
        defaultResponse.withEntity(entity).withHeaders(headers.asJava)
      }
    }
  }

  private final val AnyTypeUrlHostName = "type.googleapis.com/"

  private val expectedReplyTypeUrl: String = AnyTypeUrlHostName + methDesc.getOutputType.getFullName

  private[this] def processRequest(req: jm.HttpRequest, matcher: Matcher): Future[jm.HttpResponse] = {
    transformRequest(req, matcher).toScala
      .transformWith {
        case Success(request) =>
          val response = grpcHandler.apply(request).toScala.map { resp =>
            val headers = resp.getHeaders
            val grpcReader = GrpcProtocolNative.newReader(Codecs.detect(resp).get)
            val body =
              resp.entity.getDataBytes.viaMat(grpcReader.dataFrameDecoder, akka.stream.javadsl.Keep.none).map {
                payload =>
                  ProtobufAny(typeUrl = expectedReplyTypeUrl, value = ProtobufByteString.copyFrom(payload.asByteBuffer))
              }
            headers.asScala.toList -> body
          }

          transformResponse(request, response)
        case Failure(_) =>
          requestError("Malformed request")
      }
      .recover {
        case ire: IllegalRequestException => HttpResponse(ire.status.intValue, entity = ire.status.reason)
        case NonFatal(error)              => HttpResponse(StatusCodes.InternalServerError, entity = error.getMessage)
      }
  }

  override def apply(req: jm.HttpRequest): CompletionStage[jm.HttpResponse] = {
    if ((methodPattern == ANY_METHOD || req.method == methodPattern) && matches(req.getUri.asScala().path)) {
      val matcher = pathTemplate.regex.pattern.matcher(req.getUri.path())
      assert(matcher.matches())
      processRequest(req, matcher).toJava
    } else {
      ServiceHandler.notFound
    }

  }
}
