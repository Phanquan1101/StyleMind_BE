# Payment service mapper extraction

Date: 2026-11-02 (Asia/Saigon).

The owner requested a dedicated mapper package and explicitly asked to push Payment JPA/mapper separately before guest checkout. The mapper is a structural extraction: existing API fields, status mapping, payload shape, validation, ownership, transaction boundaries and database schema remain unchanged. This branch does not add guest/null-owner support.

## Files for mapper extraction

- payment-service/src/main/java/com/stylemind/payment/mapper/PaymentMapper.java
- payment-service/src/main/java/com/stylemind/payment/service/impl/PaymentAttemptServiceImpl.java
- payment-service/src/main/java/com/stylemind/payment/service/impl/PaymentOutboxServiceImpl.java
- payment-service/src/test/java/com/stylemind/payment/PaymentCreationServiceTests.java
- payment-service/src/test/java/com/stylemind/payment/PaymentOrderAttemptServiceTests.java
- payment-service/src/test/java/com/stylemind/payment/PaymentWorkflowServiceTests.java
- docs/qa/PAYMENT_MAPPER_REFACTOR_REPORT.md (this report)

PaymentMapper is injected into both services. It maps new/existing attempts, exception detail/list and PaymentConfirmed payloads. SETTLED maps to PAID and REQUIRES_RECONCILIATION maps to EXCEPTION. The outbox payload retains its previous Map serialization and non-null customer contract. Service checks, exceptions, persistence and transaction handling remain in services/repositories. Unit fixtures now pass a real mapper; runtime tests verify serialization and settlement through the original endpoints.

## Verification

Full isolated Payment regression: **BUILD SUCCESS; 78 tests, zero failures/errors/skips**. This is the JPA/mapper-only branch, without guest migrations or tests.

Command (from the isolated worktree root):

~~~powershell
mvn -o -B -ntp -f payment-service/pom.xml '-Ddebug=false' '-DargLine=-Duser.timezone=UTC' '-Dstylemind.test.postgres-bin=C:/Program Files/PostgreSQL/18/bin' verify
~~~

Evidence: ignored payment-jpa-mapper-verify.log and payment-service/target/surefire-reports. All main/test sources compiled and the executable JAR was built. git diff --check passed. No packaged-service smoke or manual Postman execution was performed. The existing eight-request handoff Postman collection also passed its local structural/script validation; no HTTP collection execution is claimed.

The full command, JPA files and isolated database boundary are described in PAYMENT_JPA_REFACTOR_REPORT.md. Existing Postman contracts are unchanged; no manual collection execution is claimed. No Identity, Commerce, Gateway, Docker, guest schema or feature change is included. The owner's original worktree and mixed guest changes remain preserved. Local checks do not establish production readiness.
