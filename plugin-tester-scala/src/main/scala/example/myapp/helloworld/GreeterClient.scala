//#full-client
package example.myapp.helloworld

import javax.net.ssl.SSLContext

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.util.{Failure, Success}

object GreeterClient {

  // trait LoggerFactory {
  //   def apply(clazz: Class[_]): NoDepsLogger
  //   def apply(name: String): NoDepsLogger
  // }

  private def configureSSLContext(rootConfig: Config, sslConfig: Config): SSLContext = {
    val sslConfigWithDefaults: Config = sslConfig.withFallback(rootConfig.getConfig("ssl-config"))
    println("sslConfigWithDefaults: " + sslConfigWithDefaults)
    val sslConfigSettings: SSLConfigSettings = SSLConfigFactory.parse(sslConfigWithDefaults)
    val sslContext: SSLContext = new ConfigSSLContextBuilder(
      com.typesafe.sslconfig.util.PrintlnLogger.factory, // FIXME
      sslConfigSettings,
      new DefaultKeyManagerFactoryWrapper(sslConfigSettings.keyManagerConfig.algorithm),
      new DefaultTrustManagerFactoryWrapper(sslConfigSettings.trustManagerConfig.algorithm)
    ).build()
    sslContext
  }

  private def grpcClientConfig(config: Config, serviceName: String): Config = {
    def configPath(n: String): String = s"""akka.grpc.client."$n""""
    val clientConfig: Config = config.getConfig(configPath(serviceName))
    val defaultConfig: Config = config.getConfig(configPath("*"))
    clientConfig.withFallback(defaultConfig)
  }

  private def parseClientSettings(rootConfig: Config, clientConfig: Config): GrpcClientSettings = {
    val sslContext: SSLContext = configureSSLContext(rootConfig, clientConfig.getConfig("ssl-config"))
    GrpcClientSettings(clientConfig).withSSLContext(sslContext)
  }

  def main(args: Array[String]): Unit = {

    implicit val sys = ActorSystem("HelloWorldClient")
    implicit val mat = ActorMaterializer()
    implicit val ec = sys.dispatcher

    val config = sys.settings.config
    val clientSettings = try {
      parseClientSettings(config, grpcClientConfig(config, "helloworld.GreeterService"))
    } catch {
      case ex: Exception =>
        System.err.println("Exception encountered while parsing client settings")
        ex.printStackTrace()
        sys.terminate()
        throw ex
    }

    val client = new GreeterServiceClient(
      GrpcClientSettings("127.0.0.1", 8080)
        .withOverrideAuthority("foo.test.google.fr")
        .withTrustedCaCertificate("ca.pem"))

    singleRequestReply()
    streamingRequest()
    streamingReply()
    streamingRequestReply()

    sys.scheduler.schedule(1.second, 1.second) {
      singleRequestReply()
    }

    def singleRequestReply(): Unit = {
      sys.log.info("Performing request")
      val reply = client.sayHello(HelloRequest("Alice"))
      reply.onComplete {
        case Success(msg) =>
          println(s"got single reply: $msg")
        case Failure(e) =>
          println(s"Error sayHello: $e")
      }
    }

    def streamingRequest(): Unit = {
      val requests = List("Alice", "Bob", "Peter").map(HelloRequest.apply)
      val reply = client.itKeepsTalking(Source(requests))
      reply.onComplete {
        case Success(msg) =>
          println(s"got single reply for streaming requests: $msg")
        case Failure(e) =>
          println(s"Error streamingRequest: $e")
      }
    }

    def streamingReply(): Unit = {
      val responseStream = client.itKeepsReplying(HelloRequest("Alice"))
      val done: Future[Done] =
        responseStream.runForeach(reply => println(s"got streaming reply: ${reply.message}"))

      done.onComplete {
        case Success(_) =>
          println("streamingReply done")
        case Failure(e) =>
          println(s"Error streamingReply: $e")
      }
    }

    def streamingRequestReply(): Unit = {
      val requestStream: Source[HelloRequest, NotUsed] =
        Source
          .tick(100.millis, 1.second, "tick")
          .zipWithIndex
          .map { case (_, i) => i }
          .map(i => HelloRequest(s"Alice-$i"))
          .take(10)
          .mapMaterializedValue(_ => NotUsed)

      val responseStream: Source[HelloReply, NotUsed] = client.streamHellos(requestStream)
      val done: Future[Done] =
        responseStream.runForeach(reply => println(s"got streaming reply: ${reply.message}"))

      done.onComplete {
        case Success(_) =>
          println("streamingRequestReply done")
        case Failure(e) =>
          println(s"Error streamingRequestReply: $e")
      }
    }
  }


}

//#full-client
