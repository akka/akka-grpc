# Mutual authentication with TLS

Mutual or mTLS means that just like how a client will only connect to servers with valid certificates, the server will
also verify the client certificate and only allow connections if the client key pair is accepted by the server. This is
useful for example in microservices where only other known services are allowed to interact with a service, and public access 
should be denied.

For mTLS to work the server must be set up with a keystore containing the CA (certificate authority) public key used to sign the individual certs
for clients that are allowed to access the server, just like how in a regular TLS/HTTPS scenario the client must be able to
verify the server certificate. 

Since the CA is what controls what clients can access a service, it is likely an organisation or service specific CA rather
than a normal public one like what you use for a public web server.

## Setting the server up

A JKS store can be prepared with the right contents, or we can create it on the fly from certificate files in some location the server as read access to. 
In this example we use certificate and key files in the PEM format available from the file system and use the Akka HTTP convenience factories to load them.

The server is set up with its own private key and certificate as well as with a trust 
store containing the certificate authority (CA) to trust client certificates from:

Scala
:  @@snip [MtlsGreeterServer.scala](/plugin-tester-scala/src/main/scala/example/myapp/helloworld/MtlsGreeterServer.scala) { #full-server }

Java
:  @@snip [MtlsGreeterServer.java](/plugin-tester-java/src/main/java/example/myapp/helloworld/MtlsGreeterServer.java) { #full-server }

When run the server will only accept client connections that use a keypair that it considers valid, other connections will be denied
and fail with a TLS protocol error.

It is possible to rotate the certificates without restarting the service, see the @extref[Akka HTTP documentation](akka-http:server-side/server-https-support.html#rotating-certificates). 


## Setting the client up

In the client, the trust store must be set up to trust the server certificate, in our example it is signed with the same CA as the
server:

Scala
:  @@snip [MtlsGreeterClient.scala](/plugin-tester-scala/src/main/scala/example/myapp/helloworld/MtlsGreeterClient.scala) { #full-client }

Java
:  @@snip [MtlsGreeterClient.java](/plugin-tester-java/src/main/java/example/myapp/helloworld/MtlsGreeterClient.java) { #full-client }

A client presenting a keypair will be able to connect to both servers requiring regular HTTPS gRPC services and mTLS servers that
accept the client certificate.

It is possible to rotate the certificates without restarting the service, by using the Akka HTTP `SSLContextFactory.refreshingSSLContextProvider`. Note however that picking up the new certs works in concert with the connection handling
of the underlying client, new certificates are not picked up until a new connection is made and connections are kept alive
for a relatively long time by default (30 minutes without any requests sent with the default Netty based client).

For more details about the Akka HTTP certificate utilities see @extref[Akka HTTP documentation](akka-http:client-side/client-https-support.html#convenient-loading-of-key-and-certificate).

## Further limiting of access using client certificate identities

In addition to requiring a trusted certificate it is possible to further limit access based on the identity present in
the trusted client certificate ip or dns SAN (Subject Alternative Names) or CN (Common Name).

This is done by wrapping the service handler in the `requireClientCertificateIdentity`:

Scala
:  @@snip [AuthenticatedGreeterServer.scala](/plugin-tester-scala/src/main/scala/example/myapp/helloworld/GreeterServerWithClientCertIdentity.scala) { #with-mtls-cert-identity }

Java
:  @@snip [AuthenticatedGreeterServer.java](/plugin-tester-java/src/main/java/example/myapp/helloworld/GreeterServerWithClientCertIdentity.java) { #with-mtls-cert-identity }


FIXME provide sample snippets


