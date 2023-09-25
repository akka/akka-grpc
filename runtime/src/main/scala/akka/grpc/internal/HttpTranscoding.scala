/*
 * Copyright (C) 2018-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import akka.ConfigurationException
import akka.annotation.InternalApi
import akka.grpc.{ Options, ProtobufSerializer }
import akka.http.scaladsl.model._
import akka.parboiled2.util.Base64
import akka.stream.Materializer
import akka.util.ByteString
import com.google.api.annotations.AnnotationsProto
import com.google.api.http.HttpRule
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType
import com.google.protobuf.Descriptors._
import com.google.protobuf.any.{ Any => ProtobufAny }
import com.google.protobuf.{ ByteString => ProtobufByteString }

import java.lang.{
  Boolean => JBoolean,
  Double => JDouble,
  Float => JFloat,
  Integer => JInteger,
  Long => JLong,
  Short => JShort
}
import java.util.regex.Pattern
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.util.matching.Regex
import scala.util.parsing.combinator.Parsers
import scala.util.parsing.input.{ CharSequenceReader, Positional }

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
  final val configError: String => Nothing = s => throw new ConfigurationException("HTTP API Config: " + s)

  // We use this to signal to the requestor that there's something wrong with the request
  final val requestError: String => Nothing = s => throw IllegalRequestException(StatusCodes.BadRequest, s)

  // This is used to support the "*" custom pattern
  final val ANY_METHOD = HttpMethod.custom(
    name = "ANY",
    safe = false,
    idempotent = false,
    requestEntityAcceptance = RequestEntityAcceptance.Tolerated,
    contentLengthAllowed = true)

  final val NEWLINE_BYTES = ByteString('\n')

  @InternalApi
  def serve(fileDescriptor: FileDescriptor)(
      implicit mat: Materializer,
      ec: ExecutionContext): Seq[(MethodDescriptor, HttpRule)] = {
    for {
      service <- fileDescriptor.getServices.asScala
      method <- service.getMethods.asScala
      rules = getRules(method)
      binding <- rules
    } yield {
      method -> binding
    }
  }

  private def getRules(methDesc: MethodDescriptor): Seq[HttpRule] = {
    AnnotationsProto.http.get(Options.convertMethodOptions(methDesc)) match {
      case Some(rule) =>
        rule +: rule.additionalBindings
      case None =>
        Seq.empty
    }
  }

  object ReplySerializer extends ProtobufSerializer[ProtobufAny] {
    override def serialize(reply: ProtobufAny): ByteString =
      if (reply.value.isEmpty) ByteString.empty
      else ByteString.fromArrayUnsafe(reply.value.toByteArray)

    override def deserialize(bytes: ByteString): ProtobufAny =
      throw new UnsupportedOperationException("operation not supported")
  }

  object Names {
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

  object PathTemplateParser extends Parsers {

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
                case (_: MultiSegmentMatcher) :: _ | _ :: _ :: _ => true
                case _                                           => false
              }))
        }
      }
    }

    final case class TemplateVariable(fieldPath: List[String], multi: Boolean)

    private final case class PathTemplateParseException(msg: String, path: String, column: Int)
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
          case VariableSegment(_, Some(nested)) => flattenSegments(nested, allowVariables = false)
          case other                            => List(other)
        }

      // Flatten, verifying that there are no nested variables
      val flattened = flattenSegments(template.segments, allowVariables = true)

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
