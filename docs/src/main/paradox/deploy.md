# Deployment

You can deploy an Akka gRPC application just like you would any other JVM-based project. For some general pointers on this topic, see [the deployment section of the Akka documentation](https://doc.akka.io/docs/akka/current/additional/deploying.html).

Remember that the cleartext HTTP/2 "h2c with prior knowledge" protocol is not compatible with HTTP/1.1, so if your infrastructure uses any proxies they must either understand this protocol or support generic TCP connections.

## Serve gRPC over HTTPS

To deploy your gRPC service over a HTTPS connection you will have to use an @apidoc[akka.http.(javadsl|scaladsl).HttpsConnectionContext] as described in the [Akka-HTTP documentation](https://doc.akka.io/docs/akka-http/10.1/server-side/server-https-support.html).

## Example: Kubernetes

As an example, [here](https://developer.lightbend.com/start/?group=akka&project=akka-grpc-sample-kubernetes-scala) is a complete project consisting of two applications (a gRPC service and an HTTP service that consumes the gRPC service) that can be deployed together in Kubernetes.
