# Manage Profile Postman Guide

## Manage Users — US-02.07

Import [the Manage Users Collection v2.1](StyleMind-Identity-Manage-Users.postman_collection.json) and the blank [Manage Users example environment](StyleMind-Manage-Users.postman_environment.example.json). This flow is intended for a **disposable local Identity database** and the Identity API directly; use `http://localhost:18081` for the dedicated smoke stack or change `baseUrl` for another isolated stack. It changes status/roles and creates refresh sessions. Do not run it against production or an important/persistent user database.

Provision three dedicated accounts in that disposable database through Register and real email verification using Mailpit: Admin A, Admin B, and Customer C. Do not use a public role-assignment API (none exists). Apply `ADMIN` to A and B only through an operator-controlled SQL session in the disposable database, preserving their CUSTOMER roles. Keep C as CUSTOMER. The [first-Admin guide](../docs/operations/identity/FIRST_ADMIN_PROVISIONING.md) describes the guarded operator procedure; do not run it against persistent local data for this smoke. Fill the three email/password/account-ID fields only in the local Postman environment Current Values. Shared examples intentionally contain no passwords or tokens.

Run all six folders in order in Collection Runner (46 requests):

1. Sign in A/B/C and confirm their distinct identities.
2. Check list output, filters, count, stable pages, and invalid query handling.
3. Prove Customer C cannot list or mutate users; the full-list snapshots before and after rejected writes must match.
4. Suspend C, check Login/Refresh denial and the existing access-token lifetime, reactivate C, and use the still-valid refresh session.
5. Give C both roles, verify a newly refreshed token can call Manage Users, remove ADMIN, reject C's stale ADMIN JWT using live database state, then refresh to CUSTOMER-only.
6. Check no-op self requests, self-change conflicts, demote B, then prove the last qualifying Admin cannot suspend or remove their own ADMIN role. The ending snapshot checks rejected writes did not mutate account data.

Each protected request binds Authorization from the collection-scoped token captured for its actor, so an environment token cannot replace it. The fixture data should contain only A/B/C; list snapshots use the first 100 users. Collection tests assert status, Problem Details code, no-store, safe response fields, and business outcomes. A structural/script check is separate from an actual HTTP/Newman run. Runtime evidence for this implementation is in the [US-02.07 report](../docs/qa/identity-users/US-02.07_IMPLEMENTATION_REPORT.md).

## Manage Addresses — US-02.06

Import [the Manage Addresses Collection v2.1](StyleMind-Identity-Manage-Addresses.postman_collection.json) and use the existing [local example environment](StyleMind-Local.postman_environment.example.json). The collection calls Identity directly at `http://localhost:8081`; the repository has no API Gateway. It exercises the eight US-02.06 routes, real Login for A/B, pinned province/ward lookup, default-address transitions, validation, no-store responses, owner isolation and cleanup of only IDs created by that run. Authenticated requests explicitly set their Authorization header from the run-scoped collection token in a pre-request script, so a stale `tokenA`/`tokenB` value in an older environment cannot replace the token returned by the Login A/B request.

For Address runs, `emailA` and `emailB` must be two distinct, verified, dedicated local CUSTOMER accounts whose Address list is empty. The collection stops after the first preflight if the accounts are not distinct/empty. Do not use a personal account or delete existing addresses to satisfy this precondition. Fill `passwordA` and `passwordB` in Postman's local environment only; the shared example keeps them blank. Login is performed at run time. Access tokens and account IDs exist only in collection variables during the current run; never export a populated collection/environment.

Run collection folders in order. Folder 02 chooses the first province and a ward from the live response returned by the pinned local dataset. Folder 03 creates A1/A2, reads and patches A2, switches default, then snapshots A's current full Address state immediately before the delete-default conflict. The follow-up list must match every field exactly, including explicit `null` values. Folders 04–05 each snapshot the relevant A and B state immediately before the negative request group, then compare the canonicalized complete Address objects (`address_id`, `label`, `recipient_name`, `recipient_phone`, `address_line`, `ward_code`, `is_default`) afterward. Missing fields fail separately from `null`; rows are sorted by `address_id` before comparison. Folder 04 also proves B is unchanged after targeting A's addresses, and A is unchanged after B's attempts. Folder 05 checks both accounts after invalid input and attempted owner override. Folder 06 then deletes only the two IDs created by this run and checks A's list is empty. Folder 07 checks missing/malformed authentication. Do not run cleanup against any IDs except the collection variables set by this run. If a token expires, start a new run so Login supplies fresh tokens.

The collection does not register or verify accounts, invent OTPs, call email, issue SQL cleanup, or use a gateway. It calls the existing authenticated GET /api/v1/me once for each account to prove their account IDs differ; Identity still verifies each signed token on every API call. Collection schema/script validation is a structural check; it is not proof of runtime HTTP success. The Postman collection is separate from Java Testcontainers/HTTP verification, whose result is recorded in the [US-02.06 implementation report](../docs/qa/identity-address/US-02.06_IMPLEMENTATION_REPORT.md).

This importable Postman Collection v2.1 exercises the approved local Identity Manage Profile contract. The local repository has no API Gateway: use the Identity service at http://localhost:8081, not port 8080.

## Import and configure

