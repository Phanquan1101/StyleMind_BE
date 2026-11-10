# EP-07 P03-A — Session and history handover

Implementation began 2026-10-29; review/handover finalized 2026-10-31, Asia/Bangkok. Branch: `feature/ep-07-ai-stylist`.

## Authorization and boundary

Owner Prompt 08 explicitly authorizes P03-A only: create a Stylist session for the JWT subject, read that session’s message history, persist those two records, and provide tests/Postman artifacts. It also corrects product scope to exactly one women’s TOP and one BOTTOM per outfit; P02 code and fixtures now enforce that correction. P03-A does not complete P03, US-07.01, or EP-07. No other service source or persistent database was changed.

Workbook `StyleMind_Task.xlsx` was read-only; R93–R96 were reviewed. This slice contributes the session/message portion of R93, session creation/history portions of R94, and ownership/invalid-input portions of R96. It does not complete any backlog row or US.

## Implemented contract

- `POST /api/v1/stylist/sessions` (Gateway: `/stylist/api/v1/stylist/sessions`) accepts an optional JSON `title`. Omitted/null means no title; a string is trimmed and must be 1–100 Unicode code points without control characters. Unknown/duplicate properties, invalid type, unsupported media type and request bodies above 32 KiB are rejected. Owner, UUID and time come from the server/JWT.
- Success is HTTP 201 with direct DTO fields `session_id`, `title`, `opened_at`, `closed_at`; `closed_at` is null. `Location` points through the configured Gateway prefix. Responses use `Cache-Control: no-store`.
- `GET /api/v1/stylist/sessions/{session_id}/messages` accepts optional `limit` (default 20, range 1–50) and opaque `before` cursor; unknown or repeated query keys, malformed UUID/cursor and GET bodies are rejected. It returns `items` and nullable `next_cursor`, newest first by `sent_at DESC, stylist_message_id DESC`.
- JWT validation requires RS256, configured issuer/audience, valid timestamps, UUID `sub`/`jti`, and CUSTOMER or ADMIN role. Both roles are self-only. Missing and foreign sessions return the same 404. Error responses use Problem Details and avoid secrets.
- New sessions have empty history. There is no public message write, request admission or fake assistant response.

## Schema

New `stylist-service/src/main/resources/db/migration/V1__create_stylist_session_message_tables.sql` creates exactly two tables:

- `stylist_session`: UUID primary key, logical Identity `customer_id` with no cross-service FK, nullable title, opened/closed `timestamptz`, close-time check and owner/session index.
- `stylist_message`: UUID primary key, internal session FK, USER/ASSISTANT check, content and sent time, unique `(stylist_session_id, stylist_message_id)`, and stable pagination index `(stylist_session_id, sent_at DESC, stylist_message_id DESC)`.

Flyway is configured with clean disabled, no baseline-on-migrate, and validation enabled; Hibernate schema auto-create/update is not enabled. No audit, request, recommendation or cross-service table was added. The migration ran only against a Testcontainers PostgreSQL 18 disposable container created for integration tests, with no persistent volume. It was never applied to a local/team database.

## Verification

Final command:

```powershell
mvn -B -ntp -f stylist-service/pom.xml verify
```

Final result on 2026-10-31: **BUILD SUCCESS; 83 tests total, 0 failures, 0 errors, 0 skipped** — 52 P02 engine tests, 13 existing Stylist application/security/OpenAPI regression tests, 16 P03-A PostgreSQL integration tests, and 2 parser unit tests. The integration suite migrated a fresh disposable PostgreSQL database, verified table constraints/index behavior, owner isolation, rollback, stable pagination including timestamp ties, query count, and startup/migration validation. MockMvc exercised HTTP/security/input/error behavior with test-signed JWTs; this is not a real Identity login/Gateway E2E run.

Focused commands after the fix:

```powershell
mvn -B -ntp -f stylist-service/pom.xml -Dtest=StylistSessionRequestParserTests test
mvn -B -ntp -f stylist-service/pom.xml "-Dtest=StylistSessionApiIntegrationTests#strictRequestRejectsUnknownDuplicateWrongTypeAndNonObjectJson" test
```

Results: parser tests **2/2 passed**; focused PostgreSQL/MockMvc malformed-JSON integration **1/1 passed**.

The malformed-body review reproduced a bug before the fix: the parser accepted `{} {}` and ignored the second root value. After enabling trailing-token rejection and routing the body through the endpoint-specific converter, the new HTTP regression returned 400 with `INVALID_REQUEST`, and a direct parser test confirmed valid trailing whitespace remains accepted.

The first focused integration invocation occurred while Docker Desktop was stopped and Testcontainers reported 1 skipped test; it is not counted as a pass. After starting Docker Desktop, the focused malformed-JSON MockMvc integration test passed (1/1), followed by the final full `verify` with zero skips. A parser-only pre-fix check failed as expected, then passed 2/2 after the fix.

