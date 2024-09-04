/*
 * Copyright (C) 2009-2023 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.internal

import akka.actor.ActorSystem
import akka.annotation.InternalApi
import akka.event.LoggingAdapter
import akka.grpc.GrpcClientSettings
import akka.grpc.GrpcResponseMetadata
import akka.grpc.GrpcSingleResponse
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Keep
import akka.stream.scaladsl.Source
import akka.Done
import akka.NotUsed
import io.grpc.netty.shaded.io.grpc.netty.GrpcSslContexts
import io.grpc.netty.shaded.io.grpc.netty.NegotiationType
import io.grpc.netty.shaded.io.grpc.netty.NettyChannelBuilder
import io.grpc.netty.shaded.io.netty.buffer.ByteBufAllocator
import io.grpc.netty.shaded.io.netty.handler.ssl.ApplicationProtocolConfig.Protocol
import io.grpc.netty.shaded.io.netty.handler.ssl.ApplicationProtocolConfig.SelectedListenerFailureBehavior
import io.grpc.netty.shaded.io.netty.handler.ssl.ApplicationProtocolConfig.SelectorFailureBehavior
import io.grpc.netty.shaded.io.netty.handler.ssl.ApplicationProtocolConfig
import io.grpc.netty.shaded.io.netty.handler.ssl.ApplicationProtocolNames
import io.grpc.netty.shaded.io.netty.handler.ssl.ApplicationProtocolNegotiator
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder
import io.grpc.netty.shaded.io.netty.handler.ssl.{ SslContext => ShadedNettySslContext }
import io.grpc.CallOptions
import io.grpc.MethodDescriptor

import java.util
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLSessionContext
import scala.annotation.nowarn
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.concurrent.Promise
import scala.util.Failure
import scala.util.Success

/**
 * INTERNAL API
 */
@InternalApi
object NettyClientUtils {

  private final class RefreshingShadedNettySslContext(javaSslContextProvider: () => SSLContext)
      extends ShadedNettySslContext {

    private val lock = new Object
    private var sslContexts: Option[(SSLContext, ShadedNettySslContext)] = None

    private def getContext(): ShadedNettySslContext = lock.synchronized {
      val freshJavaSslContext = javaSslContextProvider()
      sslContexts
        .collect { case (javaContext, nettyContext) if javaContext eq freshJavaSslContext => nettyContext }
        .getOrElse {
          val nettyContext = createNettySslContext(freshJavaSslContext)
          sslContexts = Some((freshJavaSslContext, nettyContext))
          nettyContext
        }
    }

    override def isClient: Boolean = getContext().isClient

    override def cipherSuites(): util.List[String] = getContext().cipherSuites()

    @nowarn("msg=deprecated")
    override def applicationProtocolNegotiator(): ApplicationProtocolNegotiator =
      getContext().applicationProtocolNegotiator()

    override def newEngine(byteBufAllocator: ByteBufAllocator): SSLEngine = getContext().newEngine(byteBufAllocator)

    override def newEngine(byteBufAllocator: ByteBufAllocator, s: String, i: Int): SSLEngine =
      getContext().newEngine(byteBufAllocator, s, i)

    override def sessionContext(): SSLSessionContext = getContext().sessionContext()
  }

