/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.akka.grpc.client;

import akka.actor.ActorSystem;
import akka.discovery.Discovery;
import akka.discovery.ServiceDiscovery;
import akka.grpc.GrpcClientSettings;
import scala.Some;

import java.time.Duration;

public class GrpcClientSettingsCompileOnly {
    public static void sd() {

        ActorSystem actorSystem = ActorSystem.create();
        //#simple
        GrpcClientSettings.connectToServiceAt("localhost", 443, actorSystem);
        //#simple

        //#simple-programmatic
        GrpcClientSettings.connectToServiceAt("localhost", 443, actorSystem)
                .withDeadline(Duration.ofSeconds(1))
                .withTls(false);
        //#simple-programmatic

        ServiceDiscovery serviceDiscovery = Discovery.get(actorSystem).discovery();

        //#provide-sd
        // An ActorSystem's default service discovery mechanism
        GrpcClientSettings
                .usingServiceDiscovery("my-service", actorSystem)
                .withServicePortName("https"); // (optional) refine the lookup operation to only https ports
        //#provide-sd

        //#sd-settings
        // sys is an ActorSystem which is required for service discovery
        GrpcClientSettings.fromConfig(
                "project.WithConfigServiceDiscovery", actorSystem
        );
        //#sd-settings

    }
}
