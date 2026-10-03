# StyleMind Backend

Six independent Spring Boot applications form the initial backend skeleton. This bootstrap contains infrastructure health checks only; business APIs and database connections are not implemented.

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

Each service has its own Maven project. From the repository root, run `mvn -f <service>/pom.xml clean verify` for each service. For example:

```powershell
mvn -f identity-service/pom.xml clean verify
mvn -f commerce-service/pom.xml clean verify
mvn -f payment-service/pom.xml clean verify
mvn -f wardrobe-community-service/pom.xml clean verify
mvn -f stylist-service/pom.xml clean verify
mvn -f notification-service/pom.xml clean verify
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
