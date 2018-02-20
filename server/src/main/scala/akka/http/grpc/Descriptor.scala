package akka.http.grpc

import akka.NotUsed
import akka.http.scaladsl.model.HttpEntity.LastChunk
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.stream.{ Attributes, Materializer }
import akka.stream.impl.io.ByteStringParser
import akka.stream.impl.io.ByteStringParser.{ ParseResult, ParseStep }
import akka.stream.scaladsl.{ Flow, Sink, Source }
import akka.stream.stage.GraphStageLogic
import akka.util.ByteString
import com.trueaccord.scalapb.{ GeneratedMessage, GeneratedMessageCompanion, Message }

import scala.concurrent.{ ExecutionContext, Future }

// TODO separate it into "runtime" library
case class Descriptor[T](name: String, calls: Seq[CallDescriptor[T, _, _]])

// TODO separate it into "runtime" library
case class CallDescriptor[T, Request, Response](
  methodName: String,
  serverInvoker: CallDescriptor.ServerInvoker[T, Request, Response],
  requestSerializer: ProtobufSerializer[Request],
  responseSerializer: ProtobufSerializer[Response]) {

  def toCallInvoker(server: T, mat: Materializer, ec: ExecutionContext): CallInvoker[Request, Response] = {
    new CallInvoker[Request, Response](this, serverInvoker(server, mat, ec))
  }
}

// TODO separate it into "runtime" library
object CallDescriptor {

  type ServerInvoker[T, Request, Response] = (T, Materializer, ExecutionContext) => (Source[Request, _] => Source[Response, _])

  def named[T, Request: ProtobufSerializer, Response: ProtobufSerializer](
    name: String, serverInvoker: ServerInvoker[T, Request, Response]): CallDescriptor[T, Request, Response] =
    CallDescriptor(name, serverInvoker, implicitly[ProtobufSerializer[Request]], implicitly[ProtobufSerializer[Response]])
}

// TODO separate it into "runtime" library;
// TODO go over ByteBuffers so we avoid copying?
trait ProtobufSerializer[T] {
  def serialize(t: T): ByteString
  def deserialize(bytes: ByteString): T
}

object ProtobufSerializer {
  implicit def scalaPbSerializer[T <: GeneratedMessage with Message[T]: GeneratedMessageCompanion]: ProtobufSerializer[T] = {
    new ScalapbProtobufSerializer(implicitly[GeneratedMessageCompanion[T]])
  }
}

class ScalapbProtobufSerializer[T <: GeneratedMessage with Message[T]](companion: GeneratedMessageCompanion[T]) extends ProtobufSerializer[T] {
  override def serialize(t: T) = ByteString(companion.toByteArray(t))
  override def deserialize(bytes: ByteString): T = companion.parseFrom(bytes.iterator.asInputStream)
}

class CallInvoker[Request, Response](
  val desc: CallDescriptor[_, Request, Response],
  handler: Source[Request, _] => Source[Response, _]) {

  def apply(request: HttpRequest): HttpResponse = {
    // todo handle trailers
    val byteStream = request.entity.dataBytes

    val inStream = (byteStream via Grpc.grpcFramingDecoder).map(desc.requestSerializer.deserialize)

    val outStream = handler(inStream)
    val outChunks = (outStream.map(desc.responseSerializer.serialize) via Grpc.grpcFramingEncoder)
      .map(bytes => HttpEntity.Chunk(bytes))
      .concat(Source.single(LastChunk(trailer = List(RawHeader("grpc-status", "0")))))
      .recover {
        case e: Exception =>
          // todo handle better
          e.printStackTrace()
          LastChunk(trailer = List(RawHeader("grpc-status", "2"), RawHeader("grpc-message", "Stream error")))
      }

    HttpResponse(entity = HttpEntity.Chunked(Grpc.contentType, outChunks))

  }
}

class ServerInvokerBuilder[T] {

  import CallDescriptor.ServerInvoker

