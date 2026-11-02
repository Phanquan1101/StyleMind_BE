# StyleMind Backend

For service boundaries, code-layer responsibilities and the current folder tree of all six services plus API Gateway, see [Service Architecture and Folder Layout](docs/architecture/SERVICE_ARCHITECTURE_AND_LAYOUT.md).

Six independent Spring Boot services form the backend. Client-facing requests use the API Gateway at `http://localhost:8080`. The Gateway forwards currently implemented public Identity routes without changing their contracts; Identity calls Notification internally through Spring Cloud OpenFeign. Identity has Flyway migrations V1–V3 and implements EP-02 Register, Login, Refresh/Logout, Forgot Password, Profile, Address, and Manage Users flows. Other business APIs remain outside this scope.

Use [the current project context](docs/governance/CURRENT_PROJECT_CONTEXT.md), [approved business rules](docs/governance/CURRENT_BUSINESS_RULES.md), and [open implementation decisions](docs/governance/OPEN_IMPLEMENTATION_DECISIONS.md) as the active working baseline. Earlier specification packs are preserved under [the legacy archive](docs/archive/legacy-baseline/20261008T0855Z/README.md). Identity regression evidence is in [the release audit](docs/qa/identity-audit/20261008T1144-0700/IDENTITY_RELEASE_READINESS_AUDIT.md) and [its follow-up](docs/qa/identity-audit/20261008T1144-0700/IDENTITY_AUDIT_FOLLOW_UP.md).

The current task source is `C:\Users\FPTSHOP\Desktop\StyleMind_Task.xlsx`, sheet `Backlog` (read-only during this reconciliation). The user-facing Identity request set is also available as the [Postman collection](postman/StyleMind-Identity-Auth-Complete.postman_collection.json).

## Services and local ports

| Service | Responsibility | Port |
| --- | --- | ---: |
| `api-gateway` | Client-facing routing to implemented public APIs | 8080 |
| `identity-service` | Accounts and identity | 8081 |
| `commerce-service` | Catalog, cart, checkout, and order logic | 8082 |
| `payment-service` | Payment processing | 8083 |
| `wardrobe-community-service` | Wardrobe, sharing, community, and related chat context | 8084 |
| `stylist-service` | AI stylist | 8085 |
| `notification-service` | Notifications | 8086 |

## Prerequisites

- Java 21
- Apache Maven 3.6.3 or later

## Build and test

Each service has its own Maven project. From the repository root, run `mvn -f <service>/pom.xml verify` for each service. For example:

```powershell
mvn -f api-gateway/pom.xml verify
mvn -f notification-service/pom.xml verify
mvn -f identity-service/pom.xml verify
mvn -f commerce-service/pom.xml verify
mvn -f payment-service/pom.xml verify
mvn -f wardrobe-community-service/pom.xml verify
mvn -f stylist-service/pom.xml verify
```

## Run locally

From the repository root, run the command for the service you want to start:

```powershell
mvn -f api-gateway/pom.xml spring-boot:run
mvn -f identity-service/pom.xml spring-boot:run
mvn -f commerce-service/pom.xml spring-boot:run
mvn -f payment-service/pom.xml spring-boot:run
mvn -f wardrobe-community-service/pom.xml spring-boot:run
mvn -f stylist-service/pom.xml spring-boot:run
mvn -f notification-service/pom.xml spring-boot:run
```

Run one application per terminal session. Each application binds to its assigned port in the table above. When running apps from IntelliJ or `spring-boot:run`, set the Gateway target to Identity at `http://localhost:8081`; client and Postman requests use `http://localhost:8080`. Identity calls Notification internally at `http://localhost:8086` through Feign.
Host-run services need the matching `STYLEMIND_<SERVICE>_DB_URL`, `_DB_USERNAME`, and `_DB_PASSWORD` values and a PostgreSQL server reachable from the host. The Compose database is intentionally not published on a host port.

