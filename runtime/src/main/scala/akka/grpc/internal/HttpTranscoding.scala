/*
 * Copyright (C) 2020-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import akka.ConfigurationException
import akka.annotation.InternalApi
import akka.grpc.internal.HttpTranscoding.PathTemplateParser.TemplateVariable
import akka.grpc.scaladsl.headers.`Message-Accept-Encoding`
import akka.grpc.{ GrpcProtocol, ProtobufSerializer }
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling
import akka.http.scaladsl.model.HttpMessage._
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.server.PathMatcher._
import akka.http.scaladsl.server.{ PathMatcher, PathMatcher1 }
import akka.http.scaladsl.server.PathMatchers
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.parboiled2._
import akka.parboiled2.util.Base64
import akka.stream.Materializer
import akka.stream.scaladsl.{ Sink, Source }
import akka.util.ByteString
import com.google.api.annotations.AnnotationsProto
import com.google.api.http.HttpRule
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType
import com.google.protobuf.Descriptors.{
  Descriptor,
  EnumValueDescriptor,
  FieldDescriptor,
  MethodDescriptor,
  FileDescriptor => JavaFileDescriptor
}
import com.google.protobuf.any.{ Any => ProtobufAny }
import com.google.protobuf.util.JsonFormat
import com.google.protobuf.{
  DynamicMessage,
  ListValue,
  MessageOrBuilder,
  Struct,
  Value,
  ByteString => ProtobufByteString
}
import io.grpc.{ Status, StatusRuntimeException }
import scalapb.descriptors.{ FileDescriptor => PBFileDescriptor, MethodDescriptor => PBMethodDescriptor }

import java.lang.{
  Boolean => JBoolean,
  Double => JDouble,
  Float => JFloat,
  Integer => JInteger,
  Long => JLong,
  Short => JShort
}
import java.net.URLDecoder
import scala.annotation.tailrec
import scala.collection.JavaConverters._
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.control.NonFatal
import scala.util.{ Failure, Success }

@InternalApi
private[grpc] object HttpTranscoding {

  final val ParseShort: String => Option[JShort] =
    s =>
      try Option(JShort.valueOf(s))
      catch {
        case _: NumberFormatException => None
      }

  private final val ParseInt: String => Option[JInteger] =
    s =>
      try Option(JInteger.valueOf(s))
      catch {
        case _: NumberFormatException => None
      }

  private final val ParseLong: String => Option[JLong] =
    s =>
      try Option(JLong.valueOf(s))
      catch {
        case _: NumberFormatException => None
      }

  private final val ParseFloat: String => Option[JFloat] =
    s =>
      try Option(JFloat.valueOf(s))
      catch {
        case _: NumberFormatException => None
      }

  private final val ParseDouble: String => Option[JDouble] =
    s =>
      try Option(JDouble.valueOf(s))
      catch {
        case _: NumberFormatException => None
      }

  private final val ParseString: String => Option[String] =
    s => Option(s)

  private[this] final val someJTrue = Some(JBoolean.TRUE)
  private[this] final val someJFalse = Some(JBoolean.FALSE)

  private final val ParseBoolean: String => Option[JBoolean] =
    _.toLowerCase match {
      case "true"  => someJTrue
      case "false" => someJFalse
      case _       => None
    }

  // Reads a rfc2045 encoded Base64 string
  private final val ParseBytes: String => Option[ProtobufByteString] =
    s => Some(ProtobufByteString.copyFrom(Base64.rfc2045().decode(s))) // Make cheaper? Protobuf has a Base64 decoder?

  private final def suitableParserFor(field: FieldDescriptor)(whenIllegal: String => Nothing): String => Option[Any] =
    field.getJavaType match {
      case JavaType.BOOLEAN     => ParseBoolean
      case JavaType.BYTE_STRING => ParseBytes
      case JavaType.DOUBLE      => ParseDouble
      case JavaType.ENUM        => whenIllegal("Enum path parameters not supported!")
      case JavaType.FLOAT       => ParseFloat
      case JavaType.INT         => ParseInt
      case JavaType.LONG        => ParseLong
      case JavaType.MESSAGE     => whenIllegal("Message path parameters not supported!")
      case JavaType.STRING      => ParseString
    }

  // We use this to indicate problems with the configuration of the routes
  private final val configError: String => Nothing = s => throw new ConfigurationException("HTTP API Config: " + s)

  // We use this to signal to the requestor that there's something wrong with the request
  private final val requestError: String => Nothing = s => throw IllegalRequestException(StatusCodes.BadRequest, s)

  // This is used to support the "*" custom pattern
  private final val ANY_METHOD = HttpMethod.custom(
    name = "ANY",
    safe = false,
    idempotent = false,
    requestEntityAcceptance = RequestEntityAcceptance.Tolerated,
    contentLengthAllowed = true)

  private final val NEWLINE_BYTES = ByteString('\n')

  def parseRules(
      javaFileDescriptor: JavaFileDescriptor,
      pbFileDescriptor: PBFileDescriptor): Seq[(MethodDescriptor, HttpRule)] = {
    for {
      (jService, sService) <- javaFileDescriptor.getServices.asScala.toVector.zip(pbFileDescriptor.services)
      (jMethod, sMethod) <- jService.getMethods.asScala.toVector.zip(sService.methods)
      rules = getRules(sMethod)
      binding <- rules
    } yield {
      jMethod -> binding
    }
  }

  def httpHandler(
      methDesc: MethodDescriptor,
      rule: HttpRule,
      grpcHandler: PartialFunction[HttpRequest, Future[HttpResponse]])(
      implicit ec: ExecutionContext,
      mat: Materializer): PartialFunction[HttpRequest, Future[HttpResponse]] = {

    import HttpHandler._

    val (methodPattern, pathTemplate, pathExtractor, bodyDescriptor, responseBodyDescriptor) =
      extractAndValidate(rule, methDesc)

    // Making this a method so we can ensure it's used the same way
    def matches(path: Uri.Path): Boolean = {
      pathTemplate.matcher(path) match {
        case PathMatcher.Matched(Path.Empty, _) => true
        case _                                  => false
      }
    }

    def parsePathParametersInto(capturedVariables: Map[String, String], inputBuilder: DynamicMessage.Builder): Unit =
      pathExtractor(
        capturedVariables,
        (field, value) => {
          inputBuilder.setField(field, value.getOrElse(requestError("Path contains value of wrong type!")))
        })

    def parseResponseBody(pbAny: ProtobufAny): MessageOrBuilder = {
      val bytes = ReplySerializer.serialize(pbAny)
      val message = DynamicMessage.parseFrom(methDesc.getOutputType, bytes.iterator.asInputStream)
      responseBodyDescriptor.fold(message: MessageOrBuilder) { field =>
        message.getField(field) match {
          case m: MessageOrBuilder if !field.isRepeated => m // No need to wrap this
          case value                                    => responseBody(field.getJavaType, value, field.isRepeated)
        }
      }
    }

    val jsonParser: JsonFormat.Parser =
      JsonFormat.parser.usingTypeRegistry(JsonFormat.TypeRegistry.newBuilder().add(bodyDescriptor).build)

    // TODO it's annoying that protobuf JSON printer format every Long to String in JSON, find a way to deal with it
    // see https://stackoverflow.com/questions/53080136/protobuf-jsonformater-printer-convert-long-to-string-in-json
    val jsonPrinter = JsonFormat.printer
      .usingTypeRegistry(JsonFormat.TypeRegistry.newBuilder.add(methDesc.getOutputType).build())
      .includingDefaultValueFields()
      .omittingInsignificantWhitespace()

    val IdentityHeader = new `Message-Accept-Encoding`("identity")
    val grpcWriter = GrpcProtocolNative.newWriter(Identity)
    val isHttpBodyResponse = methDesc.getOutputType.getFullName == "google.api.HttpBody"

    def updateRequest(req: HttpRequest, message: DynamicMessage): HttpRequest = {
      HttpRequest(
        method = HttpMethods.POST,
        uri = Uri(path = Path / methDesc.getService.getFullName / methDesc.getName),
        headers = req.headers :+ IdentityHeader,
        entity = HttpEntity.Chunked(
          ContentTypes.`application/grpc+proto`,
          Source.single(
            grpcWriter.encodeFrame(GrpcProtocol.DataFrame(ByteString.fromArrayUnsafe(message.toByteArray))))),
        protocol = HttpProtocols.`HTTP/2.0`)
    }

    def transformRequest(req: HttpRequest, capturedVariables: Map[String, String]): Future[HttpRequest] =
      if (rule.body.nonEmpty && req.entity.contentType != ContentTypes.`application/json`) {
        Future.failed(IllegalRequestException(StatusCodes.BadRequest, "Content-type must be application/json!"))
      } else {
        val inputBuilder = DynamicMessage.newBuilder(methDesc.getInputType)
        rule.body match {
          case "" => // Iff empty body rule, then only query parameters
            req.discardEntityBytes()
            parseRequestParametersInto(methDesc, req.uri.query().toMultiMap, inputBuilder)
            parsePathParametersInto(capturedVariables, inputBuilder)
            Future.successful(updateRequest(req, inputBuilder.build))
          case "*" => // Iff * body rule, then no query parameters, and only fields not mapped in path variables
            Unmarshal(req.entity)
              .to[String]
              .map(str => {
                jsonParser.merge(str, inputBuilder)
                parsePathParametersInto(capturedVariables, inputBuilder)
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
                parsePathParametersInto(capturedVariables, inputBuilder)
                inputBuilder.setField(subField, subInputBuilder.build())
                updateRequest(req, inputBuilder.build)
              })
        }
      }

    def transformResponse(
        grpcRequest: HttpRequest,
        grpcResponseFuture: Future[HttpResponse],
        deserializer: ProtobufSerializer[ProtobufAny]): Future[HttpResponse] = {
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

      def toHttpResponse(grpcResponse: HttpResponse): Future[HttpResponse] = {
        val headers = grpcResponse.headers.toList
        val responseSource =
          AkkaHttpClientUtils.responseToSource(
            grpcRequest.uri,
            grpcResponseFuture,
            deserializer,
            methDesc.isServerStreaming)
        if (methDesc.isServerStreaming) {
          val sseAccepted =
            grpcRequest
              .header[Accept]
              .exists(_.mediaRanges.exists(_.value.startsWith(MediaTypes.`text/event-stream`.toString)))

          if (sseAccepted) {
            import EventStreamMarshalling._
            val sseSource = responseSource.map(parseResponseBody).map(em => ServerSentEvent(jsonPrinter.print(em)))
            Marshal(sseSource).to[HttpResponse].map(_.withHeaders(headers))
          } else if (isHttpBodyResponse) {
            val contentTypeHeader = headers
              .find(_.lowercaseName() == "content-type")
              .flatMap(ct => ContentType.parse(ct.value()).toOption)
              .getOrElse(ContentTypes.`application/octet-stream`)
            Future.successful(
              HttpResponse(
                entity = HttpEntity.Chunked(
                  contentTypeHeader,
                  responseSource.map(em => HttpEntity.Chunk(extractDataFromHttpBody(parseResponseBody(em))))),
                headers = headers.filterNot(_.lowercaseName() == "content-type")))
          } else {
            Future.successful(
              HttpResponse(
                entity = HttpEntity.Chunked(
                  ContentTypes.`application/json`,
                  responseSource
                    .map(parseResponseBody)
                    .map(em => HttpEntity.Chunk(ByteString(jsonPrinter.print(em)) ++ NEWLINE_BYTES))),
                headers = headers))
          }
        } else {
          responseSource.runWith(Sink.head).map { protobuf =>
            val bytes = ReplySerializer.serialize(protobuf)
            val message = DynamicMessage.parseFrom(methDesc.getOutputType, bytes.iterator.asInputStream)
            jsonPrinter.print(message)
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

      grpcResponseFuture.flatMap(toHttpResponse)
    }

    val AnyTypeUrlHostName = "type.googleapis.com/"

    val expectedReplyTypeUrl: String = AnyTypeUrlHostName + methDesc.getOutputType.getFullName

    val protobufAnyDeserializer: ProtobufSerializer[ProtobufAny] = new ProtobufSerializer[ProtobufAny] {
      def serialize(t: ProtobufAny): ByteString = throw new UnsupportedOperationException("operation not supported")

      def deserialize(bytes: ByteString): ProtobufAny =
        ProtobufAny(typeUrl = expectedReplyTypeUrl, value = ProtobufByteString.copyFrom(bytes.asByteBuffer))
    }

    def processRequest(req: HttpRequest, capturedVariables: Map[String, String]): Future[HttpResponse] = {
      transformRequest(req, capturedVariables)
        .transformWith {
          case Success(request) =>
            val response = grpcHandler.applyOrElse(
              request,
              // actually, this should be impossible since only way to miss match transformed request with real gRPC server schema
              // is either wrongly transform HTTP request to gRPC request
              // or wrapping other service's gRPC handler
              (_: HttpRequest) =>
                Future.failed(
                  IllegalRequestException(StatusCodes.NotFound, s"Requested resource ${req.uri} not found!")))

            transformResponse(request, response, protobufAnyDeserializer)
          case Failure(_) =>
            requestError("Malformed request")
        }
        .recover {
          case ire: IllegalRequestException => HttpResponse(ire.status.intValue, entity = ire.status.reason)
          case sre: StatusRuntimeException  => mapToHttpResponse(sre)
          case NonFatal(err)                => HttpResponse(StatusCodes.InternalServerError, entity = err.getMessage)
        }
    }

    new PartialFunction[HttpRequest, Future[HttpResponse]] {
      def isDefinedAt(req: HttpRequest): Boolean =
        (methodPattern == ANY_METHOD || req.method == methodPattern) && matches(req.uri.path)

      def apply(req: HttpRequest): Future[HttpResponse] = {
        assert(methodPattern == ANY_METHOD || req.method == methodPattern)
        val capturedVariables = pathTemplate.matcher(req.uri.path) match {
          case Matched(_, (variables, _)) =>
            variables.map {
              case (fieldPath, segments) => fieldPath.fields.mkString(".") -> segments
            }.toMap

          // impossible
          case Unmatched => Map.empty[String, String]
        }
        processRequest(req, capturedVariables)
      }
    }

  }

  /**
   * Since there is no standard for mapping gRPC status to HTTP status
   * So we consider https://github.com/grpc/grpc/blob/master/doc/http-grpc-status-mapping.md
   * And https://cloud.google.com/apis/design/standard_methods
   * Also provide some extra mappings for convenience
   */
  private def mapToHttpResponse(sre: StatusRuntimeException): HttpResponse = {
    val httpStatus = sre.getStatus.getCode match {
      case Status.Code.INTERNAL          => StatusCodes.BadRequest
      case Status.Code.INVALID_ARGUMENT  => StatusCodes.BadRequest
      case Status.Code.UNAUTHENTICATED   => StatusCodes.Unauthorized
      case Status.Code.PERMISSION_DENIED => StatusCodes.Forbidden
      case Status.Code.UNIMPLEMENTED     => StatusCodes.NotFound
      case Status.Code.NOT_FOUND         => StatusCodes.NotFound
      case Status.Code.ALREADY_EXISTS    => StatusCodes.Conflict
      case Status.Code.UNAVAILABLE       => StatusCodes.ServiceUnavailable
      case Status.Code.UNKNOWN           => StatusCodes.InternalServerError
      case _                             => StatusCodes.InternalServerError
    }
    HttpResponse(httpStatus, entity = sre.getStatus.getDescription)
  }

  private def getRules(methDesc: PBMethodDescriptor): Seq[HttpRule] = {
    AnnotationsProto.http.get(methDesc.getOptions) match {
      case Some(rule) =>
        rule +: rule.additionalBindings
      case None =>
        Seq.empty
    }
  }

  private object HttpHandler {
    // For descriptive purposes so it's clear what these types do
    private type PathParameterEffect = (FieldDescriptor, Option[Any]) => Unit
    private type ExtractPathParameters = (Map[String, String], PathParameterEffect) => Unit

    // Question: Do we need to handle conversion from JSON names?
    def lookupFieldByName(desc: Descriptor, selector: String): FieldDescriptor =
      desc.findFieldByName(
        selector
      ) // TODO potentially start supporting path-like selectors with maximum nesting level?

    def scalarValueParser(fieldDescriptor: FieldDescriptor): String => Option[Any] = {
      val valueParser = suitableParserFor(fieldDescriptor)(configError)

      valueParser.compose[String](value => {
        URLDecoder.decode(value, "utf-8")
      })

    }

    // for descriptive purpose
    type FieldName = String
    type ScalarValueParser = Option[String => Option[Any]]
    type SelectorField = (FieldDescriptor, ScalarValueParser, FieldName)
    type Selector = Seq[SelectorField]

    //   1     2      3      4   stages
    // <------------------------ building direction
    // book.author.location.address
    // book.author.name
    // book.title
    //
    // at each stage we first lookup if it's any exist(by selector prefix) message builder in the next stage
    // if it exists, we added whatever we have now(message or scalar field) into it
    // and if it isn't, we first construct a new message builder from it's parent selector then added itself into it and update next stage
    def complexSelectorBuilder(
        methodDesc: MethodDescriptor,
        rootDesc: Descriptor,
        selectors: Seq[TemplateVariable]): (Map[String, String], PathParameterEffect) => Unit = {
      import scala.collection.mutable.{ Map => MutableMap }

      def getScalarValue(fullSelector: Selector, variables: Map[String, String]): (FieldDescriptor, Any) = {
        val scalarDescriptor = fullSelector.last._1
        val fieldPath = fullSelector.map(_._3)
        val scalarValue = variables.getOrElse(
          fieldPath.mkString("."),
          requestError(s"Value of field ${fullSelector.last._3} not found in method ${methodDesc.getFullName}"))
        val valueParser = fullSelector.last._2.get
        val parsedValue = valueParser(scalarValue).getOrElse(
          requestError(s"Invalid value for field ${fullSelector.last._3} in method ${methodDesc.getFullName}"))
        scalarDescriptor -> parsedValue
      }

      val startStage = selectors.map(_.fieldPath.length).max

      val fullSelectors: Map[Int, Seq[(Selector, Option[DynamicMessage.Builder])]] = selectors
        .map {
          case TemplateVariable(fieldPath, _) => {
            var previousDesc = rootDesc
            var previousFieldDesc: FieldDescriptor = null
            fieldPath.zipWithIndex.map {
              case (field, i) => {
                val fieldDescriptor = previousDesc.findFieldByName(field)
                if (fieldDescriptor == null) configError(s"Unknown field ${field} in method ${methodDesc.getFullName}")
                previousFieldDesc = fieldDescriptor
                // every last field has to be scalar field
                val valueParser =
                  if (i == fieldPath.length - 1) Option(suitableParserFor(fieldDescriptor)(configError))
                  else {
                    previousDesc = fieldDescriptor.getMessageType
                    Option.empty
                  }
                (fieldDescriptor, valueParser, field)
              }
            } -> Option.empty[DynamicMessage.Builder]
          }
        }
        .groupBy(_._1.length)

      (variables, updater) => {
        val stages =
          MutableMap(fullSelectors.map { case (k, vs) => k -> MutableMap(vs: _*) }.toList: _*)
            .withDefaultValue(MutableMap.empty)

        def buildStage(stage: Int): Unit = {
          val currentStage = stages(stage)
          val nextStage = stages(stage - 1)
          currentStage.foreach {
            case (fullSelector, builderOpt) => {
              val fieldPath = fullSelector.map(_._3)
              val existBuilder = nextStage.find(stg => fieldPath.startsWith(stg._1.map(_._3)))
              existBuilder match {
                // there is already a builder
                case Some((_, Some(parentBuilder))) => {
                  builderOpt match {
                    case Some(builder) => {
                      // at current stage we have a message type
                      // note: this means we've collect all of its fields
                      // since the fact one's field cannot at the same stage with itself
                      val descriptor = fullSelector.last._1
                      val message = builder.build()
                      parentBuilder.setField(descriptor, message)
                      currentStage.remove(fullSelector)
                    }
                    case None => {
                      // at current stage we have a scalar type
                      val (scalarDescriptor, parsedValue) = getScalarValue(fullSelector, variables)
                      parentBuilder.setField(scalarDescriptor, parsedValue)
                      currentStage.remove(fullSelector)
                    }
                  }
                }
                case Some((_, None)) =>
                  // impossible
                  requestError(s"Invalid selector ${fieldPath.mkString(".")}, reference directly to message type field")
                // there isn't, so we have to build one and update stage
                case None =>
                  builderOpt match {
                    case Some(builder) => {
                      // at current stage we have a message type
                      val parent = fullSelector.dropRight(1)
                      val parentDescriptor = parent.last._1.getMessageType
                      val parentBuilder = DynamicMessage.newBuilder(parentDescriptor)
                      val message = builder.build()
                      val descriptor = fullSelector.last._1
                      parentBuilder.setField(descriptor, message)
                      currentStage.remove(fullSelector)
                      nextStage.update(parent, Some(parentBuilder))
                    }
                    case None => {
                      // at current stage we have a scalar type
                      val parent = fullSelector.dropRight(1)
                      val parentDescriptor = parent.last._1.getMessageType
                      val parentBuilder = DynamicMessage.newBuilder(parentDescriptor)
                      val (scalarDescriptor, parsedValue) = getScalarValue(fullSelector, variables)
                      parentBuilder.setField(scalarDescriptor, parsedValue)
                      currentStage.remove(fullSelector)
                      nextStage.update(parent, Some(parentBuilder))
                    }
                  }
              }
            }
          }
        }

        (2 to startStage).reverse.foreach(buildStage)

        stages(1).toList.collect { case (selector, Some(builder)) => selector -> builder.build() }.foreach {
          case (selector, message) => {
            updater(selector.last._1, Some(message))
          }
        }
      }

    }

    def scalarSelectorBuilder(methDesc: MethodDescriptor, desc: Descriptor, selectors: Seq[TemplateVariable]) = {
      val parsers = selectors.map {
        case TemplateVariable(selector, multi) => {
          val fieldName = selector.head
          lookupFieldByName(desc, fieldName) match {
            case null =>
              configError(
                s"Unknown field name [$fieldName] in type [${desc.getFullName}] reference in path template for method [${methDesc.getFullName}]")
            case field =>
              if (field.isRepeated)
                configError(s"Repeated parameters [${field.getFullName}] are not allowed as path variables")
              else if (field.isMapField)
                configError(s"Map parameters [${field.getFullName}] are not allowed as path variables")
              else {
                val valueParser = scalarValueParser(field)
                (field, fieldName, valueParser)
              }
          }
        }
      }

      (variables: Map[String, String], updater: PathParameterEffect) => {
        parsers.foreach {
          case (field, fieldName, valueParser) => {
            val rawValue = variables.getOrElse(fieldName, requestError("todo"))
            updater(field, valueParser(rawValue))
          }
        }
      }

    }

    val emptyBuilder: ExtractPathParameters = (_, _) => {}
    def parsePathExtractor(
        methDesc: MethodDescriptor,
        pattern: String): (PathTemplateParser.ParsedTemplate, ExtractPathParameters) = {
      val template = PathTemplateParser.parseToTemplate(pattern)
      val requestMessageDesc = methDesc.getInputType
      val (scalarSelectors, complexSelectors) = template.fields.partition(_.fieldPath.length == 1)

      val complexMessageBuilder =
        if (complexSelectors.nonEmpty) complexSelectorBuilder(methDesc, requestMessageDesc, complexSelectors)
        else emptyBuilder
      val scalarBuilder = scalarSelectorBuilder(methDesc, requestMessageDesc, scalarSelectors)

      val extractor: ExtractPathParameters = (variables, updater) => {
        complexMessageBuilder(variables, updater)
        scalarBuilder(variables, updater)
      }

      (template, extractor)
    }

    // This method validates the configuration and returns values obtained by parsing the configuration
    def extractAndValidate(rule: HttpRule, methDesc: MethodDescriptor): (
        HttpMethod,
        PathTemplateParser.ParsedTemplate,
        ExtractPathParameters,
        Descriptor,
        Option[FieldDescriptor]) = {
      // Validate selector
      if (rule.selector != "" && rule.selector != methDesc.getFullName)
        configError(s"Rule selector [${rule.selector}] must be empty or [${methDesc.getFullName}]")

      // Validate pattern
      val (mp, pattern) = {
        import HttpRule.Pattern._
        import akka.http.scaladsl.model.HttpMethods._

        rule.pattern match {
          case Empty           => configError(s"Pattern missing for rule [$rule]!") // TODO improve error message
          case Get(pattern)    => (GET, pattern)
          case Put(pattern)    => (PUT, pattern)
          case Post(pattern)   => (POST, pattern)
          case Delete(pattern) => (DELETE, pattern)
          case Patch(pattern)  => (PATCH, pattern)
          case Custom(chp) =>
            if (chp.kind == "*")
              (
                ANY_METHOD,
                chp.path
              ) // FIXME is "path" the same as "pattern" for the other kinds? Is an empty kind valid?
            else configError(s"Only Custom patterns with [*] kind supported but [${chp.kind}] found!")
        }
      }
      val (template, extractor) = parsePathExtractor(methDesc, pattern)

      // Validate body value
      val bd =
        rule.body match {
          case "" => methDesc.getInputType
          case "*" =>
            if (!mp.isEntityAccepted)
              configError(s"Body configured to [*] but HTTP Method [$mp] does not have a request body.")
            else
              methDesc.getInputType
          case fieldName =>
            val field = lookupFieldByName(methDesc.getInputType, fieldName)
            if (field == null)
              configError(s"Body configured to [$fieldName] but that field does not exist on input type.")
            else if (field.isRepeated)
              configError(s"Body configured to [$fieldName] but that field is a repeated field.")
            else if (!mp.isEntityAccepted)
              configError(s"Body configured to [$fieldName] but HTTP Method $mp does not have a request body.")
            else
              field.getMessageType
        }

      // Validate response body value
      val rd =
        rule.responseBody match {
          case "" => None
          case fieldName =>
            lookupFieldByName(methDesc.getOutputType, fieldName) match {
              case null =>
                configError(
                  s"Response body field [$fieldName] does not exist on type [${methDesc.getOutputType.getFullName}]")
              case field => Some(field)
            }
        }

      if (rule.additionalBindings.exists(_.additionalBindings.nonEmpty))
        configError(s"Only one level of additionalBindings supported, but [$rule] has more than one!")

      (mp, template, extractor, bd, rd)
    }

    @tailrec def lookupFieldByPath(desc: Descriptor, selector: String): FieldDescriptor =
      Names.splitNext(selector) match {
        case ("", "")        => null
        case (fieldName, "") => lookupFieldByName(desc, fieldName)
        case (fieldName, next) =>
          val field = lookupFieldByName(desc, fieldName)
          if (field == null) null
          else if (field.getMessageType == null) null
          else lookupFieldByPath(field.getMessageType, next)
      }

    def parseRequestParametersInto(
        methDesc: MethodDescriptor,
        query: Map[String, List[String]],
        inputBuilder: DynamicMessage.Builder): Unit =
      query.foreach {
        case (selector, values) =>
          if (values.nonEmpty) {
            lookupFieldByPath(methDesc.getInputType, selector) match {
              case null => requestError("Query parameter [$selector] refers to non-existent field")
              case field if field.getJavaType == FieldDescriptor.JavaType.MESSAGE =>
                requestError(
                  "Query parameter [$selector] refers to a message type"
                ) // FIXME validate assumption that this is prohibited
              case field if !field.isRepeated && values.size > 1 =>
                requestError("Multiple values sent for non-repeated field by query parameter [$selector]")
              case field => // FIXME verify that we can set nested fields from the inputBuilder type
                val x = suitableParserFor(field)(requestError)
                if (field.isRepeated) {
                  values.foreach { v =>
                    inputBuilder.addRepeatedField(
                      field,
                      x(v).getOrElse(requestError("Malformed Query parameter [$selector]")))
                  }
                } else
                  inputBuilder.setField(
                    field,
                    x(values.head).getOrElse(requestError("Malformed Query parameter [$selector]")))
            }
          } // Ignore empty values
      }

    // FIXME Devise other way of supporting responseBody, this is waaay too costly and unproven
    // This method converts an arbitrary type to something which can be represented as JSON.
    def responseBody(jType: JavaType, value: AnyRef, repeated: Boolean): com.google.protobuf.Value = {
      val result =
        if (repeated) {
          Value.newBuilder.setListValue(
            ListValue.newBuilder.addAllValues(
              value
                .asInstanceOf[java.lang.Iterable[AnyRef]]
                .asScala
                .map(v => responseBody(jType, v, repeated = false))
                .asJava))
        } else {
          val b = Value.newBuilder
          jType match {
            case JavaType.BOOLEAN     => b.setBoolValue(value.asInstanceOf[JBoolean])
            case JavaType.BYTE_STRING => b.setStringValueBytes(value.asInstanceOf[ProtobufByteString])
            case JavaType.DOUBLE      => b.setNumberValue(value.asInstanceOf[JDouble])
            case JavaType.ENUM =>
              b.setStringValue(
                value.asInstanceOf[EnumValueDescriptor].getName
              ) // Switch to getNumber if enabling printingEnumsAsInts in the JSON Printer
            case JavaType.FLOAT => b.setNumberValue(value.asInstanceOf[JFloat].toDouble)
            case JavaType.INT   => b.setNumberValue(value.asInstanceOf[JInteger].toDouble)
            case JavaType.LONG  => b.setNumberValue(value.asInstanceOf[JLong].toDouble)
            case JavaType.MESSAGE =>
              val sb = Struct.newBuilder
              value.asInstanceOf[MessageOrBuilder].getAllFields.asScala.foreach {
                case (k, v) =>
                  sb.putFields(
                    k.getJsonName,
                    responseBody(k.getJavaType, v, k.isRepeated)
                  ) //Switch to getName if enabling preservingProtoFieldNames in the JSON Printer
              }
              b.setStructValue(sb)
            case JavaType.STRING => b.setStringValue(value.asInstanceOf[String])
          }
        }
      result.build()
    }

  }

  private object ReplySerializer extends ProtobufSerializer[ProtobufAny] {
    override def serialize(reply: ProtobufAny): ByteString =
      if (reply.value.isEmpty) ByteString.empty
      else ByteString.fromArrayUnsafe(reply.value.toByteArray)

    override def deserialize(bytes: ByteString): ProtobufAny =
      throw new UnsupportedOperationException("operation not supported")
  }

  private object Names {
    final def splitPrev(name: String): (String, String) = {
      val dot = name.lastIndexOf('.')
      if (dot >= 0) {
        (name.substring(0, dot), name.substring(dot + 1))
      } else {
        ("", name)
      }
    }

    final def splitNext(name: String): (String, String) = {
      val dot = name.indexOf('.')
      if (dot >= 0) {
        (name.substring(0, dot), name.substring(dot + 1))
      } else {
        (name, "")
      }
    }
  }

  object PathTemplateParser {
    // AST for syntax described here:
    // https://cloud.google.com/endpoints/docs/grpc-service-config/reference/rpc/google.api#google.api.HttpRule.description.subsection
    final case class Template(segments: Seq[Segment], verb: Option[String]) {
      private def printSegment(segment: Segment): String = segment match {
        case LiteralSegment(literal) => literal
        case VariableSegment(fieldPath, template) =>
          s"{${fieldPath.fields.mkString(".")}${template.map(_.map(printSegment).mkString("/")).map(str => s"=$str").getOrElse("")}}"
        case SingleSegmentMatcher => "*"
        case MultiSegmentMatcher  => "**"
      }

      override lazy val toString =
        s"/${segments.map(printSegment).mkString("/")}${verb.map(str => s":$str").getOrElse("")}"
    }

    case class FieldPath(fields: Seq[String])

    sealed trait Segment

    final case class LiteralSegment(literal: String) extends Segment

    final case class VariableSegment(fieldPath: FieldPath, template: Option[Seq[Segment]]) extends Segment

    case object SingleSegmentMatcher extends Segment

    case object MultiSegmentMatcher extends Segment

    def parse(input: ParserInput): Either[String, Template] = {
      import Parser.DeliveryScheme.Either
      val parser = new PathTemplateParser(input)

      parser.template.run().left.map(_.format(parser))
    }

    def parseToTemplate(input: String): ParsedTemplate = {
      val parser = new PathTemplateParser(input)
      import akka.parboiled2.Parser.DeliveryScheme.Either
      val template =
        parser.template
          .run()
          .left
          .map(e => PathTemplateParser.PathTemplateParseException(e.format(parser), e))
          .fold(throw _, identity)

      new ParsedTemplate(input, template)
    }

    final case class PathTemplateParseException(message: String, cause: ParseError) extends RuntimeException

    private object ParsedTemplate {

      import akka.http.scaladsl.server.util.TupleOps._

      private def splitVerb(segmentWithVerb: String): (String, String) = {
        val ss = segmentWithVerb.split(':')
        val segment = ss(0)
        val verb = ss(1)
        segment -> verb
      }

      private object VerbAwareSegment extends PathMatcher[Tuple1[String]] {
        def apply(path: Path): Matching[Tuple1[String]] = path match {
          // verb can only occurs at the very end
          case Path.Segment(head, Path.Empty) if head.contains(':') =>
            val (segment, verb) = splitVerb(head)
            Matched(Path(s":${verb}"), Tuple1(segment))
          case Path.Segment(head, tail) =>
            Matched(tail, Tuple1(head))
          case _ => Unmatched
        }
      }

      private val VerbAwareSegments = VerbAwareSegment.repeat(0, 128, separator = PathMatchers.Slash)

      private def verbAwareLiteralSegmentMatcher(literal: String) = new PathMatcher[Tuple1[String]] {
        def apply(path: Path): Matching[Tuple1[String]] = path match {
          case Path.Segment(`literal`, tail) => Matched(tail, Tuple1(literal))
          // verb can only occurs at the very end
          case Path.Segment(head, Path.Empty) if head.contains(':') =>
            val (segment, verb) = splitVerb(head)
            if (segment == literal) Matched(Path(s":${verb}"), Tuple1(segment))
            else Unmatched
          case _ => Unmatched
        }
      }

      private def literalSegmentMatcher(literal: String) = new PathMatcher[Tuple1[String]] {
        def apply(path: Path): Matching[Tuple1[String]] = path match {
          case Path.Segment(`literal`, tail) => Matched(tail, Tuple1(literal))
          case _                             => Unmatched
        }
      }

      implicit def appendList1[T1]: AppendOne.Aux[Tuple1[List[T1]], List[T1], Tuple1[List[T1]]] =
        new AppendOne[Tuple1[List[T1]], List[T1]] {
          type Out = Tuple1[List[T1]]

          def apply(prefix: Tuple1[List[T1]], last: List[T1]): Tuple1[List[T1]] = Tuple1(prefix._1 ++ last)
        }

      implicit class MorePathMatcher1Ops[T](matcher: PathMatcher1[T]) {
        def ignore[U]: PathMatcher1[List[U]] = matcher.map(_ => List.empty[U])

        def capture: PathMatcher1[List[T]] = matcher.map(t => List(t))
      }

      // use List as Option here
      private def segmentMatcher(segment: Segment): PathMatcher[Tuple1[List[(FieldPath, String)]]] = segment match {
        case LiteralSegment(literal) => verbAwareLiteralSegmentMatcher(literal).ignore
        case VariableSegment(fieldPath, template) => {
          template
            .flatMap(_.map {
              case LiteralSegment(literal) => verbAwareLiteralSegmentMatcher(literal).capture
              case SingleSegmentMatcher    => VerbAwareSegment.capture
              case MultiSegmentMatcher     => VerbAwareSegments
              // nested variable isn't allowed
              case VariableSegment(_, _) => PathMatchers.nothingMatcher[Tuple1[List[String]]]
            }.reduceOption(_ / _))
            .getOrElse(VerbAwareSegment.capture)
            .tmap {
              case Tuple1(segments) => Tuple1(List(fieldPath -> segments.mkString("/")))
            }
        }
        case SingleSegmentMatcher => VerbAwareSegment.ignore
        case MultiSegmentMatcher  => VerbAwareSegments.ignore
      }

    }
    final class ParsedTemplate(path: String, template: Template) {

      import ParsedTemplate._

      val matcher: PathMatcher[(List[(FieldPath, String)], Option[String])] = {
        val segmentsMatcher =
          template.segments.map(segmentMatcher).foldLeft(provide(Tuple1(List.empty[(FieldPath, String)])))(_ / _)
        val verbMatcher =
          template.verb
            .map(v => literalSegmentMatcher(":" + v).map(matched => Option(matched.drop(1))))
            .getOrElse(provide(Tuple1(Option.empty[String])))

        segmentsMatcher ~ verbMatcher ~ PathMatchers.PathEnd
      }

      val fields: Seq[TemplateVariable] = {
        val found = scala.collection.mutable.Set.empty[Seq[String]]
        template.segments.collect {
          case v @ VariableSegment(fieldPath, _) if found(fieldPath.fields) =>
            throw new IllegalArgumentException("Duplicate path in template")
          case VariableSegment(fieldPath, segments) =>
            found += fieldPath.fields
            TemplateVariable(
              fieldPath.fields,
              segments.exists(_ match {
                case (_: MultiSegmentMatcher.type) :: _ | _ :: _ :: _ => true
                case _                                                => false
              }))
        }
      }
    }
    final case class TemplateVariable(fieldPath: Seq[String], multi: Boolean)

  }

  private class PathTemplateParser(val input: ParserInput) extends Parser {

    import PathTemplateParser._

    private final val NotLiteral = Set('*', '{', '}', '/', ':', '\n').mkString

    // There is no description of this in the spec. It's not a URL segment, since the spec explicitly says that the value
    // must be URL encoded when expressed as a URL. Since all segments are delimited by a / character or a colon, and a
    // literal may only be a full segment, we could assume it's any non slash or colon character, but that would mean
    // syntax errors in variables for example would end up being parsed as literals, which wouldn't give nice error
    // messages at all. So we'll be a little more strict, and not allow *, { or } in any literals.
    def literal = rule {
      capture(oneOrMore(noneOf(NotLiteral)))
    }

    // Matches ident syntax from https://developers.google.com/protocol-buffers/docs/reference/proto3-spec
    def ident = rule {
      CharPredicate.Alpha ~ oneOrMore(CharPredicate.AlphaNum | '_')
    }

    def fieldPathRule = rule {
      capture(ident) + '.'
    }
    def fieldPath = rule {
      fieldPathRule ~> FieldPath.apply
    }

    def literalSegment = rule {
      literal ~> LiteralSegment.apply
    }

    def variable: Rule1[VariableSegment] = rule {
      ('{' ~ (fieldPath ~ optional('=' ~ varSegments)) ~ '}') ~> VariableSegment.apply
    }

    def singleSegmentMatcher = rule {
      '*' ~ push(SingleSegmentMatcher)
    }

    def multiSegmentMatcher = rule {
      2.times('*') ~ push(MultiSegmentMatcher)
    }

    def multiSegmentMatcherChecked = rule {
      // ensure multiSegmentMatcher only occurs at the end of segments
      multiSegmentMatcher ~!~ (&(('}' ~ optional(verb) ~ EOI) | (optional(verb) ~ EOI)) | fail(
        "Multi segment matchers (**) may only be in the last position of the template"))
    }

    def segment: Rule1[Segment] = rule {
      multiSegmentMatcherChecked | singleSegmentMatcher | variable | literalSegment
    }

    def segments = rule {
      segment + '/'
    }

    def varSegments: Rule1[Seq[Segment]] = rule {
      (multiSegmentMatcherChecked | singleSegmentMatcher | literalSegment) + '/'
    }

    def verb = rule {
      ':' ~ literal
    }

    def templateRule = rule {
      ('/' | fail("Template must start with a slash")) ~ segments ~ optional(verb) ~ EOI
    }
    def template: Rule1[Template] = rule {
      templateRule ~> Template.apply
    }

  }
}
