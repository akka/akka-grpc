# Kubernetes

An Akka gRPC service can be deployed to a Kubernetes cluster just like any other HTTP-based application,
but if you want to make sure HTTP/2 is used across the board there are some things to consider.

There are 3 ways to use HTTP/2:

* HTTP/2 over TLS, using ALPN to detect support for HTTP/2 in the TLS handshake.
* HTTP/2 over plaintext TCP ('h2c'), starting with HTTP/1 and using the `Upgrade` mechanism to negotiate moving to HTTP/2.
* HTTP/2 over plaintext TCP ('h2c'), without negotiation. This assumes the client has prior knowledge that the server supports HTTP/2.

A straightforward way to expose a TCP endpoint outside the cluster is to create a Kubernetes `Service` of type `LoadBalancer`. Beyond that, Kubernetes supports many types of [Ingress Controllers](https://kubernetes.io/docs/concepts/services-networking/ingress-controllers/).
[GCE](https://github.com/kubernetes/ingress-gce/blob/master/README.md) and [NGINX](https://github.com/kubernetes/ingress-nginx/blob/master/README.md)
are the most widely known.

## LoadBalancer Service

By creating a service of type `LoadBalancer` you can expose your services using a direct TCP loadbalancer.
This means all features supported by the underlying implementation are available.

Using a `LoadBalancer` as the Ingress has some limitations, for example:

* you are restricted to 1 service per IP
* more advanced features such as TLS termination and path-based routing are not available

## NGINX Ingress

Of the three ways to use HTTP/2 presented at the beginning of this page, NGINX by default only supports 
`h2c` with negotiation via the `Upgrade` mechanism. Because the `Upgrade` mechanism is more
complicated than the other 2 approaches ("TLS", and h2c with "prior knowledge"), gRPC client support 
for h2c with the `Upgrade` mechanism is not ubiquitous. Also, the loadbalancer will use HTTP/1 rather 
than HTTP/2 for the connection from the loadbalancer to the service. While this may happen to work it is not optimal.

## GCE Ingress

GCE only supports HTTP/2 over TLS. This means you must configure GCE to use HTTPS both on the
'outside' and on the 'inside', and you will have to make sure your service is exposed over HTTPS
internally as well.

To make sure the GCE loadbalancer uses HTTP/2 to connect to your service you must
[use the `cloud.google.com/app-protocols` annotation](https://cloud.google.com/kubernetes-engine/docs/concepts/ingress-xlb#https_tls_between_load_balancer_and_your_application) the service:

```
apiVersion: v1
kind: Service
metadata:
  name: my-service-3
  annotations:
    cloud.google.com/app-protocols: '{"my-grpc-port":"HTTP2"}'
spec:
  type: NodePort
  selector:
    app: metrics
    department: sales
  ports:
  - name: my-grpc-port
    port: 443
    targetPort: 8443
```

By default the loadbalancer will accept any certificate, so your service can be configured with
a self-signed certificate.

The GCE load balancer only routes to pods that pass the health check. The default health check
is a request to '/' that expects a `200 OK` return value, so it might be helpful to add this to your
route.

## Google Cloud Endpoints

The Google cloud has a [Cloud Endpoints](https://cloud.google.com/endpoints) feature that
allows exposing a gRPC API in a more 'controlled' way: you can configure API key management,
authentication, monitoring quote/rate limiting, generate a 'developer portal' for your API's users
and much more. You need to provide your `.proto` definitions when creating the endpoint, and
still need to configure your own loadbalancer to expose this proxy to the outside.   
