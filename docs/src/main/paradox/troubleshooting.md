# Troubleshooting

## Client

```
java.lang.IllegalArgumentException: Could not find Jetty NPN/ALPN or Conscrypt as installed JDK providers
```

Perhaps surprisingly, this error may be thrown when connecting to an insecure
gRPC server with a client that is configured with TLS enabled.
