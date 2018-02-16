package akka.http.grpc

import javax.net.ssl.SSLContext

import akka.actor.ActorSystem
import akka.http.impl.util.JavaMapping.HttpsConnectionContext
import akka.http.scaladsl.{ ConnectionContext, Http2 }
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import akka.stream.{ ActorMaterializer, Materializer }
import io.grpc.ManagedChannelBuilder
import io.grpc.examples.helloworld.helloworld.{ GreeterGrpc, HelloReply, HelloRequest }

import scala.concurrent.{ ExecutionContext, Future }

trait Greeter {
  def sayHello(req: HelloRequest): Future[HelloReply]
}

class GreeterImpl extends Greeter {
  override def sayHello(req: HelloRequest) = Future.successful(HelloReply("Hello " + req.name))
}

object Greeter {
  val descriptor: Descriptor[Greeter] = {
    val builder = new ServerInvokerBuilder[Greeter]
    Descriptor[Greeter]("helloworld.Greeter", Seq(
      CallDescriptor.named("SayHello", builder.unaryToUnary(_.sayHello))))
  }
}

object Test extends App {

  implicit val system = ActorSystem()
  implicit val mat = ActorMaterializer()
  import system.dispatcher

  // Start scalapb grpc client
  val channel = ManagedChannelBuilder.forAddress("localhost", 8443).build

  try {
    val greeterHandler = Grpc(Greeter.descriptor, new GreeterImpl)

    // Start Akka HTTP server
    Http2().bindAndHandleAsync(
      request => Future.successful(greeterHandler(request)),
      interface = "localhost",
      port = 8443,
      httpsContext = ConnectionContext.https(SSLContext.getDefault))

    val request = HelloRequest(name = "World")

    val blockingStub = GreeterGrpc.blockingStub(channel)

    Thread.sleep(30000)

    val reply: HelloReply = blockingStub.sayHello(request)
    println(reply)
  } finally {
    system.terminate()
    channel.shutdown()
  }

}
