# Kubernetes

An Akka gRPC service can be deployed to a Kubernetes cluster just like any other HTTP-based application,
but if you want to make sure HTTP/2 is used across the board there are some things to consider.

There are 3 ways to use HTTP/2:

* With TLS
* Without TLS ('h2c'), using the `Upgrade` mechanism to negotiate between using HTTP/1 and HTTP/2
* Without TLS ('h2c'), without negotiation (which assumes the client has prior knowledge that the server supports HTTP/2)

Kubternetes supports many types of [Ingress Controllers](https://kubernetes.io/docs/concepts/services-networking/ingress-controllers/),
but [GCE](https://git.k8s.io/ingress-gce/README.md) and [nginx](https://git.k8s.io/ingress-nginx/README.md)
seem to be the most widely known.

## LoadBalancer Service

By creating a service of type `LoadBalancer` you can expose your services using a direct TCP loadbalancer.
This means all features supported by the underlying implementation are available.

Limitations of this approach are that you are restricted to 1 service per IP, and
more advanced features such as TLS termination and path-based routing are not available.

## Nginx Ingress

Nginx, by default, will only support h2c via the `Upgrade` mechanism. Because this mechanism is more
complicated than the other 2 approaches, client support for this mechanism is not ubiquitous. Also, the
loadbalancer will use HTTP/1 rather than HTTP/2 to the service. While this may happen to work it is
not optimal.

## GCE Ingress

GCE only supports HTTP/2 over TLS. This means you must configure GCE to use HTTPS both on the
'outside' and on this 'inside', and you will have to make sure your service is exposed over HTTPS
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