Postman collection/environment JSON parse structurally. No Postman or Newman HTTP runtime was executed. See [Postman run guide](../../../stylist-service/postman/P03A_RUN_GUIDE.md).

Initial and final runs of both `node scripts/verify-stylist-planning.cjs` and `node scripts/verify-stylist-planning.cjs --phase P03` exited 1. The initial runs reported commit-range drift, four P03-A Stylist source fingerprints, the Identity `StyleProfileResponse.java` fingerprint, and two broken links. After reviewing the changed sources, recording the impacts below, updating only the reviewed digests, and correcting the two proven link targets, the final runs report only:

- Committed-source drift since the planning review in eight Identity files: `LocalComposeAdminBootstrapService.java`, `NotificationEmailClient.java`, `AddressController.java`, `AdminUserController.java`, `AuthController.java`, `InternalAccountController.java`, `JwksController.java`, and `ProfileController.java`.
- No remaining source-fingerprint or broken-link failures.

The Identity paths in the dependency memo were corrected to the existing `ProfileController.java` and `security/jwt/JwtDecoderConfiguration.java`. Targeted fingerprint updates are recorded only after the following source review:

- `stylist-service/pom.xml`: Flyway core/PostgreSQL module and Testcontainers dependencies support the approved source migration and isolated integration tests.
- `application.properties`: `ddl-auto=none`; Flyway clean disabled, baseline disabled, and validation enabled; JWT defaults match the Identity issuer/audience contract.
- `JwtDecoderConfiguration.java`: RS256 is enforced; contract validator checks issuer, audience, time claims, UUID `sub` and `jti`.
- `SecurityConfiguration.java`: only the two session route patterns receive CUSTOMER/ADMIN authority access, with self-only ownership enforced in repository/service reads.
- Identity `StyleProfileResponse.java` differs from its old digest only by an added import; the response record and JSON properties are unchanged.

No Identity Java source was modified. These reviewed digests were updated in the manifest; no drift was accepted without source inspection.

The requested Identity code review compared the old/current controller mappings: address and admin-user route paths/methods are retained; the auth endpoints remain under `/api/v1/auth` with the prior register, email-verification, login, refresh/logout and password-reset paths; internal batch lookup remains `/internal/v1/accounts/batch-lookup`; JWKS remains `/.well-known/jwks.json`; profile remains `/api/v1/me` and `/api/v1/me/style-profile`. The JWT issuer/audience/subject/issued-at/expiry/JTI/roles claims continue to match the Stylist verifier’s expected contract. One observed API error behavior does change: `NotificationEmailClient` now maps email delivery failures to 503/504 through `ApiOperationException`; the prior uncaught `EmailDeliveryException` fell through the old handler’s generic 500. This is outside P03-A and could affect clients that branch on those status codes. The bootstrap change is import relocation/formatting. This is source/mapping review, not a fresh Identity runtime regression run.

The current branch contains a much broader committed Identity refactor than these requested files (155 changed Identity paths in the reviewed commit range). I did not advance the manifest’s `reviewed_head` across all that code. Therefore the committed-source drift warning remains intentionally visible for review beyond the inspected paths. The validator’s final exit code is 1 for both commands, solely because of that baseline drift warning. It reports P03-A recorded prerequisites but does not prove full P03 completion.

## Remaining work and status

- **P02 correction:** implemented and verified for pure engine/synthetic data; no live catalog mapping or ownership proof.
- **P03-A:** implementation and disposable DB verification complete for session create/history read.
- **P03 / US-07.01 / EP-07:** incomplete. Request/context admission, actual owned/saved producer, Identity context client, retrieval, provider, recommendation persistence, events and live Gateway integration remain outside this authorization.
- No commit, push, merge, workbook edit or persistent database migration was performed.

## Implementation file inventory

P02 correction source/tests: `stylist-service/src/main/java/com/stylemind/stylist/model/recommendation/EngineModel.java`; `stylist-service/src/main/java/com/stylemind/stylist/service/recommendation/internal/CandidatePolicy.java`; `stylist-service/src/main/java/com/stylemind/stylist/service/recommendation/internal/OutfitComposer.java`; `stylist-service/src/test/java/com/stylemind/stylist/engine/OutfitEngineTests.java`; `stylist-service/src/test/resources/stylist/fixtures/p02-commerce-examples.json`.

