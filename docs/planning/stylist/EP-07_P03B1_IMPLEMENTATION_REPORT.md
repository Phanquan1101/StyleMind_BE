# EP-07 P03-B1 — implementation and disposable verification

**Status: P03-B1 VERIFIED in the approved shop/manual intake scope. P03 remains PARTIAL; US-07.01 and EP-07 are not complete.**

## Implemented contract

- `POST /api/v1/stylist/sessions/{session_id}/requests` stores a validated request, optional USER message, and all constraints in one transaction. It returns `201`, `Location`, `Cache-Control: no-store`, status `RECEIVED`, and `generation_started: false`.
- `GET /api/v1/stylist/requests/{request_id}` returns the saved request and constraints only when the session owner matches the JWT subject. Missing and foreign requests both return `404 RESOURCE_NOT_FOUND`. CUSTOMER and ADMIN are both self-only; ADMIN is not a bypass.
- A supplied `source_message_id` must refer to a USER message in the same session. When `message` is supplied, the service appends a USER history message and references it from the new request. Structured-only requests create no synthetic message.
- Request parsing rejects malformed/non-object JSON, duplicate/unknown fields, trailing JSON, invalid field types, non-integral/overflow/negative VND, unsupported/duplicate constraints, hard budget without an amount, and requests without meaningful text/context. The request body is limited to 32 KiB. User-scoped paths and Problem Details use `Cache-Control: no-store`.
- V2 is new and leaves V1 unchanged. It creates exactly `styling_request` and `styling_constraint`, local Stylist foreign keys, checks for `RECEIVED`, budget and constraint shape, and indexes for session/request lookups. No cross-service key or owned-context field/table was added.

## Verification

| Command | Result |
|---|---|
| `mvn -B -ntp -f stylist-service/pom.xml -DskipTests compile` | BUILD SUCCESS |
| `mvn -B -ntp -f stylist-service/pom.xml -Dtest=StylistSessionApiIntegrationTests test` | 23 tests, 0 failures, 0 errors, 0 skipped |
| `mvn -B -ntp -f stylist-service/pom.xml test` | BUILD SUCCESS; 119 tests, 0 failures, 0 errors, 0 skipped |

The full suite includes 52 P02 engine tests, the P04.1 retrieval/Qdrant tests (including 2 live disposable Qdrant Testcontainers integration tests), 23 session/request PostgreSQL integration tests, 2 strict P03-A parser tests, and 13 application/security/OpenAPI tests. The 23-test session integration class includes the prior P03-A tests plus seven P03-B1 scenarios for create/read, A/B/Admin isolation, USER-message ownership, closed sessions, strict validation, rollback and body/media limits.

Docker Engine was Linux 28.5.2. The Stylist integration suite started `postgres:18-alpine` (PostgreSQL 18.6) in a Testcontainers container with a random mapped port. Flyway applied only source V1 and V2 to the disposable database; the migration test confirmed exactly the four expected tables total and exactly the approved V2 indexes. Testcontainers/Ryuk removed the temporary container after the run. No Compose project, persistent database, shared Qdrant, or other service was accessed or migrated.

## Handover limits

- The Postman collection/environment and run guide are supplied and their JSON structure was parsed locally. Postman/Newman HTTP execution through Gateway was not run.
- No persistent migration was applied. A runtime Postman exercise requires a separately disposable Stylist database already at V2; do not point it at the persistent local/team database for this slice.
- The API only records a request as `RECEIVED`. It does not queue or run generation, create an assistant response, access profile/catalog/wardrobe, verify owned garments, call Qdrant/Neo4j/provider/LLM, or implement recovery/retry.
- The owner-approved P03-B1 is only a partial P03 slice. Owned context remains blocked on a trusted Wardrobe contract. Broader generation and acceptance decisions remain open.
- The Stylist `SecurityConfiguration.java` fingerprint was refreshed after reviewing its P03-B1 role matcher and passing the HTTP security tests. Planning guard remains non-green due the pre-existing Commerce source/fingerprint drift. No Commerce fingerprint or `reviewed_head` was changed.

## Files added or changed by P03-B1

- `stylist-service/src/main/resources/db/migration/V2__create_styling_request_tables.sql`
- `stylist-service/src/main/java/com/stylemind/stylist/controller/request/StylingRequestController.java`
- `stylist-service/src/main/java/com/stylemind/stylist/dto/request/`
- `stylist-service/src/main/java/com/stylemind/stylist/entity/request/StylingRequestEntity.java`
- `stylist-service/src/main/java/com/stylemind/stylist/entity/constraint/StylingConstraintEntity.java`
- `stylist-service/src/main/java/com/stylemind/stylist/mapper/request/StylingRequestMapper.java`
- `stylist-service/src/main/java/com/stylemind/stylist/repository/request/StylingRequestRepository.java`
- `stylist-service/src/main/java/com/stylemind/stylist/repository/constraint/StylingConstraintRepository.java`
- `stylist-service/src/main/java/com/stylemind/stylist/service/request/` (interface, parser, bounded JSON converter)
- `stylist-service/src/main/java/com/stylemind/stylist/service/impl/request/StylingRequestServiceImpl.java`
- `stylist-service/src/main/java/com/stylemind/stylist/errors/ErrorCode.java`
- `stylist-service/src/main/java/com/stylemind/stylist/security/SecurityConfiguration.java`
- `stylist-service/src/main/java/com/stylemind/stylist/security/SessionRequestPolicyFilter.java`
- `stylist-service/src/main/java/com/stylemind/stylist/config/StylistWebConfiguration.java`
- `stylist-service/src/main/java/com/stylemind/stylist/repository/session/StylistSessionRepository.java`
- `stylist-service/src/main/java/com/stylemind/stylist/repository/message/StylistMessageRepository.java`
- `stylist-service/src/test/java/com/stylemind/stylist/session/StylistSessionApiIntegrationTests.java`
- `stylist-service/postman/StyleMind-Stylist-P03B1.postman_collection.json`
- `stylist-service/postman/StyleMind-Stylist-P03B1.postman_environment.json`
- `stylist-service/postman/P03B1_RUN_GUIDE.md`
- `docs/planning/stylist/EP-07_P03B_CONTRACT_PROPOSAL.md`
- `docs/planning/stylist/EP-07_EXECUTION_MANIFEST.json`
- `docs/planning/stylist/README.md`
