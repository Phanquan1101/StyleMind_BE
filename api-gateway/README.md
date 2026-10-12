# StyleMind API Gateway

The local Gateway is the client-facing HTTP entry point at `http://localhost:8080`. It currently forwards the implemented public Identity API paths unchanged:

- `/api/**`
- `/.well-known/jwks.json`
- `/v3/api-docs` and `/v3/api-docs/**`

The Gateway does not issue or validate Identity JWTs. It forwards the bearer token and request unchanged, and Identity remains responsible for authentication, role checks, validation, errors and business behavior. Notification's `/internal/**` endpoints are intentionally not routed through the Gateway; Identity calls those through its Spring Cloud OpenFeign client on the private service network.

Commerce, Payment, Wardrobe Community and Stylist currently have service scaffolding but no business API contracts/routes. Add Gateway routes when those services implement approved public paths; do not route their APIs speculatively or change existing API prefixes here.

## Local run

For Docker Compose, start the Gateway with the dependencies needed by the current Auth flow:

```powershell
docker compose --project-name stylemind-task4 up -d --build postgres mailpit notification-service identity-service api-gateway
```

Compose publishes only `127.0.0.1:8080` for client API traffic. Backend application ports remain on the Compose network. Set Postman `baseUrl` to `http://localhost:8080`.

When starting applications from IntelliJ or Maven, run Gateway with Identity on port 8081. Gateway's target can be set with `STYLEMIND_IDENTITY_BASE_URL`; it defaults to `http://localhost:8081`.

## Verification

`mvn -f api-gateway/pom.xml test` exercises method/path/query/header/body forwarding, the public JWKS route, and confirms internal Notification paths are not routed. Identity's `NotificationEmailClientTests` exercise the Feign HTTP contract, exact `202` acceptance, 503/timeout mapping, and the no-retry client configuration.

Production Gateway, load balancing, TLS termination, service discovery and production service authentication remain TBD.
