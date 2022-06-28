/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc

import akka.grpc.internal.{ Codecs, GrpcProtocolNative, Identity }
import akka.grpc.scaladsl.headers.`Message-Accept-Encoding`
import akka.{ ConfigurationException, NotUsed }
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.marshalling.sse.EventStreamMarshalling
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model.headers.Accept
import akka.http.scaladsl.model.sse.ServerSentEvent
import akka.http.scaladsl.model.{
  ContentType,
  ContentTypes,
  ErrorInfo,
  HttpEntity,
  HttpHeader,
  HttpMethod,
  HttpMethods,
  HttpProtocols,
  HttpRequest,
  HttpResponse,
  IllegalRequestException,
  IllegalResponseException,
  MediaTypes,
  RequestEntityAcceptance,
  StatusCodes,
  Uri
}
import akka.http.scaladsl.unmarshalling.Unmarshal
import akka.parboiled2.util.Base64
import akka.stream.Materializer
import akka.stream.scaladsl.{ Keep, Sink, Source }
import akka.util.ByteString
import com.google.api.annotations.AnnotationsProto
import com.google.api.http.HttpRule
import com.google.protobuf.any.{ Any => ProtobufAny }
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType
import com.google.protobuf.Descriptors.{
  Descriptor,
  EnumValueDescriptor,
  FieldDescriptor,
  FileDescriptor,
  MethodDescriptor
}
import com.google.protobuf.util.JsonFormat

import java.lang.{
  Boolean => JBoolean,
  Double => JDouble,
  Float => JFloat,
  Integer => JInteger,
  Long => JLong,
  Short => JShort
}
import com.google.protobuf.{
  DynamicMessage,
  ListValue,
  MessageOrBuilder,
  Struct,
  Value,
  ByteString => ProtobufByteString
}

import java.net.URLDecoder
import java.util.regex.{ Matcher, Pattern }
import scala.annotation.tailrec
import scala.concurrent.{ ExecutionContext, Future }
import scala.util.matching.Regex
import scala.util.parsing.combinator.Parsers
import scala.util.parsing.input.{ CharSequenceReader, Positional }
import scala.collection.JavaConverters._
import scala.util.{ Failure, Success }
import scala.util.control.NonFatal

object HttpApi {

  final val ParseShort: String => Option[JShort] =
    s =>
      try Option(JShort.valueOf(s))
      catch {
        case _: NumberFormatException => None
      }

  final val ParseInt: String => Option[JInteger] =
    s =>
      try Option(JInteger.valueOf(s))
      catch {
        case _: NumberFormatException => None
      }

  final val ParseLong: String => Option[JLong] =
    s =>
      try Option(JLong.valueOf(s))
      catch {
        case _: NumberFormatException => None
      }

  final val ParseFloat: String => Option[JFloat] =
    s =>
      try Option(JFloat.valueOf(s))
      catch {
        case _: NumberFormatException => None
      }

  final val ParseDouble: String => Option[JDouble] =
    s =>
      try Option(JDouble.valueOf(s))
      catch {
        case _: NumberFormatException => None
      }

  final val ParseString: String => Option[String] =
    s => Option(s)

  private[this] final val someJTrue = Some(JBoolean.TRUE)
  private[this] final val someJFalse = Some(JBoolean.FALSE)

  final val ParseBoolean: String => Option[JBoolean] =
    _.toLowerCase match {
      case "true"  => someJTrue
      case "false" => someJFalse
      case _       => None
    }

  // Reads a rfc2045 encoded Base64 string
  final val ParseBytes: String => Option[ProtobufByteString] =
    s => Some(ProtobufByteString.copyFrom(Base64.rfc2045.decode(s))) // Make cheaper? Protobuf has a Base64 decoder?

