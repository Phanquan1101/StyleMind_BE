# EP-02 Identity — Final Acceptance Review and Handover

- **Review date:** 2026-10-10 (+07:00)
- **Workspace:** C:\Users\FPTSHOP\Desktop\StyleMind_BE
- **Branch / HEAD:** dev / 61d5d78ca14e1c14660dc078a15dbecd8ff2bbee

**Recommendation:** accept EP-02 Identity's approved **local functional scope** after owner review. This is not an owner acceptance record, a workbook status change, or a production-readiness claim.

## 1. Scope and baseline

This review covers only Identity stories US-02.01 through US-02.07. Notification was used only as the email-delivery dependency and its regression suite was run. No Commerce, Payment, Wardrobe, Stylist, or other feature work was started.

At the start, the working tree already contained staged/unstaged/untracked work from prior tasks, including Address and Manage Users. No staged diff was present. All of that work was preserved; there was no reset, checkout, clean, commit, or push. In this review the only repository changes are this report and the F-01 wording correction in [EP-02_RECONCILIATION.md](../identity-users/EP-02_RECONCILIATION.md). Test fixtures and credentials were generated under ignored target/ and removed after the runtime stack was cleaned.

Sources used were the current governance rules, the approved story contracts and reports, Identity source/tests, migrations V1–V3, four current Postman collections, and StyleMind_Task.xlsx → Backlog rows 6–27. The workbook was opened read-only. Its 22 EP-02 task rows still show To Do; no workbook cell was changed.

| Artifact | SHA-256 at review start | SHA-256 after verification |
|---|---|---|
| C:\Users\FPTSHOP\Desktop\StyleMind_Task.xlsx | A4027B00A89A5E41135FCA94140AFDD81173CA7E79D63BBACCE0072D16A92A42 | Same |
| identity-service/src/main/resources/db/migration/V1__create_identity_tables.sql | 3BB2316BAAF43FEF5A8C9B3B261DA4ADA2BE25D9253C69A42379DF71E82B5CAB | Same |
| identity-service/src/main/resources/db/migration/V2__create_email_verification_challenge.sql | 68DB77E1629D27F9E21AB90D9E22200560A405A4D4EB4BC02049A38882699FC6 | Same |
| identity-service/src/main/resources/db/migration/V3__create_password_reset_challenge.sql | 4D01B554F3474C56A736F9E1906D0BFC516E19C196655291DA65BE63AD314A51 | Same |

V1–V3 were not edited. The final runtime used its own disposable PostgreSQL 18.6 database and observed Flyway history 1:true, 2:true, 3:true.

## 2. Acceptance matrix

