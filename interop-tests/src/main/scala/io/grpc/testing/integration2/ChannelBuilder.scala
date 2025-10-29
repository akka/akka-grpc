/*
 * Copyright (C) 2018-2025 Lightbend Inc. <https://www.lightbend.com>
 */

package io.grpc.testing.integration2

import io.grpc.ManagedChannelBuilder
import io.grpc.internal.testing.TestUtils
import io.grpc.netty.{ GrpcSslContexts, NegotiationType, NettyChannelBuilder }
import io.netty.handler.ssl.SslContext

object ChannelBuilder {

  /**
   *  Reimplementation of the private io.grpc.testing.integration.TestServiceClient$Tester#createChannelBuilder
   */
  def create(settings: Settings): ManagedChannelBuilder[NettyChannelBuilder] =
    if (settings.useAkkaHttp) {
      // TODO: here comes the akka-http based channel (when ready)
      throw new RuntimeException("Not implemented")
    } else {
      val sslContext: SslContext = {
        if (settings.useTestCa) {
          try GrpcSslContexts.forClient.trustManager(TestUtils.loadCert("ca.pem")).build
          catch {
            case ex: Exception => throw new RuntimeException(ex)
          }
        } else null
      }

      val builder =
        NettyChannelBuilder
          .forAddress(settings.serverHost, settings.serverPort)
          .flowControlWindow(65 * 1024)
          .negotiationType(if (settings.useTls) NegotiationType.TLS else NegotiationType.PLAINTEXT)
          .sslContext(sslContext)

      if (settings.serverHostOverride != null)
        builder.overrideAuthority(settings.serverHostOverride)

      builder
    }
}