  final def suitableParserFor(field: FieldDescriptor)(whenIllegal: String => Nothing): String => Option[Any] =
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
    requestEntityAcceptance = RequestEntityAcceptance.Tolerated)

  private final val NEWLINE_BYTES = ByteString('\n')
  private final val NoMatch = PartialFunction.empty[HttpRequest, Future[HttpResponse]]

  def serve(fileDescriptor: FileDescriptor, handler: (HttpRequest, String) => Future[HttpResponse])(
      implicit mat: Materializer,
      ec: ExecutionContext) = {
    val handlers = for {
      service <- fileDescriptor.getServices.asScala
      method <- service.getMethods.asScala
      rules = getRules(method)
      binding <- rules
    } yield {
      new HttpHandler(method, binding, req => handler(req, method.getName))
    }
    handlers.foldLeft(NoMatch) {
      case (NoMatch, first)    => first
      case (previous, current) => current.orElse(previous) // Last goes first
    }
  }

  private def getRules(methDesc: MethodDescriptor) = {
    AnnotationsProto.http.get(Options.convertMethodOptions(methDesc)) match {
      case Some(rule) =>
        rule +: rule.additionalBindings
      case None =>
        Seq.empty
    }
  }

  final class HttpHandler(methDesc: MethodDescriptor, rule: HttpRule, grpcHandler: HttpRequest => Future[HttpResponse])(
      implicit ec: ExecutionContext,
      mat: Materializer)
      extends PartialFunction[HttpRequest, Future[HttpResponse]] {

    // For descriptive purposes so it's clear what these types do
    private type PathParameterEffect = (FieldDescriptor, Option[Any]) => Unit
    private type ExtractPathParameters = (Matcher, PathParameterEffect) => Unit

    // Question: Do we need to handle conversion from JSON names?
    private[this] final def lookupFieldByName(desc: Descriptor, selector: String): FieldDescriptor =
      desc.findFieldByName(
        selector
      ) // TODO potentially start supporting path-like selectors with maximum nesting level?

    private[this] final def parsePathExtractor(
        pattern: String): (PathTemplateParser.ParsedTemplate, ExtractPathParameters) = {
      val template = PathTemplateParser.parse(pattern)
      val pathFieldParsers = template.fields.iterator
        .map {
          case tv @ PathTemplateParser.TemplateVariable(fieldName :: Nil, _) =>
            lookupFieldByName(methDesc.getInputType, fieldName) match {
              case null =>
                configError(
                  s"Unknown field name [$fieldName] in type [${methDesc.getInputType.getFullName}] reference in path template for method [${methDesc.getFullName}]")
              case field =>
                if (field.isRepeated)
                  configError(s"Repeated parameters [${field.getFullName}] are not allowed as path variables")
                else if (field.isMapField)
                  configError(s"Map parameters [${field.getFullName}] are not allowed as path variables")
                else (tv, field, suitableParserFor(field)(configError))
            }
          case multi =>
            // todo implement field paths properly
            configError("Multiple fields in field path not yet implemented: " + multi.fieldPath.mkString("."))
        }
        .zipWithIndex
        .toList

      (
        template,
        (matcher, effect) => {
          pathFieldParsers.foreach {
            case ((_, field, parser), idx) =>
              val rawValue = matcher.group(idx + 1)
              // When encoding, we need to be careful to only encode / if it's a single segment variable. But when
              // decoding, it doesn't matter, we decode %2F if it's there regardless.
              val decoded = URLDecoder.decode(rawValue, "utf-8")
              val value = parser(decoded)
              effect(field, value)
          }
        })
    }

    // This method validates the configuration and returns values obtained by parsing the configuration
    private[this] final def extractAndValidate(): (
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
        import HttpRule.Pattern.{ Custom, Delete, Empty, Get, Patch, Post, Put }
        import akka.http.scaladsl.model.HttpMethods.{ DELETE, GET, PATCH, POST, PUT }

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
      val (template, extractor) = parsePathExtractor(pattern)

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

    private[this] final val (methodPattern, pathTemplate, pathExtractor, bodyDescriptor, responseBodyDescriptor) =
      extractAndValidate()

    @tailrec private[this] final def lookupFieldByPath(desc: Descriptor, selector: String): FieldDescriptor =
      Names.splitNext(selector) match {
        case ("", "")        => null
        case (fieldName, "") => lookupFieldByName(desc, fieldName)
        case (fieldName, next) =>
          val field = lookupFieldByName(desc, fieldName)
          if (field == null) null
          else if (field.getMessageType == null) null
          else lookupFieldByPath(field.getMessageType, next)
      }

    private val jsonParser =
      JsonFormat.parser.usingTypeRegistry(JsonFormat.TypeRegistry.newBuilder().add(bodyDescriptor).build)

    private[this] final val jsonPrinter = JsonFormat.printer
      .usingTypeRegistry(JsonFormat.TypeRegistry.newBuilder.add(methDesc.getOutputType).build())
      .includingDefaultValueFields()
      .omittingInsignificantWhitespace()

    // Making this a method so we can ensure it's used the same way
    final def matches(path: Uri.Path): Boolean =
      pathTemplate.regex.pattern
        .matcher(path.toString())
        .matches() // FIXME path.toString is costly, and using Regexes are too, switch to using a generated parser instead

    private[this] final def parseRequestParametersInto(
        query: Map[String, List[String]],
        inputBuilder: DynamicMessage.Builder): Unit =
      query.foreach {
        case (selector, values) =>
          if (values.nonEmpty) {
            lookupFieldByPath(methDesc.getInputType, selector) match {
              case null => requestError("Query parameter [$selector] refers to non-existant field")
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

    private[this] final def parsePathParametersInto(matcher: Matcher, inputBuilder: DynamicMessage.Builder): Unit =
      pathExtractor(
        matcher,
        (field, value) =>
          inputBuilder.setField(field, value.getOrElse(requestError("Path contains value of wrong type!"))))

    final private[this] val isHttpBodyResponse = methDesc.getOutputType.getFullName == "google.api.HttpBody"

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

    // FIXME Devise other way of supporting responseBody, this is waaay too costly and unproven
    // This method converts an arbitrary type to something which can be represented as JSON.
    private[this] final def responseBody(
        jType: JavaType,
        value: AnyRef,
        repeated: Boolean): com.google.protobuf.Value = {
      val result =
        if (repeated) {
          Value.newBuilder.setListValue(
            ListValue.newBuilder.addAllValues(
              value.asInstanceOf[java.lang.Iterable[AnyRef]].asScala.map(v => responseBody(jType, v, false)).asJava))
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
              value
                .asInstanceOf[MessageOrBuilder]
                .getAllFields
                .forEach((k, v) =>
                  sb.putFields(
                    k.getJsonName,
                    responseBody(k.getJavaType, v, k.isRepeated)
                  ) //Switch to getName if enabling preservingProtoFieldNames in the JSON Printer
                )
              b.setStructValue(sb)
            case JavaType.STRING => b.setStringValue(value.asInstanceOf[String])
          }
        }
      result.build()
    }

    private final val IdentityHeader = new `Message-Accept-Encoding`("identity")
    private final val grpcWriter = GrpcProtocolNative.newWriter(Identity)

    private[this] final def updateRequest(req: HttpRequest, message: DynamicMessage): HttpRequest = {
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

    final def transformRequest(req: HttpRequest, matcher: Matcher): Future[HttpRequest] =
      if (rule.body.nonEmpty && req.entity.contentType != ContentTypes.`application/json`) {
        Future.failed(IllegalRequestException(StatusCodes.BadRequest, "Content-type must be application/json!"))
      } else {
        val inputBuilder = DynamicMessage.newBuilder(methDesc.getInputType)
        rule.body match {
          case "" => // Iff empty body rule, then only query parameters
            req.discardEntityBytes()
            parseRequestParametersInto(req.uri.query().toMultiMap, inputBuilder)
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
                parseRequestParametersInto(req.uri.query().toMultiMap, inputBuilder)
                parsePathParametersInto(matcher, inputBuilder)
                inputBuilder.setField(subField, subInputBuilder.build())
                updateRequest(req, inputBuilder.build)
              })
        }
      }

    private[this] final def parseResponseBody(pbAny: ProtobufAny): MessageOrBuilder = {
      val bytes = ReplySerializer.serialize(pbAny)
      val message = DynamicMessage.parseFrom(methDesc.getOutputType, bytes.iterator.asInputStream)
      responseBodyDescriptor.fold(message: MessageOrBuilder) { field =>
        message.getField(field) match {
          case m: MessageOrBuilder if !field.isRepeated => m // No need to wrap this
          case value                                    => responseBody(field.getJavaType, value, field.isRepeated)
        }
      }
    }

    final def transformResponse(
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

    final val AnyTypeUrlHostName = "type.googleapis.com/"

    final val expectedReplyTypeUrl: String = AnyTypeUrlHostName + methDesc.getOutputType.getFullName

    private[this] final def processRequest(req: HttpRequest, matcher: Matcher): Future[HttpResponse] = {
      transformRequest(req, matcher)
        .transformWith {
          case Success(request) =>
            val response = grpcHandler(request).map { resp =>
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
      assert((methodPattern == ANY_METHOD || req.method == methodPattern))
      val matcher = pathTemplate.regex.pattern.matcher(req.uri.path.toString())
      assert(matcher.matches())
      processRequest(req, matcher)
    }
  }

  private final object ReplySerializer extends ProtobufSerializer[ProtobufAny] {
    override final def serialize(reply: ProtobufAny): ByteString =
      if (reply.value.isEmpty) ByteString.empty
      else ByteString.fromArrayUnsafe(reply.value.toByteArray)

    override final def deserialize(bytes: ByteString): ProtobufAny =
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

  private object PathTemplateParser extends Parsers {

    override type Elem = Char

    final class ParsedTemplate(path: String, template: Template) {
      val regex: Regex = {
        def doToRegex(builder: StringBuilder, segments: List[Segment], matchSlash: Boolean): StringBuilder =
          segments match {
            case Nil => builder // Do nothing
            case head :: tail =>
              if (matchSlash) {
                builder.append('/')
              }

              head match {
                case LiteralSegment(literal) =>
                  builder.append(Pattern.quote(literal))
                case SingleSegmentMatcher =>
                  builder.append("[^/:]*")
                case MultiSegmentMatcher() =>
                  builder.append(".*")
                case VariableSegment(_, None) =>
                  builder.append("([^/:]*)")
                case VariableSegment(_, Some(template)) =>
                  builder.append('(')
                  doToRegex(builder, template, matchSlash = false)
                  builder.append(')')
              }

              doToRegex(builder, tail, matchSlash = true)
          }

        val builder = doToRegex(new StringBuilder, template.segments, matchSlash = true)

        template.verb
          .foldLeft(builder) { (builder, verb) =>
            builder.append(':').append(Pattern.quote(verb))
          }
          .toString()
          .r
      }

      val fields: List[TemplateVariable] = {
        var found = Set.empty[List[String]]
        template.segments.collect {
          case v @ VariableSegment(fieldPath, _) if found(fieldPath) =>
            throw PathTemplateParseException("Duplicate path in template", path, v.pos.column + 1)
          case VariableSegment(fieldPath, segments) =>
            found += fieldPath
            TemplateVariable(
              fieldPath,
              segments.exists(_ match {
                case ((_: MultiSegmentMatcher) :: _) | (_ :: _ :: _) => true
                case _                                               => false
              }))
        }
      }
    }

    final case class TemplateVariable(fieldPath: List[String], multi: Boolean)

    final case class PathTemplateParseException(msg: String, path: String, column: Int)
        extends RuntimeException(
          s"$msg at ${if (column >= path.length) "end of input" else s"character $column"} of '$path'") {

      def prettyPrint: String = {
        val caret =
          if (column >= path.length) ""
          else "\n" + path.take(column - 1).map { case '\t' => '\t'; case _ => ' ' } + "^"

        s"$msg at ${if (column >= path.length) "end of input" else s"character $column"}:${'\n'}$path$caret"
      }
    }

    final def parse(path: String): ParsedTemplate =
      template(new CharSequenceReader(path)) match {
        case Success(template, _) =>
          new ParsedTemplate(path, validate(path, template))
        case NoSuccess(msg, next) =>
          throw PathTemplateParseException(msg, path, next.pos.column)
      }

    private final def validate(path: String, template: Template): Template = {
      def flattenSegments(segments: Segments, allowVariables: Boolean): Segments =
        segments.flatMap {
          case variable: VariableSegment if !allowVariables =>
            throw PathTemplateParseException("Variable segments may not be nested", path, variable.pos.column)
          case VariableSegment(_, Some(nested)) => flattenSegments(nested, false)
          case other                            => List(other)
        }

      // Flatten, verifying that there are no nested variables
      val flattened = flattenSegments(template.segments, true)

      // Verify there are no ** matchers that aren't the last matcher
      flattened.dropRight(1).foreach {
        case m @ MultiSegmentMatcher() =>
          throw PathTemplateParseException(
            "Multi segment matchers (**) may only be in the last position of the template",
            path,
            m.pos.column)
        case _ =>
      }
      template
    }

    // AST for syntax described here:
    // https://cloud.google.com/endpoints/docs/grpc-service-config/reference/rpc/google.api#google.api.HttpRule.description.subsection
    // Note that there are additional rules (eg variables cannot contain nested variables) that this AST doesn't enforce,
    // these are validated elsewhere.
    private final case class Template(segments: Segments, verb: Option[Verb])

    private type Segments = List[Segment]
    private type Verb = String

    private sealed trait Segment

    private final case class LiteralSegment(literal: Literal) extends Segment

    private final case class VariableSegment(fieldPath: FieldPath, template: Option[Segments])
        extends Segment
        with Positional

    private type FieldPath = List[Ident]

    private case object SingleSegmentMatcher extends Segment

    private final case class MultiSegmentMatcher() extends Segment with Positional

    private type Literal = String
    private type Ident = String

    private final val NotLiteral = Set('*', '{', '}', '/', ':', '\n')

    // Matches ident syntax from https://developers.google.com/protocol-buffers/docs/reference/proto3-spec
    private final val ident: Parser[Ident] = rep1(
      acceptIf(ch => (ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z'))(e =>
        s"Expected identifier first letter, but got '$e'"),
      acceptIf(ch => (ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '_')(_ =>
        "identifier part")) ^^ (_.mkString)

    // There is no description of this in the spec. It's not a URL segment, since the spec explicitly says that the value
    // must be URL encoded when expressed as a URL. Since all segments are delimited by a / character or a colon, and a
    // literal may only be a full segment, we could assume it's any non slash or colon character, but that would mean
    // syntax errors in variables for example would end up being parsed as literals, which wouldn't give nice error
    // messages at all. So we'll be a little more strict, and not allow *, { or } in any literals.
    private final val literal: Parser[Literal] =
      rep(acceptIf(ch => !NotLiteral(ch))(_ => "literal part")) ^^ (_.mkString)

    private final val fieldPath: Parser[FieldPath] = rep1(ident, '.' ~> ident)

    private final val literalSegment: Parser[LiteralSegment] = literal ^^ LiteralSegment

    // After we see an open curly, we commit to erroring if we fail to parse the remainder.
    private final def variable: Parser[VariableSegment] =
      positioned(
        '{' ~> commit(
          fieldPath ~ ('=' ~> segments).? <~ '}'.withFailureMessage("Unclosed variable or unexpected character") ^^ {
            case fieldPath ~ maybeTemplate => VariableSegment(fieldPath, maybeTemplate)
          }))

    private final val singleSegmentMatcher: Parser[SingleSegmentMatcher.type] = '*' ^^ (_ => SingleSegmentMatcher)
    private final val multiSegmentMatcher: Parser[MultiSegmentMatcher] = positioned(
      '*' ~ '*' ^^ (_ => MultiSegmentMatcher()))
    private final val segment: Parser[Segment] = commit(
      multiSegmentMatcher | singleSegmentMatcher | variable | literalSegment)

    private final val verb: Parser[Verb] = ':' ~> literal
    private final val segments: Parser[Segments] = rep1(segment, '/' ~> segment)
    private final val endOfInput: Parser[None.type] = Parser { in =>
      if (!in.atEnd) {
        Error("Expected '/', ':', path literal character, or end of input", in)
      } else {
        Success(None, in)
      }
    }

    private final val template: Parser[Template] = '/'.withFailureMessage("Template must start with a slash") ~>
      segments ~ verb.? <~ endOfInput ^^ {
        case segments ~ maybeVerb => Template(segments, maybeVerb)
      }
  }

}
