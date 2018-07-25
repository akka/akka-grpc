/*
 * Copyright (C) 2018 Lightbend Inc. <https://www.lightbend.com>
 */

package jdocs.akka.grpc.client;

import akka.actor.ActorSystem;
import akka.grpc.GrpcClientSettings;

import java.time.Duration;

public class GrpcClientSettingsCompileOnly {
    public static void sd() {

        ActorSystem actorSystem = ActorSystem.create();
        //#simple
        GrpcClientSettings.create("localhost", 443, actorSystem);
        //#simple

        //#simple-programmatic
        GrpcClientSettings.create("localhost", 443, actorSystem)
                .withDeadline(Duration.ofSeconds(1))
                .withTls(false);
        //#simple-programmatic

        //#provide-sd
        // An ActorSystem's default service discovery mechanism
        GrpcClientSettings.create(
                "my-service",
                443,
                "config", // config based service discovery must be defined in the ActorSystems' config
                actorSystem
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
