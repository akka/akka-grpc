package akka.grpc

/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

import akka.annotation.{ ApiMayChange, DoNotInherit, InternalApi }
import akka.grpc.internal.HttpTranscoding._
import akka.grpc.internal.{ GrpcProtocolNative, Identity }
import akka.grpc.scaladsl.headers.`Message-Accept-Encoding`
import akka.http.scaladsl.model._
import com.google.api.http.HttpRule
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType
import com.google.protobuf.Descriptors._
import com.google.protobuf.util.JsonFormat
import com.google.protobuf.{
  DynamicMessage,
  ListValue,
  MessageOrBuilder,
  Struct,
  Value,
  ByteString => ProtobufByteString
}

import java.lang.{ Boolean => JBoolean, Double => JDouble, Float => JFloat, Integer => JInteger, Long => JLong }
import java.net.URLDecoder
import java.util.regex.Matcher
import scala.annotation.tailrec
import scala.collection.JavaConverters._
import com.google.protobuf.any.{ Any => ProtobufAny }

@InternalApi
private[grpc] object HttpHandler {
  // For descriptive purposes so it's clear what these types do
  private type PathParameterEffect = (FieldDescriptor, Option[Any]) => Unit
  private type ExtractPathParameters = (Matcher, PathParameterEffect) => Unit

  // Question: Do we need to handle conversion from JSON names?
  def lookupFieldByName(desc: Descriptor, selector: String): FieldDescriptor =
    desc.findFieldByName(selector) // TODO potentially start supporting path-like selectors with maximum nesting level?

  def parsePathExtractor(
      methDesc: MethodDescriptor,
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
  def extractAndValidate(rule: HttpRule, methDesc: MethodDescriptor)
      : (HttpMethod, PathTemplateParser.ParsedTemplate, ExtractPathParameters, Descriptor, Option[FieldDescriptor]) = {
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
            (ANY_METHOD, chp.path) // FIXME is "path" the same as "pattern" for the other kinds? Is an empty kind valid?
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

}

@DoNotInherit
@ApiMayChange
abstract class HttpHandler {
  def methDesc: MethodDescriptor

  def rule: HttpRule

  import HttpHandler._

  val (methodPattern, pathTemplate, pathExtractor, bodyDescriptor, responseBodyDescriptor) =
    extractAndValidate(rule, methDesc)

  // Making this a method so we can ensure it's used the same way
  def matches(path: Uri.Path): Boolean =
    pathTemplate.regex.pattern
      .matcher(path.toString())
      .matches() // FIXME path.toString is costly, and using Regexes are too, switch to using a generated parser instead

  def parsePathParametersInto(matcher: Matcher, inputBuilder: DynamicMessage.Builder): Unit =
    pathExtractor(
      matcher,
      (field, value) =>
        inputBuilder.setField(field, value.getOrElse(requestError("Path contains value of wrong type!"))))

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

  final val jsonParser: JsonFormat.Parser =
    JsonFormat.parser.usingTypeRegistry(JsonFormat.TypeRegistry.newBuilder().add(bodyDescriptor).build)

  // TODO it's annoying that protobuf JSON printer format every Long to String in JSON, find a way to deal with it
  // see https://stackoverflow.com/questions/53080136/protobuf-jsonformater-printer-convert-long-to-string-in-json
  final val jsonPrinter = JsonFormat.printer
    .usingTypeRegistry(JsonFormat.TypeRegistry.newBuilder.add(methDesc.getOutputType).build())
    .includingDefaultValueFields()
    .omittingInsignificantWhitespace()

  final val IdentityHeader = new `Message-Accept-Encoding`("identity")
  final val grpcWriter = GrpcProtocolNative.newWriter(Identity)
  final val isHttpBodyResponse = methDesc.getOutputType.getFullName == "google.api.HttpBody"

}
