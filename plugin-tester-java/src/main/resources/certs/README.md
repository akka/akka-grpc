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
