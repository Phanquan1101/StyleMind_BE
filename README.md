# StyleMind Backend

Six independent Spring Boot applications form the backend. Local PostgreSQL connectivity is configured for each service. Identity has Flyway migrations V1–V3 and implements EP-02 Register, Login, Refresh/Logout, and Forgot Password flows. Other business APIs remain outside this scope.

Use [the current project context](docs/governance/CURRENT_PROJECT_CONTEXT.md), [approved business rules](docs/governance/CURRENT_BUSINESS_RULES.md), and [open implementation decisions](docs/governance/OPEN_IMPLEMENTATION_DECISIONS.md) as the active working baseline. Earlier specification packs are preserved under [the legacy archive](docs/archive/legacy-baseline/20261008T0855Z/README.md). Identity regression evidence is in [the release audit](docs/qa/identity-audit/20261008T1144-0700/IDENTITY_RELEASE_READINESS_AUDIT.md) and [its follow-up](docs/qa/identity-audit/20261008T1144-0700/IDENTITY_AUDIT_FOLLOW_UP.md).

The current task source is `C:\Users\FPTSHOP\Desktop\StyleMind_Task.xlsx`, sheet `Backlog` (read-only during this reconciliation). The user-facing Identity request set is also available as the [Postman collection](postman/StyleMind-Identity-Auth-Complete.postman_collection.json).

## Services and local ports

| Service | Responsibility | Port |
| --- | --- | ---: |
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
mvn -f identity-service/pom.xml spring-boot:run
mvn -f commerce-service/pom.xml spring-boot:run
mvn -f payment-service/pom.xml spring-boot:run
mvn -f wardrobe-community-service/pom.xml spring-boot:run
mvn -f stylist-service/pom.xml spring-boot:run
mvn -f notification-service/pom.xml spring-boot:run
```

Run only one command per terminal session. Each service binds to its assigned port in the table above.
Host-run services need the matching `STYLEMIND_<SERVICE>_DB_URL`, `_DB_USERNAME`, and `_DB_PASSWORD` values and a PostgreSQL server reachable from the host. The Compose database is intentionally not published on a host port.

## Runtime configuration

All services accept the standard `SERVER_PORT` environment variable. When it is unset, the service uses its default local port.

| Service | Default port | Port override | Example run command |
| --- | ---: | --- | --- |
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

Verify a running service at `http://localhost:<port>/actuator/health`. A healthy application returns HTTP 200 with `{"status":"UP"}`.

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

The initialization SQL runs only when PostgreSQL first initializes an empty volume. `docker compose down` stops this Compose project but preserves its database volume. Do not remove the volume if you need to keep its local data.

To verify a restart, run `docker compose --project-name stylemind-task4 restart`, then repeat the health check script. Stop this stack and remove its containers and default network with:

```powershell
docker compose --project-name stylemind-task4 down
```

## Identity schema and Register

Identity has eleven business tables from migrations V1–V3: nine from V1, `email_verification_challenge` from V2, and `password_reset_challenge` from V3. The persistent local `stylemind_identity` Flyway history was last recorded as V1–V3 successful in the audit; this documentation pass did not connect to or change the database. The other five service databases are provisioned locally, but their target logical models are not business schemas and have no service migrations yet. Do not count `flyway_schema_history` as a business table.

Register and Forgot Password integration tests use Mailpit. The repository Compose default captures SMTP in Mailpit; a user-provided ignored `.env` may override it. The audit did not verify external SMTP delivery. See [Identity](identity-service/README.md), [Notification](notification-service/README.md), and the [six-service data model](database/six-service-model/ALL_DATABASES.md).

## API security, errors, and documentation

**Observed local security configuration:** APIs under `/api/**` require a valid bearer JWT, except the exact POST routes for Register, email verification, Forgot Password, Login, Refresh and Logout. Identity's exact profile routes `/api/v1/me` and `/api/v1/me/style-profile` also require `CUSTOMER` or `ADMIN`; the resource owner is JWT `sub`. Notification's exact POST routes `/internal/v1/email-verifications` and `/internal/v1/password-resets` require `X-StyleMind-Service-Token`. Other application paths are denied. `/actuator/health` and `/v3/api-docs` are public. Register and password reset do not issue JWTs. There is no API Gateway in the local Compose stack; call the service on its own port.

Identity signs RS256 access JWTs and validates the signature, expiry/issued-at, configured issuer and audience, UUID subject/token ID, and role claims. Defaults are issuer `https://identity.stylemind.local`, audience `stylemind-api`, 15-minute access TTL and 7-day refresh TTL; signing uses the external key configured by `STYLEMIND_JWT_PRIVATE_KEY_PATH`. Its public key endpoint is `/.well-known/jwks.json`. The other resource services still use their configured JWK-set URI/algorithm and do not yet have Identity-equivalent issuer/audience validation or `roles`-to-authority mapping. These are observed implementation facts, not evidence that authorization is complete across all services.

Errors use `application/problem+json` with a stable `code`, `timestamp`, and `correlationId`; unexpected failures return a generic detail. Request logging uses `X-Correlation-Id` (safe incoming values are preserved, otherwise one is generated) and records service, method, path, status, and duration. Request bodies, authorization headers, and tokens are not logged.

Each service publishes OpenAPI JSON at `http://localhost:<port>/v3/api-docs`, with service-specific security schemes. Swagger UI is not included. The shared internal token is a local configuration choice; production service authentication and topology remain TBD.

Production key provisioning/rotation, service-to-service trust, deployment topology and provider operations remain TBD. The local issuer/audience defaults and JWKS network address are documented in the Identity README; they do not decide production topology.
