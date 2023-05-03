# Mutual authentication with TLS

Mutual or mTLS means that just like how a client will only connect to servers with valid certificates, the server will
also verify the client certificate and only allow connections if the client key pair is accepted by the server. This is
useful for example in microservices where only other known services are allowed to interact with a service, and public access 
should be denied.

