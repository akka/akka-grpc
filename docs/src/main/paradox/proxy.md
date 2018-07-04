# Running Akka gRPC behind a proxy

As gRPC uses HTTP/2 as it's underlying transport any proxy supporting HTTP/2 should in theory work fine as a proxy
in front of the Akka gRPC server.

These proxies has been verified to work in between the Akka gRPC client and server.

## Envoy

[Envoy proxy](www.envoyproxy.io) has [specific support for gRPC](https://www.envoyproxy.io/docs/envoy/latest/intro/arch_overview/grpc.html).
A sample configuration to proxy gRPC can be found among the [Envoy examples in their github repository](https://github.com/envoyproxy/envoy/blob/3f59fb5c0f6554f8b3f2e73ab4c1437a63d42668/examples/grpc-bridge/config/s2s-grpc-envoy.yaml)

Note that the TLS connection will always have to be terminated at Envoy, so the options to run gRPC behind Envoy are the following:

* [gRPC client] &rarr; unencrypted &rarr; [Envoy] &rarr; unencrypted &rarr; [gRPC server] - this is not recommended for production systems for security reasons
* [gRPC client] &rarr; TLS &rarr; [Envoy] &rarr; unencrypted &rarr; [gRPC server] - TLS is terminated in Envoy
* [gRPC client] &rarr; TLS &rarr; [Envoy] &rarr; TLS &rarr; [gRPC server] - TLS is terminated in Envoy and then a new TLS connection is made from Envoy to the gRPC server

Envoy 1.7 can not handle server streaming (single request and streaming response) because it is missing
support for half closed [Envoy issue #3724](https://github.com/envoyproxy/envoy/issues/3724).

For such methods you can work around the missing support by making
gRPC methods bidirectional streaming both ways and keep the source from the client running
(this can be done with `prefixAndTail(1)` to capture the first element and then the rest as a new `Source` which can be
kept running with the `ignore` sink).