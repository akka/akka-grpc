# Deployment

You can deploy an Akka gRPC application just like you would any other JVM-based project. For some general pointers on this topic, see [the deployment section of the Akka documentation](https://doc.akka.io/docs/akka/current/additional/deploy.html).

Remember that the unencrypted HTTP2 'h2c with preexisting knowledge' protocol is not compatible with HTTP/1.1, so if your infrastructure uses any proxies they must either understand this protocol or support generic TCP connections.

## Example: Kubernetes

As an example, [here](https://developer.lightbend.com/start/?group=akka&project=akka-grpc-sample-kubernetes-scala) is a complete project consisting of 2 applications (a gRPC service and an HTTP service that consumes the gRPC service) that can be deployed together in Kubernetes.