P03-A implementation: `stylist-service/pom.xml`; `stylist-service/src/main/resources/application.properties`; `stylist-service/src/main/resources/db/migration/V1__create_stylist_session_message_tables.sql`; `stylist-service/src/main/java/com/stylemind/stylist/config/TimeConfiguration.java`; `stylist-service/src/main/java/com/stylemind/stylist/config/StylistWebConfiguration.java`; `stylist-service/src/main/java/com/stylemind/stylist/controller/session/StylistSessionController.java`; `stylist-service/src/main/java/com/stylemind/stylist/dto/session/CreateStylistSessionRequest.java`; `stylist-service/src/main/java/com/stylemind/stylist/dto/session/SessionHistoryQuery.java`; `stylist-service/src/main/java/com/stylemind/stylist/dto/session/StylistMessagePageResponse.java`; `stylist-service/src/main/java/com/stylemind/stylist/dto/session/StylistMessageResponse.java`; `stylist-service/src/main/java/com/stylemind/stylist/dto/session/StylistSessionResponse.java`; `stylist-service/src/main/java/com/stylemind/stylist/entity/message/StylistMessageEntity.java`; `stylist-service/src/main/java/com/stylemind/stylist/entity/session/StylistSessionEntity.java`; `stylist-service/src/main/java/com/stylemind/stylist/errors/ApiExceptionHandler.java`; `stylist-service/src/main/java/com/stylemind/stylist/errors/ErrorCode.java`; `stylist-service/src/main/java/com/stylemind/stylist/errors/StylistApiException.java`; `stylist-service/src/main/java/com/stylemind/stylist/logging/CorrelationIdFilter.java`; `stylist-service/src/main/java/com/stylemind/stylist/mapper/session/StylistSessionMapper.java`; `stylist-service/src/main/java/com/stylemind/stylist/repository/message/StylistMessageRepository.java`; `stylist-service/src/main/java/com/stylemind/stylist/repository/session/StylistSessionRepository.java`; `stylist-service/src/main/java/com/stylemind/stylist/security/ApiAccessDeniedHandler.java`; `stylist-service/src/main/java/com/stylemind/stylist/security/ApiAuthenticationEntryPoint.java`; `stylist-service/src/main/java/com/stylemind/stylist/security/ApiProblemDetails.java`; `stylist-service/src/main/java/com/stylemind/stylist/security/JwtContractValidator.java`; `stylist-service/src/main/java/com/stylemind/stylist/security/JwtDecoderConfiguration.java`; `stylist-service/src/main/java/com/stylemind/stylist/security/SecurityConfiguration.java`; `stylist-service/src/main/java/com/stylemind/stylist/security/SessionRequestPolicyFilter.java`; `stylist-service/src/main/java/com/stylemind/stylist/service/impl/session/StylistSessionServiceImpl.java`; `stylist-service/src/main/java/com/stylemind/stylist/service/session/CreateStylistSessionRequestHttpMessageConverter.java`; `stylist-service/src/main/java/com/stylemind/stylist/service/session/SessionHistoryCursorCodec.java`; `stylist-service/src/main/java/com/stylemind/stylist/service/session/SessionHistoryQueryValidator.java`; `stylist-service/src/main/java/com/stylemind/stylist/service/session/StylistSessionRequestParser.java`; `stylist-service/src/main/java/com/stylemind/stylist/service/session/StylistSessionService.java`.

Tests and handover: `stylist-service/src/test/java/com/stylemind/stylist/session/StylistSessionApiIntegrationTests.java`; `stylist-service/src/test/java/com/stylemind/stylist/session/StylistSessionRequestParserTests.java`; `stylist-service/src/test/java/com/stylemind/stylist/StylistServiceApplicationTests.java`; `stylist-service/postman/StyleMind-Stylist-P03A.postman_collection.json`; `stylist-service/postman/StyleMind-Stylist-P03A.postman_environment.json`; `stylist-service/postman/P03A_RUN_GUIDE.md`; `docs/planning/stylist/EP-07_P02_IMPLEMENTATION_REPORT.md`; `docs/planning/stylist/EP-07_P03A_SESSION_HISTORY_REPORT.md`; `docs/planning/stylist/EP-07_EXECUTION_MANIFEST.json`. The repository’s pre-existing staged, unstaged and untracked work was retained; this inventory names the implementation scope, not a claim that every listed file was newly created in this turn.

Decision and instruction alignment files updated for the latest scope: `AGENTS.md`; `README.md`; `stylist-service/AGENTS.md`; `docs/architecture/SERVICE_ARCHITECTURE_AND_LAYOUT.md`; `docs/governance/CURRENT_PROJECT_CONTEXT.md`; `docs/governance/CURRENT_BUSINESS_RULES.md`; `docs/governance/OPEN_IMPLEMENTATION_DECISIONS.md`; `docs/planning/stylist/README.md`; `docs/planning/stylist/EP-07_BUSINESS_CORRECTION_2026-10-28.md`; `docs/planning/stylist/EP-07_IMPLEMENTATION_PLAN.md`; `docs/planning/stylist/EP-07_PHASE_A_CONTRACT_MEMO.md`; `docs/planning/stylist/EP-07_P03_DECISION_MEMO.md`; `docs/planning/stylist/EP-07_VERIFICATION_MATRIX.md`.