| Story | Approved rule → implementation | Automated evidence | HTTP/runtime evidence | Result and limit |
|---|---|---|---|---|
| **US-02.01 Register / verification** | Normalize email; preserve password; server assigns CUSTOMER/PENDING_VERIFICATION; create account and challenge atomically; do not issue tokens. OTP is purpose/account-bound, digest-only, expires at the exact boundary, and follows approved attempt/lock/resend limits. Implemented through RegisterController → RegisterService → AccountRepository / EmailVerificationRepository and EmailVerificationService; V1/V2 own the tables. | RegisterPostgresIntegrationTests 13 tests and RegisterServiceTests / VerificationCodeServiceTests; includes duplicate-normalized-email, rollback, exact expiry, leading zero, lock, cooldown, resend rotation, and verify/resend races. | Disposable flow registered and verified A/B/C and fresh D. Mailpit checks required one exact recipient, registration subject/purpose, and matching in-memory OTP. Fresh D got 429/no extra email during cooldown; after 61 seconds resend rotated the code, rejected the old code, and accepted the new code. | **PASS in local scope.** External SMTP/provider delivery was not tested. |
| **US-02.02 Login / JWT / JWKS** | Unknown email and wrong password share the credential error; check state after correct password; only verified ACTIVE account with allowed database role receives RS256 access and opaque refresh tokens. Claims and roles come from the server/database. Implemented through LoginController → LoginService / LoginSessionService → repositories and JwtTokenService. | LoginPostgresIntegrationTests 13 plus JwtTokenServiceTests, signing and decoder tests, and HTTP validation matrix. | Real Login in flows 1–4; signed token subject/issuer/audience/roles checked in memory and accepted by APIs. OpenAPI/JWKS and refresh-derived roles were exercised. | **PASS in local scope.** Other resource services' JWT compatibility and production key operations were not assessed. |
| **US-02.03 Refresh / Logout** | Refresh rotates one digest-only session atomically while preserving absolute expiry; replay/expired/revoked tokens fail. Logout is idempotent and revokes only the identified refresh session. Implemented through RefreshSessionController → RefreshSessionService / RefreshSessionTransactionService → RefreshSessionRepository. | RefreshPostgresIntegrationTests 16 tests, including concurrent refresh, refresh/logout races, rollback, replay, role/state checks, and access-JWT lifetime. | Flow 1 rotated A's token, rejected replay, logged out the current session, and rejected reuse. B's session remained usable during A's password reset. | **PASS in local scope.** A previously issued access JWT remains accepted until exp; no blacklist, family replay handling, or all-device logout is approved. |
| **US-02.04 Forgot Password** | Generic request response; only eligible verified ACTIVE account receives a reset email. OTP and reset grant are purpose-bound, expiring, digest-only and one-use. Reset changes password, consumes grant, and revokes all unrevoked refresh sessions in one transaction. Implemented through PasswordResetController → PasswordResetService / PasswordResetTransactionService → PasswordResetRepository; V3 adds only the approved reset-challenge table. | PasswordResetPostgresIntegrationTests 12 tests, including exact TTL, rate/attempt limits, wrong-purpose rejection, one-use grant, rollback, and concurrent reset/refresh. | Flow 2 checked exact recipient and password-reset purpose in Mailpit; verified the OTP before reset, reset the password, rejected both pre-reset A refresh sessions and the old password, accepted the new password, and kept B's session working. Reset-token replay failed. | **PASS in local scope.** Existing access JWTs remain valid to expiry. No external email provider was contacted. |
| **US-02.05 Manage Profile** | Self-only by signed JWT sub; GET is read-only; PATCH / PUT have the approved null, replacement, normalization, limit, and atomic-write semantics. Brand references are UUID syntax/uniqueness only. Implemented through ManageProfileController → ManageProfileService / persistence service → ProfileRepository. | ManageProfilePostgresIntegrationTests 8 and ProfileRequestValidationTests 7. Covers aggregate behavior, authorization, transaction and ownership. | Postman folders 01–06: 107 requests / 107 assertions. Fresh-account folder 07 separately: 9 / 9. A/B full-state comparisons passed. Fresh D returned null on repeated reads before first PATCH / PUT, then verified first-row creation. | **PASS in local scope.** A syntactically valid brand UUID is not proof a Commerce brand exists; Identity makes no Commerce call. |
| **US-02.06 Manage Addresses** | Eight owned routes plus local province/ward lookup; strict ownership, validation, default selection, merge/delete rules, and account-row transaction coordination. Implemented through ManageAddressController → ManageAddressService / persistence service → AddressRepository and pinned local directory. | ManageAddressPostgresIntegrationTests 11, AddressRequestValidationTests 4, and VietnamAdministrativeDirectoryTests 2. Covers rollback and five coordinated write races. | Postman: 39 requests / 75 assertions. A/B authentication, address-free preflight, lifecycle, default conflict, full unchanged-state comparisons, foreign-owner denial, and cleanup all passed. Flow 1 also created/read/deleted an address through HTTP. | **PASS in local scope.** The default-address invariant is protected by application writes, not direct SQL. No Commerce/order/shipping side effect exists. |
| **US-02.07 Manage Users** | Only the three approved Admin routes; actor must be currently ACTIVE, verified, and ADMIN. Status is ACTIVE↔SUSPENDED; roles are full CUSTOMER/ADMIN replacement; last qualifying Admin is guarded. Status/role changes do not revoke sessions. Implemented through ManageUsersController → ManageUsersService → ManageUsersRepository. | ManageUsersPostgresIntegrationTests 12 and ManageUsersRequestValidatorTests 5. Current tests include role-removal/suspension authorization ordering, real HTTP/HEAD cases, write races, rollback, and last-Admin guards. | Postman: 46 requests / 138 assertions. Custom Flow 3: 16 assertions for role grant, fresh Login/Refresh roles, stale Admin denial, and Profile/Address baseline access. Flow 4: 15 HTTP/DB assertions for suspension, reactivation and preserved email_verified_at. | **PASS in approved local scope.** First Admin remains operator-controlled; none was provisioned in a persistent database. |

