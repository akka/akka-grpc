# Some notes about the certs in this directory

Self-signing sample CA in rootCA.*, CA cert secret: "secret"
Server cert for localhost signed by rootCA in localhost-server.*, no password for private key
Client cert for a client to connect in localhost-client.*, no password for private key

Certs used by `MtlsGreeterServer`.

## Hitting the server from curl:

Hit it with no extras, will fail because server cert is self signed:
`curl -v https://localhost:8443/Test`

Accept root CA for server cert, server denies access because no client cert:
`curl -v --cacert ./rootCA.crt https://localhost:8443/Test`

Accept root CA for server cert, pass a client cert the server doesn't know, TLS error
(exact error differs depending on underlying TLS impl):
`curl -v --key client1.key --cert bad-client.crt --cacert ./rootCA.crt https://localhost:8443/Test`

Accept root CA for server cert, pass a cert the server accepts, 404 because no such route:
 `curl -v --key client1.key --cert client1.crt --cacert ./rootCA.crt https://localhost:8443/Test`

## Re-creating the certs

Creating our own CA, specifying 'secret' as password and non-important values for the other properties when prompted,:

```shell
openssl req -x509 -sha256 -days 36500 -newkey rsa:2048 -keyout rootCA.key -out rootCA.crt
```

Server key and key signing request, `localhost` for Common Name when prompted, empty challenge password:

```shell
openssl req -newkey rsa:2048 -nodes -keyout localhost-server.key -out localhost-server.csr
```

Sign server cert with our own CA (note that `domain.ext` is a manually created text file), use CA password `secret` from above:

```shell
openssl x509 -req -CA rootCA.crt -CAkey rootCA.key -in localhost-server.csr -out localhost-server.crt -days 36500 -CAcreateserial -extfile domain.ext
```

We now have localhost-server.crt and localhost-server.key for the server, and rootCA.crt for verifying that keypair.

Same for client, no password, set a commmon name, but value isn't really important:

```shell
openssl req -newkey rsa:2048 -nodes -keyout client1.key -out client1.csr
```

Sign it:
```shell
openssl x509 -req -CA rootCA.crt -CAkey rootCA.key -in client1.csr -out client1.crt -days 36500 -CAcreateserial
```

We now have client1.crt and client1.key for the client.

Additional non CA-signed certs for testing key pair that the server does not agree to:

```shell
openssl req -x509 -nodes -days 36500 -newkey rsa:2048 -keyout bad-client.key -out bad-client.crt
```

