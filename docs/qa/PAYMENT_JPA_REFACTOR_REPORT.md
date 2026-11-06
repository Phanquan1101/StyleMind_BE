# Payment repository JPA refactor

Date: 2026-11-02 (Asia/Saigon).

## Authorization and scope

The owner explicitly requested converting Payment repositories to JPA, extracting service mapping, then committing/pushing those two structural changes on a separate branch before guest checkout. This supplies the scoped approval required by AGENTS.md. Branch: refactor/payment-jpa-mapper, based on origin/dev at edfe9b4679249b3dc3db08e98112935b5f7af203.

This branch retains existing registered-customer behavior. Guest checkout, guest owner/null-handling changes, guest migrations, Commerce/Gateway/Docker changes and mixed governance/model edits are excluded. Original working files and staging are preserved in the primary worktree. No persistence dependency, migration, schema, HTTP endpoint, authorization or service boundary is changed.

Read the current governance documents, Payment V1/model, US-04.02, COD two-step and US-04.03 contracts and existing tests. The configured read-only backlog workbook path is unavailable; no workbook was changed.

## Files for JPA and its verification

- payment-service/src/main/java/com/stylemind/payment/repository/PaymentAttemptRepository.java
- payment-service/src/main/java/com/stylemind/payment/repository/PaymentReconciliationRepository.java
- payment-service/src/main/java/com/stylemind/payment/repository/PaymentOutboxRepository.java
- payment-service/src/main/java/com/stylemind/payment/entity/PaymentAttemptEntity.java
- payment-service/src/main/java/com/stylemind/payment/entity/FinancialTransactionEntity.java
- payment-service/src/main/java/com/stylemind/payment/entity/PaymentExceptionEntity.java
- payment-service/src/main/java/com/stylemind/payment/entity/PaymentOutboxEntity.java
- payment-service/src/test/java/com/stylemind/payment/OrderPaymentPostgresIntegrationTests.java
- payment-service/src/test/java/com/stylemind/payment/PaymentCreationHttpPostgresTests.java
- payment-service/src/test/java/com/stylemind/payment/PaymentEventDispatcherPostgresIntegrationTests.java
- payment-service/src/test/java/com/stylemind/payment/PaymentRuntimePostgresIntegrationTests.java
- payment-service/src/test/java/com/stylemind/payment/IsolatedPostgres.java
- docs/qa/PAYMENT_JPA_REFACTOR_REPORT.md (this report)

The three extra integration fixtures use the shared opt-in native PostgreSQL helper, enabling the isolated JPA gate without Docker. Default execution still uses Testcontainers. No guest test or migration is copied. Mapper/service/unit-test files are listed in PAYMENT_MAPPER_REFACTOR_REPORT.md.

## Requirement mapping

| Approved requirement | Implementation and evidence |
| --- | --- |
| Payment-only JPA repositories | EntityManager, mapped entities and JPQL/entity reads replace JdbcClient in three repositories; existing JPA dependency/configuration is reused |
| Existing physical model | Four entities map Payment V1 tables; remote UUIDs remain scalar references; ddl-auto remains none |
| Serialization, replay and concurrency | Existing transaction advisory locks, unique keys and ON CONFLICT DO NOTHING are preserved; duplicate insert does not poison the transaction |
| Settlement/outbox atomicity | Existing service transactions and pessimistic row locking retained; ledger/attempt/outbox remain atomic; native DML clears managed cache |
| Regression | Added bulk-update cache/rollback and duplicate-insert-followed-by-success cases; existing tests cover owners A/B, negative HTTP authorization/input, concurrent creation/collection, replay, callback settlement and outbox delivery |

Native PostgreSQL DML remains deliberately routed through JPA for locks and conflict-safe writes. These repositories use EntityManager rather than Spring Data JpaRepository interfaces.

## Verification

Full isolated Payment regression: **BUILD SUCCESS; 78 tests, zero failures/errors/skips**. This is the JPA/mapper-only branch, without guest migrations or tests.

Command (from the isolated worktree root):

~~~powershell
mvn -o -B -ntp -f payment-service/pom.xml '-Ddebug=false' '-DargLine=-Duser.timezone=UTC' '-Dstylemind.test.postgres-bin=C:/Program Files/PostgreSQL/18/bin' verify
~~~

Evidence: ignored payment-jpa-mapper-verify.log and payment-service/target/surefire-reports. All main/test sources compiled and the executable JAR was built. git diff --check passed. No packaged-service smoke or manual Postman execution was performed. The existing eight-request handoff Postman collection also passed its local structural/script validation; no HTTP collection execution is claimed.

## Environment and limits

Tests use only disposable native PostgreSQL 18.1 clusters under the isolated worktree's ignored payment-service/target/isolated-postgres directory on random loopback ports, with synthetic fixtures. No owner database, Compose resources, schema migration in a persistent environment, real provider payment or ignored secret is accessed. No HTTP story was added; existing Postman collections/run guides apply unchanged and were not manually executed. Local tests are not production-readiness evidence.

The separate branch contains only this Payment structural refactor and its verification artifacts. The original main worktree, staged/unstaged/untracked changes and guest work remain intact. No force push or history rewrite is required.