## 3. Cross-story HTTP flows

All were run against http://localhost:18081 in the isolated stack; Mailpit was at http://localhost:18025.

1. **Register → email → verify → Login → Profile → Address → Refresh → Logout:** passed. Registration and verification mail was matched to the exact test recipient and purpose; A's profile/address were usable; refresh rotated once; logout/replay behavior matched the contract.
2. **Forgot → reset email → verify OTP → reset:** passed. The reset email matched A and reset purpose. Password changed; both active A refresh sessions were revoked; old password and grant replay failed; new password worked; B remained unaffected. A's earlier access JWT still worked, per the expiry-only rule.
3. **Admin role change:** passed. C initially logged in as CUSTOMER; after Admin grant, a new Login and Refresh carried CUSTOMER+ADMIN. After ADMIN removal, the stale Admin JWT was denied on Manage Users while Profile and Address returned 200. Refresh then carried CUSTOMER only.
4. **Suspend/reactivate:** passed. Login and Refresh returned 403 ACCOUNT_NOT_ACTIVE while suspended. Existing access continued to Profile/Address. Reactivation preserved the prior email_verified_at; the pre-suspension refresh session worked again and did not regain a removed role.
5. **A/B negative isolation:** passed in the Profile and Address Postman runs. Rejected owner overrides and writes were followed by complete A/B state comparisons; Manage Users denial cases compared the user-list snapshot before and after.

Email body/OTP and access/refresh/reset tokens were consumed only in process memory and were not written to test output or this report.

## 4. Defects, test-harness observations, and limitations

### Confirmed defect fixed before this review

The preceding US-02.07 follow-up reproduced a live-authorization race: GET could return 200 after Admin role removal had committed, and a suspension race could return 500. The fix serializes actor reads with Admin writes using the shared advisory-lock protocol and returns list count/page from one SQL statement snapshot. The current source includes deterministic PostgreSQL/HTTP regression coverage; the current full Identity suite and Manage Users Postman run pass. Details are in [US-02.07_LIVE_AUTHORIZATION_FOLLOW_UP.md](../identity-users/US-02.07_LIVE_AUTHORIZATION_FOLLOW_UP.md). This review did not change application code.

### Harness alignment; not application defects

- The first custom lookup probe assumed province_code / ward_code; the actual DTO exposes code. It was corrected and rerun successfully.
- The first suspension probe assumed Refresh should return 401. Current contract/source/tests require 403 ACCOUNT_NOT_ACTIVE; the final HTTP rerun asserted that status/code and passed. No API behavior was changed.

### Separate open gates

- **F-01 HMAC:** resolved for local configuration by the owner's earlier key-rotation approval, as stated by [IDENTITY_AUDIT_FOLLOW_UP.md](../identity-audit/20261008T1144-0700/IDENTITY_AUDIT_FOLLOW_UP.md), section 9. This review did not read .env, rotate a key, or invalidate a challenge. Default configuration was not rechecked.
- **Default local email smoke:** remains a separate, unverified gate. The earlier audit observed that the effective SMTP target was not Mailpit and therefore did not run a safe default email smoke. This turn used only its synthetic isolated Mailpit configuration.
- **F-04 handover hygiene:** the old exact directory C:\Users\FPTSHOP\AppData\Local\Temp\stylemind-identity-audit-20261008 remains an owner action. It was not opened or removed.
- **F-05 stability history:** an earlier run's five failures and one error still have no established root cause. They were not reproduced in three later 138-test runs documented in the follow-up, and this final current-tree 138-test run also passed. This does not resolve the historical cause.
- **Backlog tracking:** workbook rows 6–27 remain To Do; no tracker status or owner acceptance was changed.
- **Production readiness:** external SMTP/provider delivery, production key provisioning/rotation/distribution, deployment/database topology, and public-edge abuse controls were not tested or approved. No production-ready claim follows from this local review.

