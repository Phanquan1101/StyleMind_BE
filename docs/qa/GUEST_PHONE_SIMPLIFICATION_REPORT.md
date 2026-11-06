# Guest phone input simplification

> WITHDRAWN: the owner temporarily removed guest checkout on 2026-11-02. Guest cart remains; checkout requires login. Historical implementation/test evidence below is not an active API contract. See [removal report](../qa/GUEST_CHECKOUT_REMOVAL_REPORT.md).


Date: 2026-11-02 (Asia/Saigon).

Owner follow-up: strict guest phone validation is not needed. Removed libphonenumber from Commerce and all guest country/prefix/numbering-plan validation and automatic +84/E.164 normalization. Guest recipient_phone remains required text with Unicode trim, 1-64 code points and no remaining controls; supplied formatting is stored. No phone ownership verification is claimed.

## Exact files touched

- commerce-service/pom.xml: removed the phone dependency added during guest checkout; restores the original dependency set.
- commerce-service/src/main/java/com/stylemind/commerce/validation/order/GuestCheckoutValidator.java
- commerce-service/src/test/java/com/stylemind/commerce/GuestCheckoutValidationTests.java
- commerce-service/src/test/java/com/stylemind/commerce/BranchCartOrderPostgresIntegrationTests.java
- docs/planning/GUEST_CHECKOUT_PROPOSAL.md
- docs/governance/CURRENT_BUSINESS_RULES.md
- docs/qa/GUEST_CHECKOUT_IMPLEMENTATION_REPORT.md
- postman/GUEST_CHECKOUT_RUN_GUIDE.md
- docs/qa/GUEST_PHONE_SIMPLIFICATION_REPORT.md (this report).

Existing tests now cover preserved local-number/formatting, international/short/extension text, max length 64/65, missing/null/blank/control rejection, and actual original-value persistence during COD/SePay guest checkout. No new API, schema, migration, ownership, payment or Identity behavior is introduced. Every pre-existing staged/unstaged/untracked change was preserved.

## Verification

Command: mvn -o -B -ntp -f commerce-service/pom.xml -Ddebug=false -DargLine=-Duser.timezone=UTC '-Dstylemind.test.postgres-bin=C:\Program Files\PostgreSQL\18\bin' verify

Result: **BUILD SUCCESS: 82 tests, 0 failures, 0 errors, 0 skips**. Evidence: commerce-service/target/guest-phone-simplification.log (ignored).

Postman JSON is unchanged because request/response structure is unchanged. Updated the run guide; node scripts/validate-guest-postman.cjs passed separately with 14 requests and 15 scripts parsed (no HTTP execution). Checked the packaged Commerce JAR: no libphonenumber dependency. git diff --check passed. No manual Postman or additional packaged-three-service smoke is claimed for this narrow follow-up. The earlier guest-checkout smoke remains historical evidence.

Only disposable native PostgreSQL clusters under ignored commerce-service/target/isolated-postgres are used. No owner/persistent database, Identity files, migrations, ignored secrets, Compose environment, commit or push was touched. The configured read-only backlog workbook remains unavailable. No new business decision is pending; broader production readiness is not established by local tests.
