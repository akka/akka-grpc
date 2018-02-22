package akka.http.grpc

import java.io.InputStream
import java.security.{ KeyStore, SecureRandom }
import javax.net.ssl.{ KeyManagerFactory, SSLContext }

import akka.actor.ActorSystem
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse, StatusCodes }
import akka.http.scaladsl.model.Uri.Path
import akka.http.scaladsl.model.Uri.Path.Segment
import akka.stream.Materializer
import akka.http.scaladsl.{ Http2, HttpsConnectionContext }
import akka.stream.ActorMaterializer
import io.grpc.examples.helloworld.{ HelloReply, HelloRequest }
import io.grpc.netty.{ GrpcSslContexts, NettyChannelBuilder }
import io.netty.handler.ssl.{ SslContextBuilder, SslProvider }

import scala.concurrent.{ ExecutionContext, Future }

trait Greeter {
  def sayHello(req: HelloRequest): Future[HelloReply]

  def toHandler()(implicit mat: Materializer): PartialFunction[HttpRequest, Future[HttpResponse]] = {
    implicit val ec: ExecutionContext = mat.executionContext

    // This can be simplified further once we always use scalapb
    val helloRequestSerializer = new ScalapbProtobufSerializer(HelloRequest.messageCompanion)
    val helloResponseSerializer = new ScalapbProtobufSerializer(HelloReply.messageCompanion)

    def handle(request: HttpRequest, method: String): Future[HttpResponse] = method match {
      case "SayHello" ⇒
        GrpcRuntimeMarshalling.unmarshall(request, helloRequestSerializer, mat)
          .flatMap(sayHello)
          .map(e ⇒ GrpcRuntimeMarshalling.marshal(e, helloResponseSerializer, mat))
      case other ⇒
        Future.successful(HttpResponse(StatusCodes.NotFound))
    }

    Function.unlift((req: HttpRequest) ⇒ req.uri.path match {
      case Path.Slash(Segment(Greeter.name, Path.Slash(Segment(method, Path.Empty)))) ⇒ Some(handle(req, method))
      case _ ⇒ None
    })
  }
}
object Greeter {
  val name = "helloworld.Greeter"
}

class GreeterImpl extends Greeter {
  import scala.concurrent.ExecutionContext.Implicits.global
  override def sayHello(req: HelloRequest) = Future {
    println("returning response")
    HelloReply("Hello " + req.name)
  }
}

object Test extends App {

  implicit val system = ActorSystem()
  implicit val mat = ActorMaterializer()
  import system.dispatcher

  // Start scalapb grpc client
  // val channel = ManagedChannelBuilder.forAddress("localhost", 8443).build
  val channel = NettyChannelBuilder.forAddress("akka.example.org", 8443)
    .sslContext(GrpcSslContexts.configure(SslContextBuilder.forClient(), SslProvider.JDK)
      .trustManager(resourceStream("rootCA.crt"))
      .build())
    .build

  private def resourceStream(resourceName: String): InputStream = {
    val is = getClass.getClassLoader.getResourceAsStream(resourceName)
    require(is ne null, s"Resource $resourceName not found")
    is
  }

  private def serverHttpContext() = {
    // never put passwords into code!
    val password = "abcdef".toCharArray

    val ks = KeyStore.getInstance("PKCS12")
    ks.load(resourceStream("server.p12"), password)

    val keyManagerFactory = KeyManagerFactory.getInstance("SunX509")
    keyManagerFactory.init(ks, password)

    val context = SSLContext.getInstance("TLS")
    context.init(keyManagerFactory.getKeyManagers, null, new SecureRandom)

    new HttpsConnectionContext(context)
  }

  try {
    // Start Akka HTTP server
    val handler = new GreeterImpl().toHandler()

    Http2().bindAndHandleAsync(
      handler,
      interface = "localhost",
      port = 8443,
      httpsContext = serverHttpContext())

    val request = HelloRequest(name = "World")

    //    val blockingStub = GreeterGrpc.blockingStub(channel)
    //
    //    Thread.sleep(30000)
    //
    //    val reply: HelloReply = blockingStub.sayHello(request)
    //    println(reply)

    Thread.sleep(5000)

  } finally {
    system.terminate()
    channel.shutdown()
  }

}