Approved behavior retained as a limitation, not reclassified as a defect: access JWTs live to expiry; Logout revokes one refresh session; password reset revokes all active refresh sessions; Admin status/role changes do not revoke sessions; Profile/Address have no global live account-state gate; brand IDs are syntax-validated only; and the default-address invariant is application-write scoped.

## 5. Verification commands and evidence

| Verification | Command / scope | Result |
|---|---|---|
| Identity automated regression | mvn -B -ntp -f identity-service/pom.xml verify -l target/identity-final-20261010/identity-verify.log | **138 passed; 0 failures, 0 errors, 0 skipped; BUILD SUCCESS.** Includes PostgreSQL integration suites for all seven stories. |
| Notification regression | mvn -B -ntp -f notification-service/pom.xml verify -l target/identity-final-20261010/notification-verify.log | **23 passed; 0 failures, 0 errors, 0 skipped; BUILD SUCCESS.** |
| Postman structure | Node v22.20.0 parsed four Collection v2.1 JSON files and two example environments; compiled each embedded event script and resolved static/dynamic variable references. | **4 collections, 221 requests, 407 event scripts; pass.** Structural validation is separate from HTTP execution. |
| Profile runtime main | Newman 6.2.2; folders 01–06 of StyleMind-Identity-Manage-Profile.postman_collection.json. | **107 requests / 107 assertions; 0 failed.** |
| Profile runtime fresh lifecycle | Newman 6.2.2; folder 07 separately, with a newly verified D that had never used Profile writes. | **9 requests / 9 assertions; 0 failed.** |
| Address runtime | Newman 6.2.2; StyleMind-Identity-Manage-Addresses.postman_collection.json. | **39 requests / 75 assertions; 0 failed.** |
| Manage Users runtime | Newman 6.2.2; StyleMind-Identity-Manage-Users.postman_collection.json. | **46 requests / 138 assertions; 0 failed.** |
| Direct cross-story HTTP flows | runtime-flows.cjs, postman-cross-story.cjs, suspend-reactivate.cjs, and fresh-account-resend.cjs under ignored target/identity-final-20261010/. | **103 assertions passed** (64 + 16 + 15 + 8). No real mailbox used. |
| Runtime API inventory | GET /v3/api-docs on the disposable Identity service. | **25 operations** across auth, Profile, Address/location, Manage Users, and public JWKS. |
| Hygiene | git diff --check plus whitespace scan of new/edited Markdown. | Pass; details below. |

### Commands used

The environment and fixture files referenced below held only generated disposable test data. They have been removed; no secret values are recorded here.

~~~powershell
mvn -B -ntp -f identity-service/pom.xml verify -l target/identity-final-20261010/identity-verify.log
mvn -B -ntp -f notification-service/pom.xml verify -l target/identity-final-20261010/notification-verify.log
node target/identity-final-20261010/postman-structure-check.cjs

npx --yes newman@6.2.2 run postman/StyleMind-Identity-Manage-Profile.postman_collection.json --environment target/identity-final-20261010/profile.environment.json --folder "01 — Login and identify test accounts" --folder "02 — Profile happy path" --folder "03 — Style profile happy path" --folder "04 — Validation and unchanged-state checks" --folder "05 — Ownership A/B" --folder "06 — Authentication failures" --reporters cli --color off
npx --yes newman@6.2.2 run postman/StyleMind-Identity-Manage-Profile.postman_collection.json --environment target/identity-final-20261010/profile.environment.json --folder "07 — Fresh-account lifecycle — prerequisite required" --reporters cli --color off
npx --yes newman@6.2.2 run postman/StyleMind-Identity-Manage-Addresses.postman_collection.json --environment target/identity-final-20261010/profile.environment.json --reporters cli --color off
npx --yes newman@6.2.2 run postman/StyleMind-Identity-Manage-Users.postman_collection.json --environment target/identity-final-20261010/users.environment.json --reporters cli --color off