## Runtime configuration

All services accept the standard `SERVER_PORT` environment variable. When it is unset, the service uses its default local port.

| Service | Default port | Port override | Example run command |
| --- | ---: | --- | --- |
| `api-gateway` | 8080 | `SERVER_PORT` | `mvn -f api-gateway/pom.xml spring-boot:run` |
| `identity-service` | 8081 | `SERVER_PORT` | `mvn -f identity-service/pom.xml spring-boot:run` |
| `commerce-service` | 8082 | `SERVER_PORT` | `mvn -f commerce-service/pom.xml spring-boot:run` |
| `payment-service` | 8083 | `SERVER_PORT` | `mvn -f payment-service/pom.xml spring-boot:run` |
| `wardrobe-community-service` | 8084 | `SERVER_PORT` | `mvn -f wardrobe-community-service/pom.xml spring-boot:run` |
| `stylist-service` | 8085 | `SERVER_PORT` | `mvn -f stylist-service/pom.xml spring-boot:run` |
| `notification-service` | 8086 | `SERVER_PORT` | `mvn -f notification-service/pom.xml spring-boot:run` |

For example, Identity uses port 8081 by default. Run it with the first command. Stop it with `Ctrl+C` before using the override commands in the same PowerShell session:

```powershell
# Default port
mvn -f identity-service/pom.xml spring-boot:run

# Temporary override; stop with Ctrl+C, then remove the variable
$env:SERVER_PORT = "18081"
mvn -f identity-service/pom.xml spring-boot:run
Remove-Item Env:SERVER_PORT
```

Verify Gateway at `http://localhost:8080/actuator/health`. In Compose, backend service ports stay private; `scripts/verify-local-database-connectivity.ps1` uses a temporary curl container on the private Compose network to check each service's health and database component. If running a service directly from IntelliJ, its `/actuator/health` is available on its default port. A healthy application returns HTTP 200 with `{"status":"UP"}`.

## Run with Docker Compose

Prerequisites: Docker Desktop or Docker Engine with Docker Compose v2. The services require a local `.env` file; copy `.env.example` to `.env`, then replace every credential and secret placeholder with fresh local-only values. `.env` is ignored by Git.

From the repository root:

```powershell
if (-not (Test-Path .env)) { Copy-Item .env.example .env }
# Replace all credential and secret placeholders in .env before starting the stack.
docker compose --project-name stylemind-task4 config --quiet
docker compose --project-name stylemind-task4 up -d --build
docker compose --project-name stylemind-task4 ps
./scripts/verify-local-database-connectivity.ps1
```

Compose starts one PostgreSQL 18 container and creates one database and login role for each service. Each application receives only its own database URL and credentials. The PostgreSQL port is internal to the Compose network; host-run applications need their own reachable PostgreSQL URL and the matching service-specific credentials. The production database topology remains undecided.

Compose also starts `api-gateway` on `127.0.0.1:8080` and routes current public Identity API, JWKS and OpenAPI paths to Identity. Postman uses `http://localhost:8080`; backend service ports are not published to the host. Identity reaches Notification using the Compose service name through its Feign client. Notification's `/internal/**` routes are deliberately not exposed by the Gateway.

### First local Admin

The first Admin can be provisioned automatically by the **local Compose** Identity startup. In the ignored `.env`, set `STYLEMIND_BOOTSTRAP_ADMIN_ENABLED=true` and replace `STYLEMIND_BOOTSTRAP_ADMIN_EMAIL` and `STYLEMIND_BOOTSTRAP_ADMIN_PASSWORD` with local credentials. The password must satisfy the Register password length policy. This creates one `ACTIVE`, email-verified account with the `ADMIN` role only; this explicitly trusted local bootstrap does not send or create an OTP challenge. Its password is hashed with the Identity `PasswordEncoder`.

