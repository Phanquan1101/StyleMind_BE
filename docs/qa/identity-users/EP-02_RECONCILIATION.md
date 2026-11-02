# EP-02 Identity — Local Evidence and Gate Reconciliation

**As of:** 2026-10-10
**Workbook/backlog:** read-only; unchanged.
**Decision state:** No Epic status was changed. Local business evidence is ready for owner acceptance; default environment, handover hygiene, tracking and production gates remain separately open.

## Story-level local evidence

| Story | Local business status | Current evidence | Remaining scope limits |
|---|---|---|---|
| US-02.01 Register | Implemented; tested local flows pass in the reported scope. | [Identity audit](../identity-audit/20261008T1144-0700/IDENTITY_RELEASE_READINESS_AUDIT.md), [audit follow-up](../identity-audit/20261008T1144-0700/IDENTITY_AUDIT_FOLLOW_UP.md), Auth collection and Identity verify evidence. | Default `.env` email delivery is not the disposable Mailpit configuration; real external provider delivery is untested. |
| US-02.02 Login | Implemented; tested credential/state/token cases pass in the reported scope. | Identity automated regression, Auth collection and the audit reports above. | Historical F-05 stability risk remains unresolved. |
| US-02.03 Refresh/Logout | Implemented; tested session/token cases pass in the reported scope. | Identity automated regression and Auth collection. | Approved access-JWT expiry-only behavior; production key/topology not assessed. |
| US-02.04 Forgot Password | Implemented; tested reset and Mailpit recovery flows pass in the reported scope. | Identity regression and disposable Mailpit audit flow. | Real external SMTP/provider delivery untested. |
| US-02.05 Manage Profile | **DONE in local scope.** | [Implementation report](../identity-profile/US-02.05_IMPLEMENTATION_REPORT.md); fresh disposable regression in this follow-up also passed 107 requests / 107 assertions. | The fresh-account-only folder 07 was excluded from the repeated 01–06 regression; it is not part of this rerun. |
| US-02.06 Manage Addresses | **DONE in local scope.** | [Implementation report](../identity-address/US-02.06_IMPLEMENTATION_REPORT.md), [follow-up](../identity-address/US-02.06_FOLLOW_UP_REPORT.md); disposable regression passed 39 requests / 75 assertions. | Local evidence only. |
| US-02.07 Manage Users | **DONE in approved local scope after live-authorization fix.** | [Approved contract](../../planning/identity/US-02.07_MANAGE_USERS_CONTRACT.md), [implementation report](US-02.07_IMPLEMENTATION_REPORT.md), [race follow-up](US-02.07_LIVE_AUTHORIZATION_FOLLOW_UP.md), [Postman collection](../../../postman/StyleMind-Identity-Manage-Users.postman_collection.json). | Owner acceptance remains separate from local test completion. No persistent Admin was provisioned. |

For US-02.07, the stale-authorization race was reproduced before the fix (old Admin GET returned 200 after a committed role removal; the suspend race returned 500), then fixed. The deterministic post-fix suite passed 17/17. Three consecutive full Identity verifies each passed 138/138 with no skips; Notification passed 23/23. Disposable Newman runs passed Manage Users 46/138, Profile 107/107 and Address 39/75. Evidence and exact HTTP outcomes are in the [follow-up](US-02.07_LIVE_AUTHORIZATION_FOLLOW_UP.md). These are local results, not production certification.

## A. Local business-functional acceptance

- **Source:** owner-approved story contracts, their backlog rows and the implementation/test evidence linked above. US-02.07's live-authorization authorization point remains as approved; it was not redefined to excuse the reproduced bug.
- **Evidence:** current code and automated suites cover stories 01–07. The new GET-vs-role-removal/suspension tests use real HTTP requests, PostgreSQL lock-wait evidence and signed JWTs. Post-fix GET returns 403 if a disabling Admin write wins; a GET that authorizes first may finish while the writer waits; the next stale-JWT GET/HEAD is denied. Count and page use one SQL statement snapshot. Manage Users, Profile and Address Postman runtime regressions passed on the same isolated stack.
- **Does it block local business acceptance?** No known acceptance failure remains in the approved local scope after the fix. This is a technical recommendation, not owner acceptance.
- **Next / owner:** Review the linked evidence and accept or reject local business completion. No story/backlog status was changed here.

**Recommendation:** the owner may accept EP-02's local business-functional scope for closure after review. Keep the following environment, hygiene, tracking, stability and production gates distinct; this recommendation does not accept those risks or mark the Epic complete.

## B. Default local environment readiness