node target/identity-final-20261010/runtime-flows.cjs
node target/identity-final-20261010/postman-cross-story.cjs
node target/identity-final-20261010/suspend-reactivate.cjs
node target/identity-final-20261010/fresh-account-resend.cjs

docker compose --project-name stylemind-identity-final-20261010 --env-file target/identity-final-20261010/runtime.env -f docker-compose.yml -f target/identity-final-20261010/ports.override.yml up -d --build postgres mailpit notification-service identity-service
docker compose --project-name stylemind-identity-final-20261010 --env-file target/identity-final-20261010/runtime.env -f docker-compose.yml -f target/identity-final-20261010/ports.override.yml down --volumes
git diff --check
~~~

Sanitized result summary: target/identity-final-20261010/runtime-summary.md. Maven logs are in the same ignored evidence directory. The test-only environment files and credentials were deleted after the disposable project was removed.

The four current collections are [Auth](../../../postman/StyleMind-Identity-Auth-Complete.postman_collection.json), [Manage Profile](../../../postman/StyleMind-Identity-Manage-Profile.postman_collection.json), [Manage Addresses](../../../postman/StyleMind-Identity-Manage-Addresses.postman_collection.json), and [Manage Users](../../../postman/StyleMind-Identity-Manage-Users.postman_collection.json). Auth's collection was structurally validated; the main Auth flows were executed by the disposable HTTP harness so it could retrieve real Mailpit OTPs without exporting them.

## 6. Runtime resources and cleanup

- Created Compose project: stylemind-identity-final-20261010, with its own PostgreSQL 18.6 container/volume, Identity, Notification, and Mailpit. Host ports were 15432, 18081, 18025, and 11025. Its database was stylemind_identity; V1–V3 all succeeded.
- The runtime used generated test-only DB credentials, HMAC/service token, and RSA key. These files and the Newman environment/account fixtures were removed. No real .env or external SMTP credentials were read or used.
- The exact disposable Compose project, its containers, network, PostgreSQL volume, and uniquely tagged service images were removed and checked absent after tests. No global prune was run.
- The pre-existing stylemind-task4 containers and stylemind-task4_postgres-data volume were left untouched and remain separate. Native PostgreSQL was not used. No persistent database was connected to, migrated, reset, or cleaned. No Admin was provisioned outside the disposable database.

## 7. Handover guide

- Identity is called directly at http://localhost:8081; this repository's Compose stack has **no API Gateway on port 8080**. The Postman runtime above used a private disposable port override.
- For a safe normal local email smoke, first ensure the selected Compose environment routes SMTP to Mailpit (mailpit:1025, auth/TLS disabled). Do not use a user-managed SMTP profile for test addresses unless deliberately chosen. This review did not inspect the actual .env or certify its settings. Mailpit UI is normally on http://localhost:8025.
- Import the four collections and the example environment(s) through [postman/README.md](../../../postman/README.md). Auth needs a dedicated verified test mailbox; Profile main folders 01–06 need two verified accounts; Profile folder 07 needs a new verified account never used for Profile writes; Address needs two distinct verified accounts whose address lists are empty; Manage Users needs verified A/B Admin fixtures and Customer C in an isolated database.
- Do not provision an Admin through a public route. The operator-only procedure is [FIRST_ADMIN_PROVISIONING.md](../../operations/identity/FIRST_ADMIN_PROVISIONING.md); it must target only an explicitly approved database. This review used SQL only inside its disposable database.
- Token guarantees and limits are in [identity-service/README.md](../../../identity-service/README.md): RS256/JWKS; no-store auth responses; refresh tokens are opaque and stored as digests; refresh rotates one session; Logout revokes one; password reset revokes all active refresh sessions; access tokens remain valid to expiry; status/role changes do not instantly revoke sessions.

## 8. Final local status

**Recommend owner acceptance of US-02.01–US-02.07 local functional scope.** The current automated and disposable HTTP evidence passes for all seven stories and the required cross-story flows. Workbook tracking, default local email readiness, F-04 cleanup, F-05 historical root-cause uncertainty, and production gates remain separate and open as described above. No feature, migration, schema, database, or business contract was changed in this final review.

No work was performed for any service other than the Identity verification dependency in Notification. No commit or push was performed.
