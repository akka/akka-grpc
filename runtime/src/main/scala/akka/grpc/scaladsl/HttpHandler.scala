package akka.grpc.scaladsl

import akka.NotUsed
import akka.grpc.GrpcProtocol
import akka.grpc.internal.HttpTranscoding._
import akka.grpc.internal.{ Codecs, GrpcProtocolNative }
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling
import akka.http.scaladsl.model.HttpMessage._
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.stream.Materializer
import akka.stream.scaladsl.{ Keep, Sink, Source }
import akka.util.ByteString
import com.google.api.HttpRule
import com.google.protobuf.Descriptors._
import com.google.protobuf.any.{ Any => ProtobufAny }
import com.google.protobuf.{ DynamicMessage, MessageOrBuilder, ByteString => ProtobufByteString }

import java.util.regex.Matcher
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.control.NonFatal
import scala.util.{ Failure, Success }
final class HttpHandler(
    val methDesc: MethodDescriptor,
    val rule: HttpRule,
    grpcHandler: PartialFunction[HttpRequest, Future[HttpResponse]])(implicit ec: ExecutionContext, mat: Materializer)
    extends akka.grpc.HttpHandler
    with PartialFunction[HttpRequest, Future[HttpResponse]] {

  import akka.grpc.HttpHandler._
  private[this] def updateRequest(req: HttpRequest, message: DynamicMessage): HttpRequest = {
    HttpRequest(
      method = HttpMethods.POST,
      uri = Uri(path = Path / methDesc.getService.getFullName / methDesc.getName),
      headers = req.headers :+ IdentityHeader,
      entity = HttpEntity.Chunked(
        ContentTypes.`application/grpc+proto`,
        Source.single(grpcWriter.encodeFrame(GrpcProtocol.DataFrame(ByteString.fromArrayUnsafe(message.toByteArray))))),
      protocol = HttpProtocols.`HTTP/2.0`)
  }

  private def transformRequest(req: HttpRequest, matcher: Matcher): Future[HttpRequest] =
    if (rule.getBody.nonEmpty && req.entity.contentType != ContentTypes.`application/json`) {
      Future.failed(IllegalRequestException(StatusCodes.BadRequest, "Content-type must be application/json!"))
    } else {
      val inputBuilder = DynamicMessage.newBuilder(methDesc.getInputType)
      rule.getBody match {
        case "" => // Iff empty body rule, then only query parameters
          req.discardEntityBytes()
          parseRequestParametersInto(methDesc, req.uri.query().toMultiMap, inputBuilder)
          parsePathParametersInto(matcher, inputBuilder)
          Future.successful(updateRequest(req, inputBuilder.build))
        case "*" => // Iff * body rule, then no query parameters, and only fields not mapped in path variables
          Unmarshal(req.entity)
            .to[String]
            .map(str => {
              jsonParser.merge(str, inputBuilder)
              parsePathParametersInto(matcher, inputBuilder)
              updateRequest(req, inputBuilder.build)
            })
        case fieldName => // Iff fieldName body rule, then all parameters not mapped in path variables
          Unmarshal(req.entity)
            .to[String]
            .map(str => {
              val subField = lookupFieldByName(methDesc.getInputType, fieldName)
              val subInputBuilder = DynamicMessage.newBuilder(subField.getMessageType)
              jsonParser.merge(str, subInputBuilder)
              parseRequestParametersInto(methDesc, req.uri.query().toMultiMap, inputBuilder)
              parsePathParametersInto(matcher, inputBuilder)
              inputBuilder.setField(subField, subInputBuilder.build())
              updateRequest(req, inputBuilder.build)
            })
      }
    }
  private def transformResponse(
      grpcRequest: HttpRequest,
      futureResponse: Future[(List[HttpHeader], Source[ProtobufAny, NotUsed])]): Future[HttpResponse] = {
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

    if (methDesc.isServerStreaming) {
      val sseAccepted =
        grpcRequest
          .header[Accept]
          .exists(_.mediaRanges.exists(_.value.startsWith(MediaTypes.`text/event-stream`.toString)))

      futureResponse.flatMap {
        case (headers, data) =>
          if (sseAccepted) {
            import EventStreamMarshalling._
            Marshal(data.map(parseResponseBody).map { em =>
              ServerSentEvent(jsonPrinter.print(em))
            }).to[HttpResponse].map(response => response.withHeaders(headers))
          } else if (isHttpBodyResponse) {
            Future.successful(
              HttpResponse(
                entity = HttpEntity.Chunked(
                  headers
                    .find(_.lowercaseName() == "content-type")
                    .flatMap(ct => ContentType.parse(ct.value()).toOption)
                    .getOrElse(ContentTypes.`application/octet-stream`),
                  data.map(em => HttpEntity.Chunk(extractDataFromHttpBody(parseResponseBody(em))))),
                headers = headers.filterNot(_.lowercaseName() == "content-type")))
          } else {
            Future.successful(
              HttpResponse(
                entity = HttpEntity.Chunked(
                  ContentTypes.`application/json`,
                  data
                    .map(parseResponseBody)
                    .map(em => HttpEntity.Chunk(ByteString(jsonPrinter.print(em)) ++ NEWLINE_BYTES))),
                headers = headers))
          }
      }

    } else {
      for {
        (headers, data) <- futureResponse
        protobuf <- data.runWith(Sink.head)
      } yield {
        val entityMessage = parseResponseBody(protobuf)
        HttpResponse(
          entity = if (isHttpBodyResponse) {
            HttpEntity(extractContentTypeFromHttpBody(entityMessage), extractDataFromHttpBody(entityMessage))
          } else {
            HttpEntity(ContentTypes.`application/json`, ByteString(jsonPrinter.print(entityMessage)))
          },
          headers = headers)
      }
    }
  }

  private final val AnyTypeUrlHostName = "type.googleapis.com/"

  private val expectedReplyTypeUrl: String = AnyTypeUrlHostName + methDesc.getOutputType.getFullName

  private[this] def processRequest(req: HttpRequest, matcher: Matcher): Future[HttpResponse] = {
    transformRequest(req, matcher)
      .transformWith {
        case Success(request) =>
          val response = grpcHandler
            .applyOrElse(
              request,
              // actually, this should be impossible since only way to miss match transformed request with real gRPC server schema
              // is either wrong transformation or wrong creation(mismatched implementation and gRPC handler)
              (_: HttpRequest) =>
                Future.failed(
                  IllegalRequestException(StatusCodes.NotFound, s"Requested resource ${req.uri} not found!")))
            .map { resp =>
              val headers = resp.headers
              val grpcReader = GrpcProtocolNative.newReader(Codecs.detect(resp).get)
              val body = resp.entity.dataBytes.viaMat(grpcReader.dataFrameDecoder)(Keep.none).map { payload =>
                ProtobufAny(typeUrl = expectedReplyTypeUrl, value = ProtobufByteString.copyFrom(payload.asByteBuffer))
              }
              headers.toList -> body
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

  override def isDefinedAt(req: HttpRequest): Boolean = {
    (methodPattern == ANY_METHOD || req.method == methodPattern) && matches(req.uri.path)
  }

  override def apply(req: HttpRequest): Future[HttpResponse] = {
    assert(methodPattern == ANY_METHOD || req.method == methodPattern)
    val matcher = pathTemplate.regex.pattern.matcher(req.uri.path.toString())
    assert(matcher.matches())
    processRequest(req, matcher)
  }
}
