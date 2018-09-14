/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.akka.grpc.client;

import akka.actor.ActorSystem;
import akka.discovery.ServiceDiscovery$;
import akka.discovery.SimpleServiceDiscovery;
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

        SimpleServiceDiscovery serviceDiscovery= ServiceDiscovery$.MODULE$.apply(actorSystem).discovery();

        //#provide-sd
        // An ActorSystem's default service discovery mechanism
        GrpcClientSettings.connectTo(
                "my-service",
                serviceDiscovery,
                actorSystem
        );
        //#provide-sd

        //#sd-settings
        // sys is an ActorSystem which is required for service discovery
        GrpcClientSettings.fromConfig(
                "project.WithConfigServiceDiscovery", actorSystem
        );
        //#sd-settings

    }
}