- **Source:** [Identity audit follow-up, sections 6 and 9](../identity-audit/20261008T1144-0700/IDENTITY_AUDIT_FOLLOW_UP.md) records F-01 as **RESOLVED for local configuration** after the owner's approved HMAC-key rotation. That report separately says the effective default SMTP target was not Mailpit and a safe default email smoke was not run.
- **Evidence:** this final review used generated disposable credentials and a separate PostgreSQL 18.6 + Mailpit stack. The real `.env` was not read or used; default configuration was **not rechecked in this turn**. No external email was sent, and the persistent `stylemind-task4` database was untouched.
- **Does it block local business acceptance?** No; it does not invalidate the isolated functional results.
- **Does it block default environment readiness?** **The default local email smoke remains a separate, unverified gate.** The earlier SMTP observation is not a current recheck. F-01 is not reopened.
- **Next / owner:** choose a safe Mailpit-backed default local email smoke and run Register/verify/recovery against it. No HMAC rotation or challenge invalidation is requested by this reconciliation.

## C. Handover and security hygiene

- **Source:** F-04 in the [Identity audit](../identity-audit/20261008T1144-0700/IDENTITY_RELEASE_READINESS_AUDIT.md) and [follow-up section 7](../identity-audit/20261008T1144-0700/IDENTITY_AUDIT_FOLLOW_UP.md).
- **Resource/state:** `C:\Users\FPTSHOP\AppData\Local\Temp\stylemind-identity-audit-20261008` remains on disk. It contains an old audit-only Compose override, direct-Notification port override and verification logs; the override has one-off HMAC/service-token values. Its disposable runtime was already removed. This follow-up did not open or delete that directory and did not reuse those values.
- **Does it block local business acceptance?** No functional failure is implied.
- **Does it block handover hygiene?** **Yes.** Audit-only sensitive values remain stored locally.
- **Next / owner:** the owner should review/retain any needed non-sensitive evidence, delete only that exact old directory through an allowed, verified cleanup action, and confirm its absence. This report does not mark F-04 resolved.

## D. Test stability

- **Source:** F-05 in the [Identity audit](../identity-audit/20261008T1144-0700/IDENTITY_RELEASE_READINESS_AUDIT.md) and [follow-up section 4](../identity-audit/20261008T1144-0700/IDENTITY_AUDIT_FOLLOW_UP.md).
- **Evidence:** the original Identity run had five failures and one error; its exact root cause remains unknown. Three earlier 89-test runs were clean. This follow-up adds three clean full Identity runs of 138 tests each, with 0 failures/errors/skips. The live-authorization issue in this follow-up was separately reproduced and its specific cause fixed; it is not asserted to be the cause of the historical F-05 event.
- **Does it block local business acceptance?** No current story failure is indicated by the new evidence.
- **Does it resolve F-05?** **No.** Repeated clean results mean the historical failure was not reproduced; they do not establish its root cause or prove absence of future flakiness.
- **Next / owner:** retain F-05 as an unresolved stability risk; inspect fresh, redacted diagnostics if it recurs. No indefinite reruns or changes to expected behavior were used.

## E. Backlog tracking

- **Source:** `C:\Users\FPTSHOP\Desktop\StyleMind_Task.xlsx`, sheet `Backlog`; US-02.07 is rows 25–27. Prior audit F-03 records the US-02.01–04 tracking mismatch.
- **Evidence:** rows remain `To Do`; workbook SHA-256 before and after is `A4027B00A89A5E41135FCA94140AFDD81173CA7E79D63BBACCE0072D16A92A42`. V1, V2 and V3 hashes also match their pre-run values; see the [hash record](../../../target/us0207-liveauth-20261010/hashes-after.txt).
- **Does it block local business acceptance?** No. A tracker status alone does not prove the implementation is absent or the tests failed.
- **Does it block project tracking closure?** **Yes.** Backlog state does not yet reflect delivered work.
- **Next / owner:** reconcile workbook statuses after reviewing acceptance evidence. Workbook editing was not authorized and was not performed.

## F. Production readiness

- **Source:** production limits and risks in the [Identity release audit](../identity-audit/20261008T1144-0700/IDENTITY_RELEASE_READINESS_AUDIT.md), including F-02, and owner-provided rule that production topology remains TBD.
- **Evidence:** only local code, isolated PostgreSQL, signed test JWTs and Mailpit were exercised. Real external provider delivery, production key provisioning/rotation/distribution, final service/deployment topology, and edge-level IP/global abuse controls were not tested or approved.
- **Does it block local business acceptance?** No.
- **Does it block production readiness?** **Yes.** No production-readiness or public-deployment claim follows from these local tests.
- **Next / owner:** decide production SMTP/provider, key operations and topology; decide the edge-abuse controls before public exposure; then test against the chosen design. No new service or control was added here.

## Current recommendation

US-02.07's local functional implementation is complete and regression-tested after the race fix. **Recommend owner acceptance of EP-02 local business completion.** Keep EP-02 overall status and workbook unchanged until the owner reviews the business evidence and reconciles tracking; keep default-environment readiness, F-04 cleanup, F-05 stability uncertainty and production readiness recorded as separate gates. No workbook update, owner acceptance, migration, persistent database operation, commit or push was performed.
