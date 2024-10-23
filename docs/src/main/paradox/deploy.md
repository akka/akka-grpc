# Deployment

You can deploy an Akka gRPC application just like you would any other JVM-based project. For some general pointers on this topic, see @extref[the deployment section of the Akka documentation](akka:additional/deploying.html).

Remember that the cleartext HTTP/2 "h2c with prior knowledge" protocol is not compatible with HTTP/1.1, so if your infrastructure uses any proxies they must either understand this protocol or support generic TCP connections.

## Serve gRPC over HTTPS

To deploy your gRPC service over a HTTPS connection you will have to use an @apidoc[akka.http.(javadsl|scaladsl).HttpsConnectionContext] as described in the @extref[Akka-HTTP documentation](akka-http:server-side/server-https-support.html).

## Building Native Images

Building native images with Akka gRPC is supported. None of the functionality requires any special concerns, metadata
for the libraries Akka gRPC uses are provided out of the box.

For details about building native images with Akka in general see the @extref[Akka Documentation](akka:additional/native-image.html)