  def apply[Request, Response](handler: T => (Source[Request, _] => Source[Response, _])): ServerInvoker[T, Request, Response] =
    (t, _, _) => handler(t)

  def unaryToUnary[Request, Response](handler: T => (Request => Future[Response])): ServerInvoker[T, Request, Response] = { (t, mat, ec) => sourceIn =>
    Source.fromFuture(sourceIn.runWith(Sink.head)(mat)
      .flatMap(handler(t))(ec))
  }

  def unaryToStream[Request, Response, M](handler: T => (Request => Source[Response, M])): ServerInvoker[T, Request, Response] = { (t, mat, ec) => sourceIn =>
    Source.fromFutureSource[Response, M](
      sourceIn.runWith(Sink.head)(mat)
        .map(handler(t))(ec))
  }

  def streamToUnary[Request, Response](handler: T => Source[Request, _] => Future[Response]): ServerInvoker[T, Request, Response] = { (t, _, _) => sourceIn =>
    Source.fromFuture(handler(t)(sourceIn))
  }
}

// TODO separate it into "runtime" library;
object Grpc {

    // TODO should this be a Route to allow mixing GRPC endpoints and other routes?
    def apply[T](descriptor: Descriptor[T], service: T)(implicit mat: Materializer, ec: ExecutionContext): PartialFunction[HttpRequest, HttpResponse] = {
    // TODO this builds up a function based on the Descriptor that was generated from the grpc .proto file.
    // Shouldn't we generate this function directly instead of going via the Descriptor?
    val base = Path / descriptor.name

    val handlerMap: Map[Path, CallInvoker[_, _]] = descriptor.calls.map { call =>
      val path = Path / call.methodName
      val invoker = call.toCallInvoker(service, mat, ec)
      path -> invoker
    }.toMap

    Function.unlift { request =>
      println(s"got request $request")
      if (request.uri.path.startsWith(base)) {
        val path = request.uri.path.tail.tail
        handlerMap.get(path) match {
          case Some(handler) =>
            println(s"got handler $handler")
            Some(handler(request))
          case None =>
            Some(HttpResponse(entity = HttpEntity.Chunked(contentType, Source.single(
              LastChunk(trailer = List(RawHeader("grpc-status", "5"), RawHeader("grpc-message", "gRCP method at path " + path + " not found.")))))))
        }
      } else {
        None
      }
    }
  }

  val contentType = MediaType.applicationBinary("grpc+proto", MediaType.NotCompressible).toContentType

  val notCompressed = ByteString(0)

  def grpcFramingEncoder: Flow[ByteString, ByteString, NotUsed] = {
    Flow[ByteString].map { frame =>

      // todo handle compression

      val length = frame.size
      notCompressed ++ ByteString(
        (length >> 24).toByte,
        (length >> 16).toByte,
        (length >> 8).toByte,
        length.toByte) ++ frame
    }
  }

  def grpcFramingDecoder: Flow[ByteString, ByteString, NotUsed] = {
    Flow.fromGraph(new GrpcFramingDecoderStage)
  }

  class GrpcFramingDecoderStage extends ByteStringParser[ByteString] {
    override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new ParsingLogic {
      startWith(ReadFrameHeader)

      trait Step extends ParseStep[ByteString]

      object ReadFrameHeader extends Step {
        override def parse(reader: ByteStringParser.ByteReader): ByteStringParser.ParseResult[ByteString] = {
          val compression = reader.readByte()
          // If we want to support > 2GB frames, this should be unsigned
          val length = reader.readIntBE()

          if (length == 0) ParseResult(Some(ByteString.empty), ReadFrameHeader)
          else ParseResult(None, ReadFrame(compression == 1, length), acceptUpstreamFinish = false)
        }
      }

      case class ReadFrame(compression: Boolean, length: Int) extends Step {
        override def parse(reader: ByteStringParser.ByteReader): ParseResult[ByteString] = {
          // todo handle compression
          ParseResult(Some(reader.take(length)), ReadFrameHeader)
        }
      }
    }
  }

}