  /**
   * INTERNAL API
   */
  @InternalApi
  def createChannel(settings: GrpcClientSettings, log: LoggingAdapter)(
      implicit ec: ExecutionContext,
      system: ActorSystem): InternalChannel = {

    @nowarn("msg=deprecated")
    var builder =
      NettyChannelBuilder
        // Used to be the way to pass the service name to the name resolver but
        // Since grpc-core 1.60.0 Netty does no longer seem to pass the authority along, so we do that
        // ourselves below (channel builder is not shared between different clients anyway).
        .forTarget(s"//${settings.serviceName}")
        .flowControlWindow(NettyChannelBuilder.DEFAULT_FLOW_CONTROL_WINDOW)
        // TODO avoid nameResolverFactory #1092, then 'nowarn' can be removed above
        .nameResolverFactory(
          new AkkaDiscoveryNameResolverProvider(
            settings.serviceDiscovery,
            settings.defaultPort,
            settings.serviceName,
            settings.servicePortName,
            settings.serviceProtocol,
            settings.resolveTimeout,
            settings.discoveryRefreshInterval))

    if (!settings.useTls)
      builder = builder.usePlaintext()
    else {
      builder = builder.negotiationType(NegotiationType.TLS)
      builder = settings.sslContext match {
        case Some(sslContext) =>
          builder.sslContext(createNettySslContext(sslContext))
        case None =>
          (settings.trustManager, settings.sslProvider, settings.sslContextProvider) match {
            case (None, None, None) =>
              builder
            case (None, None, Some(contextProvider)) =>
              builder.sslContext(new RefreshingShadedNettySslContext(contextProvider))

            case (tm, provider, _) =>
              val context = provider.fold(GrpcSslContexts.configure(SslContextBuilder.forClient()))(sslProvider =>
                GrpcSslContexts.configure(SslContextBuilder.forClient(), sslProvider))
              builder.sslContext((tm match {
                case None               => context
                case Some(trustManager) => context.trustManager(trustManager)
              }).build())
          }
      }
    }

    builder = settings.loadBalancingPolicy.map(builder.defaultLoadBalancingPolicy(_)).getOrElse(builder)
    builder = settings.overrideAuthority.map(builder.overrideAuthority(_)).getOrElse(builder)
    builder = settings.userAgent.map(builder.userAgent(_)).getOrElse(builder)
    builder = settings.channelBuilderOverrides(builder)

    val connectionAttempts = settings.loadBalancingPolicy match {
      case None | Some("pick_first") => settings.connectionAttempts
      case _                         =>
        // When loadbalancing we cannot count the individual attempts, so
        // the only options are '1' ('don't retry') or 'retry indefinitely'
        settings.connectionAttempts.flatMap {
          case 1 => Some(1)
          case _ => None
        }
    }

    val channel = builder.build()

    val channelReadyPromise = Promise[Unit]()
    val channelClosedPromise = Promise[Done]()

    ChannelUtils.monitorChannel(channelReadyPromise, channelClosedPromise, channel, connectionAttempts, log)

    channelReadyPromise.future.onComplete {
      case Success(()) =>
      // OK!
      case Failure(e) =>
        // shutdown is idempotent in ManagedChannelImpl
        channel.shutdown()
        channelClosedPromise.tryFailure(e)
    }

    if (settings.eagerConnection)
      channel.getState(true)

    new InternalChannel {
      override def shutdown() = channel.shutdown()
      override def done = channelClosedPromise.future

      override def invoke[I, O](
          request: I,
          headers: MetadataImpl,
          descriptor: MethodDescriptor[I, O],
          options: CallOptions): Future[O] = {
        val listener = new UnaryCallAdapter[O]
        val call = channel.newCall(descriptor, callOptionsWithDeadline(options, settings))
        call.start(listener, headers.toGoogleGrpcMetadata())
        call.sendMessage(request)
        call.halfClose()
        call.request(2)
        listener.future
      }

      override def invokeWithMetadata[I, O](
          request: I,
          headers: MetadataImpl,
          descriptor: MethodDescriptor[I, O],
          options: CallOptions): Future[GrpcSingleResponse[O]] = {
        val listener = new UnaryCallWithMetadataAdapter[O]
        val call = channel.newCall(descriptor, callOptionsWithDeadline(options, settings))
        call.start(listener, headers.toGoogleGrpcMetadata())
        call.sendMessage(request)
        call.halfClose()
        call.request(2)
        listener.future
      }

      override def invokeWithMetadata[I, O](
          source: Source[I, NotUsed],
          headers: MetadataImpl,
          descriptor: MethodDescriptor[I, O],
          streamingResponse: Boolean,
          options: CallOptions) = {
        val flow =
          createFlow(headers, descriptor, streamingResponse, callOptionsWithDeadline(options, settings))
        source.viaMat(flow)(Keep.right)
      }

      // TODO can't you derive the method name from the descriptor?
      private def createFlow[I, O](
          headers: MetadataImpl,
          descriptor: MethodDescriptor[I, O],
          streamingResponse: Boolean,
          options: CallOptions): Flow[I, O, Future[GrpcResponseMetadata]] =
        Flow.fromGraph(new AkkaNettyGrpcClientGraphStage(descriptor, channel, options, streamingResponse, headers))

    }
  }

  /**
   * Given a Java [[SSLContext]], create a Netty [[ShadedNettySslContext]] that can be used to build
   * a Netty HTTP/2 channel.
   */
  private def createNettySslContext(javaSslContext: SSLContext): ShadedNettySslContext = {
    import io.grpc.netty.shaded.io.netty.handler.ssl.ClientAuth
    import io.grpc.netty.shaded.io.netty.handler.ssl.IdentityCipherSuiteFilter
    import io.grpc.netty.shaded.io.netty.handler.ssl.JdkSslContext
    // See
    // https://github.com/netty/netty/blob/4.1/handler/src/main/java/io/netty/handler/ssl/JdkSslContext.java#L229-L309
    val apn = new ApplicationProtocolConfig(
      Protocol.ALPN,
      SelectorFailureBehavior.NO_ADVERTISE,
      SelectedListenerFailureBehavior.ACCEPT,
      ApplicationProtocolNames.HTTP_2)
    val context = new JdkSslContext(
      javaSslContext,
      /* boolean isClient */ true,
      /* Iterable<String> ciphers */ null, // use JDK defaults (null is accepted as indicated in constructor Javadoc)
      IdentityCipherSuiteFilter.INSTANCE,
      /* ApplicationProtocolConfig apn */ apn,
      ClientAuth.OPTIONAL, // server-only option, which is ignored as isClient=true (as indicated in constructor Javadoc)
      /* String[] protocols */ null, // use JDK defaults (null is accepted as indicated in constructor Javadoc)
      /* boolean startTls */ false)
    context
  }

  /**
   * INTERNAL API
   */
  @InternalApi def callOptions(settings: GrpcClientSettings): CallOptions =
    settings.callCredentials.map(CallOptions.DEFAULT.withCallCredentials).getOrElse(CallOptions.DEFAULT)

  /**
   * INTERNAL API
   */
  @InternalApi private[akka] def callOptionsWithDeadline(
      defaultOptions: CallOptions,
      settings: GrpcClientSettings): CallOptions =
    settings.deadline match {
      case d: FiniteDuration => defaultOptions.withDeadlineAfter(d.toMillis, TimeUnit.MILLISECONDS)
      case _                 => defaultOptions
    }
}