Provisioning is insert-only and idempotent: a matching eligible Admin is left unchanged; if the configured email already belongs to any other account, startup leaves that account unchanged and logs a generic warning. It never promotes, verifies, activates, or changes the password of an existing account. Changing the `.env` password does not rotate an already-created account. The feature requires the Compose-only `compose-local` profile and the explicit enable flag; it is disabled by default and is not the provisioning procedure for non-local environments. After the first local Admin is created, you can set the flag back to `false`; the account remains in the database volume.

To start only the current Auth/API runtime instead of every scaffold:

```powershell
docker compose --project-name stylemind-task4 up -d --build postgres mailpit notification-service identity-service api-gateway
```

The initialization SQL runs only when PostgreSQL first initializes an empty volume. `docker compose down` stops this Compose project but preserves its database volume. Do not remove the volume if you need to keep its local data.

To verify a restart, run `docker compose --project-name stylemind-task4 restart`, then repeat the health check script. Stop this stack and remove its containers and default network with:

```powershell
docker compose --project-name stylemind-task4 down
```

## Identity schema and Register

Identity has eleven business tables from migrations V1–V3: nine from V1, `email_verification_challenge` from V2, and `password_reset_challenge` from V3. The persistent local `stylemind_identity` Flyway history was last recorded as V1–V3 successful in the audit; this documentation pass did not connect to or change the database. The other five service databases are provisioned locally, but their target logical models are not business schemas and have no service migrations yet. Do not count `flyway_schema_history` as a business table.

Register and Forgot Password integration tests use Mailpit. The repository Compose default captures SMTP in Mailpit; a user-provided ignored `.env` may override it. The audit did not verify external SMTP delivery. See [Identity](identity-service/README.md), [Notification](notification-service/README.md), and the [six-service data model](database/six-service-model/ALL_DATABASES.md).

## API security, errors, and documentation

**Observed local security configuration:** Gateway forwards `/api/**`, `/.well-known/jwks.json`, and `/v3/api-docs` paths to Identity while preserving method, URL path, headers, and body. Gateway does not replace Identity security: protected `/api/**` routes still require the existing bearer JWT; only the configured public auth routes remain public. Identity's profile routes also require `CUSTOMER` or `ADMIN`, with ownership from JWT `sub`. Notification's exact POST routes `/internal/v1/email-verifications` and `/internal/v1/password-resets` require `X-StyleMind-Service-Token`, and the Gateway does not route them. Register and password reset do not issue JWTs.

Identity signs RS256 access JWTs and validates the signature, expiry/issued-at, configured issuer and audience, UUID subject/token ID, and role claims. Defaults are issuer `https://identity.stylemind.local`, audience `stylemind-api`, 15-minute access TTL and 7-day refresh TTL; signing uses the external key configured by `STYLEMIND_JWT_PRIVATE_KEY_PATH`. Its public key endpoint is `/.well-known/jwks.json`. The other resource services still use their configured JWK-set URI/algorithm and do not yet have Identity-equivalent issuer/audience validation or `roles`-to-authority mapping. These are observed implementation facts, not evidence that authorization is complete across all services.

Errors use `application/problem+json` with a stable `code`, `timestamp`, and `correlationId`; unexpected failures return a generic detail. Request logging uses `X-Correlation-Id` (safe incoming values are preserved, otherwise one is generated) and records service, method, path, status, and duration. Request bodies, authorization headers, and tokens are not logged.

Identity OpenAPI JSON is available through Gateway at `http://localhost:8080/v3/api-docs`. Backend service OpenAPI and health endpoints remain inside the Compose network; the diagnostic script checks service health without publishing backend ports. Swagger UI is not included. The shared internal token is a local configuration choice; production service authentication and topology remain TBD.

Production key provisioning/rotation, service-to-service trust, deployment topology and provider operations remain TBD. The local issuer/audience defaults and JWKS network address are documented in the Identity README; they do not decide production topology.