1. Import the collection file StyleMind-Identity-Manage-Profile.postman_collection.json and environment file StyleMind-Local.postman_environment.example.json into Postman.
2. Select the imported environment. Its sample emails use the reserved example.test domain and cannot log in; replace them with two dedicated, verified CUSTOMER test accounts. Enter passwords only in the local environment Current Value fields.
3. Keep accessTokenA/accessTokenB and accountIdA/accountIdB collection-scoped. Each protected request copies the captured token into a request-local Authorization value; local scope takes precedence over environment variables. The example environment also contains no token or account ID variables, so an old environment token cannot override a fresh Login token.
4. Do not save/export a populated collection or environment into this repository. Shared files have blank passwords and tokens. Do not use personal accounts: Login creates refresh sessions and the flows change profile data.
5. Ensure PostgreSQL and Identity are running. Notification is needed only if provisioning accounts through Auth and its configured email delivery. Profile requests go directly to Identity on port 8081.

## Provision dedicated test accounts

The Profile collection never registers accounts, verifies OTPs, sends email, or resets passwords. To provision A and B, use the existing Auth collection only as needed:

- Run its folder 01 - Register and verify email separately for each account, with a distinct reachable test mailbox and password.
- Copy the real verification OTP from Mailpit or the configured mailbox and run Verify Email. Do not invent an OTP. Repeat with a second account.
- Do not run the Auth collection Forgot Password folder as part of profile testing. The Profile collection performs its own Login and does not depend on the Auth collection accessToken variable; The Auth collection later clears its separate accessToken variable after its post-logout replay check; that does not affect the Profile collection variables.

The example.test email values are documentation placeholders, not provisioned accounts.

## Main flow: folders 01–06

In Postman Collection Runner, select folders 01 through 06 in order and leave folder 07 unselected. Folder 01 logs in A/B, captures tokens and account IDs, and confirms the IDs differ. A failed Login or identity prerequisite stops the run so later requests cannot continue with empty or stale credentials.

The main run has 107 requests:

| Folder | Requests | Purpose |
|---|---:|---|
| 01 - Login and identify test accounts | 4 | Real Login for A/B, capture access tokens, confirm identities differ. |
| 02 - Profile happy path | 5 | GET /me; trim a Vietnamese display name; PATCH and GET an update. |
| 03 - Style profile happy path | 6 | Save/read all preferences, prove full replacement, clear values and distinguish existing-empty from absent. |
| 04 - Validation and unchanged-state checks | 65 | PATCH/PUT invalid cases, Problem Details/correlation checks, boundaries and post-group state comparisons. |
| 05 - Ownership A/B | 15 | Establish different baselines, try query/body owner overrides with A, then compare A/B state. |
| 06 - Authentication failures | 12 | Each API with no bearer and malformed bearer; compare state after rejected writes. |

The repeatable main flow does not assume either account has a missing profile. It establishes baselines before negative and ownership groups. GET after an invalid-request group shows that rejected requests left that group baseline unchanged; it does not prove rollback after an internal persistence failure. PostgreSQL integration tests provide rollback and concurrency evidence.

## Fresh-account lifecycle: folder 07 only

Run folder 07 separately, not in the 01–06 main run. It requires a newly registered and verified dedicated CUSTOMER account that has never used either Profile write API. Set freshEmail and freshPassword in the local environment. The folder logs in, checks repeated GETs return profile null and style_profile null, then checks first PATCH and first all-empty PUT creation.

Do not run folders 01–06 on the fresh account first; that changes its data and it no longer meets the prerequisite. The folder has real assertions and stops if the account is not fresh. It is not silently skipped. Use another new account for a later run. There is no DELETE or database-reset cleanup request.

## Assertions and coverage limits

Scripts assert exact status, response fields/values, full aggregate equality, canonical values, Problem Details code/status/instance, X-Correlation-Id and the correlationId body field. Profile responses are checked for password/hash, OTP/challenge, session/token, authorization, role and account-status fields. Scripts do not log passwords, tokens, full auth responses or request bodies.

Boundary requests include display_name length 100, heights 50 and 250, 64-character codes, 32-character size labels, 50 items in every collection, nullable scalars and empty arrays. Negative requests cover missing/null/unknown fields, wrong JSON types, malformed JSON, blank/control/length values, height precision/range, canonical duplicates and malformed/duplicate UUIDs. The style negative section sets a valid baseline and GETs once after its invalid-request group to compare the full aggregate.

Expired JWT and ADMIN self-only cases are NOT EXECUTED by this collection because no real local fixture is supplied and a JWT must not be fabricated. Existing Java integration coverage is in ManageProfilePostgresIntegrationTests: allFourRoutesRequireSignedJwtAndCustomerOrAdminAuthority covers missing/malformed/expired JWTs and disallowed roles; realHttpJwtSubjectDrivesCustomerAndAdminSelfOnlyProfileAccess covers JWT-subject ownership and ADMIN self-only behavior. In that class the expired/ADMIN checks use signed test JWTs in MockMvc; real embedded HTTP plus PostgreSQL is also covered for CUSTOMER requests.

Brand UUID checks are syntax-only. A valid UUID does not prove that a Commerce brand exists or is active.

## Verification status

Structural validation passed: both JSON files parse; the collection declares the Postman Collection v2.1 schema; request-variable references resolve, including request-local profileBoundaryBody and Authorization values; shared collection/environment scopes have no token override; and every embedded pre-request/test JavaScript script passes Node syntax checking. Shared files contain no password or access-token values.

Runtime execution is NOT EXECUTED. Newman is not installed in this workspace, and no A/B test-account credentials were supplied. The collection was not sent to an HTTP service or persistent database. Structural validation is not an E2E pass. See the [US-02.05 implementation report](../docs/qa/identity-profile/US-02.05_IMPLEMENTATION_REPORT.md) for Java HTTP/PostgreSQL tests and Identity regression evidence.
