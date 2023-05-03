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

A JSK store can be prepared with the right contents, or created on the fly from cert files in some location the server can access for reading, 
in this sample we use cert files available on the classpath. The server is set up with its own private key and cert as well as a trust 
store with a CA to trust client certificates from:

Scala
:  @@snip [MtlsGreeterServer.scala](/plugin-tester-scala/src/main/scala/example/myapp/helloworld/MtlsGreeterServer.scala) { #full-server }

Java
:  @@snip [MtlsGreeterServer.java](/plugin-tester-java/src/main/java/example/myapp/helloworld/MtlsGreeterServer.java) { #full-server }

When run the server will only accept client connections that use a keypair that it considers valid, other connections will be denied
and fail with a TLS protocol error.


## Setting the client up

In the client, the trust store must be set up to trust the server cert, in our sample it is signed with the same CA as the
server. The key store contains the public and private key for the client:

Scala
:  @@snip [MtlsGreeterClient.scala](/plugin-tester-scala/src/main/scala/example/myapp/helloworld/MtlsGreeterClient.scala) { #full-client }

Java
:  @@snip [MtlsGreeterClient.java](/plugin-tester-java/src/main/java/example/myapp/helloworld/MtlsGreeterClient.java) { #full-client }

A client presenting a keypair will be able to connect to both servers requiring regular HTTPS gRPC services and mTLS servers that
accept the client certificate.