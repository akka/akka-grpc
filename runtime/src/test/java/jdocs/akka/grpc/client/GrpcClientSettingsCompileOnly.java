/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.akka.grpc.client;

import akka.actor.ActorSystem;
import akka.discovery.ServiceDiscovery;
import akka.discovery.SimpleServiceDiscovery;
import akka.grpc.GrpcClientSettings;

import java.time.Duration;

public class GrpcClientSettingsCompileOnly {
    public static void sd() {

        //#simple
        GrpcClientSettings.create("localhost", 443);
        //#simple

        //#simple-programmatic
        GrpcClientSettings.create("localhost", 443)
                .withDeadline(Duration.ofSeconds(1))
                .withTls(false);
        //#simple-programmatic

        ActorSystem actorSystem = ActorSystem.create();
        //#provide-sd
        // An ActorSystem's default service discovery mechanism
        SimpleServiceDiscovery serviceDiscovery = ServiceDiscovery.get(actorSystem).discovery();

        GrpcClientSettings.create(
                "my-service",
                443,
                serviceDiscovery,
                Duration.ofSeconds(10)
        );
        //#provide-sd

        //#sd-settings
        // sys is an ActorSystem which is required for service discovery
        GrpcClientSettings.create(
                "project.WithConfigServiceDiscovery", actorSystem
        );
        //#sd-settings

    }
}
